package me.capcom.smsgateway.modules.localserver.routes

import android.content.Context
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import me.capcom.smsgateway.data.entities.MessageWithRecipients
import me.capcom.smsgateway.domain.EntitySource
import me.capcom.smsgateway.domain.ProcessingState
import me.capcom.smsgateway.helpers.DateTimeParser
import me.capcom.smsgateway.modules.localserver.LocalServerSettings
import me.capcom.smsgateway.modules.localserver.domain.GetMessageResponse
import me.capcom.smsgateway.modules.localserver.domain.PostMessageResponse
import me.capcom.smsgateway.modules.localserver.domain.PostMessagesInboxExportRequest
import me.capcom.smsgateway.modules.messages.MessagesService
import me.capcom.smsgateway.modules.receiver.ReceiverService
import java.util.Date

class MessagesRoutes(
    private val context: Context,
    private val messagesService: MessagesService,
    private val receiverService: ReceiverService,
    private val settings: LocalServerSettings,
) {
    fun register(routing: Route) {
        routing.apply {
            messagesRoutes()
            route("/inbox") {
                inboxRoutes(context)
            }
        }
    }

    private fun Route.messagesRoutes() {
        get {
            // Parse and validate parameters
            val state = call.request.queryParameters["state"]?.takeIf { it.isNotEmpty() }
                ?.let { ProcessingState.valueOf(it) }
            val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 50
            val offset = call.request.queryParameters["offset"]?.toIntOrNull() ?: 0

            // Parse date range parameters
            val from = call.request.queryParameters["from"]?.let {
                DateTimeParser.parseIsoDateTime(it)?.time
            } ?: 0
            val to = call.request.queryParameters["to"]?.let {
                DateTimeParser.parseIsoDateTime(it)?.time
            } ?: Date().time

            val deviceId = call.request.queryParameters["deviceId"]
            if (deviceId != null && deviceId != settings.deviceId) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("message" to "Invalid device ID")
                )
                return@get
            }

            // Ensure start date is before end date
            if (from > to) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("message" to "Start date cannot be after end date")
                )
                return@get
            }

            // Get total count for pagination
            val total = try {
                messagesService.countMessages(EntitySource.Local, state, from, to)
            } catch (e: Throwable) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf("message" to "Failed to count messages: ${e.message}")
                )
                return@get
            }

            // Get messages with pagination
            val messages = try {
                messagesService.selectMessages(EntitySource.Local, state, from, to, limit, offset)
            } catch (e: Throwable) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf("message" to "Failed to retrieve messages: ${e.message}")
                )
                return@get
            }

            call.response.headers.append("X-Total-Count", total.toString())

            call.respond(
                messages.map { it.toDomain(requireNotNull(settings.deviceId)) } as GetMessageResponse
            )
        }

        // POST endpoint for sending SMS has been removed - this is a receive-only node

        get("{id}") {
            val id = call.parameters["id"]
                ?: return@get call.respond(HttpStatusCode.BadRequest)

            val message = try {
                messagesService.getMessage(id)
                    ?: return@get call.respond(HttpStatusCode.NotFound)
            } catch (e: Throwable) {
                return@get call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf("message" to e.message)
                )
            }

            call.respond(
                message.toDomain(requireNotNull(settings.deviceId)) as PostMessageResponse
            )
        }
    }

    private fun Route.inboxRoutes(context: Context) {
        post("export") {
            val request = call.receive<PostMessagesInboxExportRequest>().validate()
            try {
                receiverService.export(context, request.period)
                call.respond(HttpStatusCode.Accepted)
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf("message" to "Failed to export inbox: ${e.message}")
                )
            }
        }
    }

    private fun MessageWithRecipients.toDomain(deviceId: String) =
        me.capcom.smsgateway.modules.localserver.domain.Message(
            id = message.id,
            deviceId = deviceId,
            state = message.state,
            isHashed = false,
            isEncrypted = message.isEncrypted,
            recipients = recipients.map {
                me.capcom.smsgateway.modules.localserver.domain.Message.Recipient(
                    it.phoneNumber,
                    it.state,
                    it.error
                )
            },
            states = states.associate {
                it.state to Date(it.updatedAt)
            }
        )
}
