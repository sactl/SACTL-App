package me.capcom.smsgateway.modules.messages

import android.content.Context
import me.capcom.smsgateway.data.dao.MessagesDao
import me.capcom.smsgateway.data.entities.MessageWithRecipients
import me.capcom.smsgateway.domain.EntitySource
import me.capcom.smsgateway.domain.ProcessingState
import me.capcom.smsgateway.modules.encryption.EncryptionService
import me.capcom.smsgateway.modules.events.EventBus
import me.capcom.smsgateway.modules.health.domain.CheckResult
import me.capcom.smsgateway.modules.health.domain.Status
import me.capcom.smsgateway.modules.logs.LogsService
import me.capcom.smsgateway.modules.messages.workers.LogTruncateWorker

class MessagesService(
    private val context: Context,
    private val settings: MessagesSettings,
    private val dao: MessagesDao,
    private val messages: MessagesRepository,
    private val encryptionService: EncryptionService,
    private val events: EventBus,
    private val logsService: LogsService,
) {
    //#region Health
    fun healthCheck(): Map<String, CheckResult> {
        val timestamp = System.currentTimeMillis() - 3600 * 1000L
        val failedStats = dao.countFailedFrom(timestamp)
        val processedStats = dao.countProcessedFrom(timestamp)
        return mapOf(
            "failed" to CheckResult(
                when {
                    failedStats.count > 0 && processedStats.count == 0 -> Status.FAIL
                    failedStats.count > 0 -> Status.WARN
                    else -> Status.PASS
                },
                failedStats.count.toLong(),
                "messages",
                "Failed messages for last hour"
            )
        )
    }
    //#endregion

    //#region Lifecycle
    fun start(context: Context) {
        LogTruncateWorker.start(context)
    }

    fun stop(context: Context) {
        LogTruncateWorker.stop(context)
    }
    //#endregion

    //#region Read
    fun getMessage(id: String): MessageWithRecipients? {
        val message = dao.get(id)
            ?: return null

        val state = message.state

        if (state == message.message.state) {
            return message
        }

        if (state != message.message.state) {
            when (state) {
                ProcessingState.Processed -> dao.setMessageProcessed(message.message.id)
                else -> dao.updateMessageState(message.message.id, state)
            }
        }

        return dao.get(id)
    }

    /**
     * Count messages based on state and date range
     */
    fun countMessages(source: EntitySource, state: ProcessingState?, start: Long, end: Long) =
        dao.count(source, state, start, end)

    /**
     * Get messages with pagination and filtering
     */
    fun selectMessages(
        source: EntitySource,
        state: ProcessingState?,
        start: Long,
        end: Long,
        limit: Int,
        offset: Int
    ) = dao.select(source, state, start, end, limit, offset)
    //#endregion

    suspend fun truncateLog() {
        val lifetime = settings.logLifetimeDays ?: return

        dao.truncateLog(System.currentTimeMillis() - lifetime * 86400000L)
    }
}
