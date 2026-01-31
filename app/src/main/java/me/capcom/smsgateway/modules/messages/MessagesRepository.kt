package me.capcom.smsgateway.modules.messages

import androidx.lifecycle.LiveData
import androidx.lifecycle.distinctUntilChanged
import com.google.gson.GsonBuilder
import me.capcom.smsgateway.data.dao.MessagesDao
import me.capcom.smsgateway.data.entities.MessageType
import me.capcom.smsgateway.data.entities.MessageWithRecipients
import me.capcom.smsgateway.data.entities.MessagesTotals
import me.capcom.smsgateway.domain.MessageContent
import me.capcom.smsgateway.domain.ProcessingState
import me.capcom.smsgateway.modules.messages.data.SendParams
import me.capcom.smsgateway.modules.messages.data.StoredSendRequest
import java.util.Date

class MessagesRepository(private val dao: MessagesDao) {
    private val gson = GsonBuilder().serializeNulls().create()

    fun selectLast(limit: Int) = dao.selectLast(limit).distinctUntilChanged()

    val messagesTotals: LiveData<MessagesTotals> = dao.getMessagesStats().distinctUntilChanged()

    fun get(id: String): StoredSendRequest {
        return dao.get(id)?.toRequest()
            ?: throw IllegalArgumentException("Message with id $id not found")
    }

    private fun MessageWithRecipients.toRequest(): StoredSendRequest {
        val message = this

        return StoredSendRequest(
            id = message.rowId,
            state = message.state,
            recipients = this.recipients,
            message.message.source,
            me.capcom.smsgateway.modules.messages.data.Message(
                id = message.message.id,
                content = when (message.message.type) {
                    MessageType.Text -> gson.fromJson(
                        message.message.content,
                        MessageContent.Text::class.java
                    )

                    MessageType.Data -> gson.fromJson(
                        message.message.content,
                        MessageContent.Data::class.java
                    )
                },
                phoneNumbers = message.recipients.filter { it.state == ProcessingState.Pending }
                    .map { it.phoneNumber },
                isEncrypted = message.message.isEncrypted,
                createdAt = Date(message.message.createdAt),
            ),
            SendParams(
                withDeliveryReport = message.message.withDeliveryReport,
                skipPhoneValidation = message.message.skipPhoneValidation,
                simNumber = message.message.simNumber,
                validUntil = message.message.validUntil,
                priority = message.message.priority
            ),
        )
    }
}
