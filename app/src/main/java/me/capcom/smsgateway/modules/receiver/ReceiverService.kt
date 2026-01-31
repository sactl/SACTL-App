package me.capcom.smsgateway.modules.receiver

import android.content.Context
import android.os.Build
import android.provider.Telephony
import android.util.Base64
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import me.capcom.smsgateway.helpers.SubscriptionsHelper
import me.capcom.smsgateway.modules.encryption.CryptoException
import me.capcom.smsgateway.modules.encryption.CryptoManager
import me.capcom.smsgateway.modules.logs.LogsService
import me.capcom.smsgateway.modules.logs.db.LogEntry
import me.capcom.smsgateway.modules.receiver.data.InboxMessage
import me.capcom.smsgateway.modules.webhooks.WebHooksService
import me.capcom.smsgateway.modules.webhooks.domain.WebHookEvent
import me.capcom.smsgateway.modules.webhooks.payload.MmsReceivedPayload
import me.capcom.smsgateway.modules.webhooks.payload.SmsEventPayload
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.Date

class ReceiverService : KoinComponent {
    private val webHooksService: WebHooksService by inject()
    private val logsService: LogsService by inject()
    private val cryptoManager: CryptoManager by inject()

    private val eventsReceiver by lazy { EventsReceiver() }

    // Coroutine scope for async encryption operations (Coding Standard: use Dispatchers.IO)
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun start(context: Context) {
        MessagesReceiver.register(context)
        MmsReceiver.register(context)
        eventsReceiver.start()
    }

    fun stop(context: Context) {
        eventsReceiver.stop()
        MmsReceiver.unregister(context)
        MessagesReceiver.unregister(context)
    }

    fun export(context: Context, period: Pair<Date, Date>) {
        logsService.insert(
            LogEntry.Priority.DEBUG,
            MODULE_NAME,
            "ReceiverService::export - start",
            mapOf("period" to period)
        )

        select(context, period)
            .forEach {
                process(context, it)
            }

        logsService.insert(
            LogEntry.Priority.DEBUG,
            MODULE_NAME,
            "ReceiverService::export - end",
            mapOf("period" to period)
        )
    }

    fun process(context: Context, message: InboxMessage) {
        logsService.insert(
            LogEntry.Priority.DEBUG,
            MODULE_NAME,
            "ReceiverService::process - message received",
            mapOf(
                "from" to message.address,
                "type" to message::class.simpleName
            )
        )

        // FAIL-SAFE: If public key is not configured, DROP the message
        // This ensures we never forward plaintext SMS to webhooks
        if (!cryptoManager.isConfigured()) {
            logsService.insert(
                LogEntry.Priority.WARN,
                MODULE_NAME,
                "Message DROPPED - No public key configured. Configure RSA public key in settings to enable forwarding.",
                mapOf(
                    "from" to message.address,
                    "action" to "DROPPED"
                )
            )
            return
        }

        // Launch encryption on IO dispatcher (Coding Standard: use Dispatchers.IO for encryption)
        serviceScope.launch {
            processAsync(context, message)
        }
    }

    /**
     * Internal async processing with encryption on IO dispatcher.
     */
    private suspend fun processAsync(context: Context, message: InboxMessage) {
        val simNumber = message.subscriptionId?.let {
            SubscriptionsHelper.getSimSlotIndex(
                context,
                it
            )
        }?.let { it + 1 }

        try {
            val (type, payload) = when (message) {
                is InboxMessage.Text -> {
                    // Encrypt the SMS body before forwarding (async on IO dispatcher)
                    val encryptedMessage = try {
                        cryptoManager.encryptAsync(message.text)
                    } catch (e: CryptoException) {
                        logsService.insert(
                            LogEntry.Priority.ERROR,
                            MODULE_NAME,
                            "Message DROPPED - Encryption failed",
                            mapOf(
                                "from" to message.address,
                                "error" to e.message,
                                "action" to "DROPPED"
                            )
                        )
                        return
                    }

                    WebHookEvent.SmsReceived to SmsEventPayload.SmsReceived(
                        messageId = message.hashCode().toUInt().toString(16),
                        message = encryptedMessage,  // Now contains encrypted ciphertext
                        phoneNumber = message.address,
                        simNumber = simNumber,
                        receivedAt = message.date,
                    )
                }

                is InboxMessage.Data -> {
                    // Encrypt the data payload before forwarding (async on IO dispatcher)
                    val dataString = Base64.encodeToString(message.data, Base64.NO_WRAP)
                    val encryptedData = try {
                        cryptoManager.encryptAsync(dataString)
                    } catch (e: CryptoException) {
                        logsService.insert(
                            LogEntry.Priority.ERROR,
                            MODULE_NAME,
                            "Data message DROPPED - Encryption failed",
                            mapOf(
                                "from" to message.address,
                                "error" to e.message,
                                "action" to "DROPPED"
                            )
                        )
                        return
                    }

                    WebHookEvent.SmsDataReceived to SmsEventPayload.SmsDataReceived(
                        messageId = message.hashCode().toUInt().toString(16),
                        data = encryptedData,  // Now contains encrypted ciphertext
                        phoneNumber = message.address,
                        simNumber = simNumber,
                        receivedAt = message.date,
                    )
                }

                is InboxMessage.Mms -> {
                    // MMS: encrypt subject if present (async on IO dispatcher)
                    val encryptedSubject = message.subject?.let { subject ->
                        try {
                            cryptoManager.encryptAsync(subject)
                        } catch (e: CryptoException) {
                            logsService.insert(
                                LogEntry.Priority.ERROR,
                                MODULE_NAME,
                                "MMS message DROPPED - Encryption failed",
                                mapOf(
                                    "from" to message.address,
                                    "error" to e.message,
                                    "action" to "DROPPED"
                                )
                            )
                            return
                        }
                    }

                    WebHookEvent.MmsReceived to MmsReceivedPayload(
                        messageId = message.messageId ?: message.transactionId,
                        phoneNumber = message.address,
                        simNumber = simNumber,
                        transactionId = message.transactionId,
                        subject = encryptedSubject,
                        size = message.size,
                        contentClass = message.contentClass,
                        receivedAt = message.date
                    )
                }
            }

            webHooksService.emit(context, type, payload)

            logsService.insert(
                LogEntry.Priority.DEBUG,
                MODULE_NAME,
                "ReceiverService::process - message encrypted and forwarded",
                mapOf(
                    "type" to type,
                    "from" to message.address,
                    "encrypted" to true
                )
            )
        } catch (e: Exception) {
            logsService.insert(
                LogEntry.Priority.ERROR,
                MODULE_NAME,
                "ReceiverService::process - unexpected error",
                mapOf(
                    "error" to e.message,
                    "from" to message.address
                )
            )
        }
    }

    fun select(context: Context, period: Pair<Date, Date>): List<InboxMessage> {
        logsService.insert(
            LogEntry.Priority.DEBUG,
            MODULE_NAME,
            "ReceiverService::select - start",
            mapOf("period" to period)
        )

        val projection = mutableListOf(
            Telephony.Sms._ID,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.DATE,
            Telephony.Sms.BODY,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            projection += Telephony.Sms.SUBSCRIPTION_ID
        }

        val selection = "${Telephony.Sms.DATE} >= ? AND ${Telephony.Sms.DATE} <= ?"
        val selectionArgs = arrayOf(
            period.first.time.toString(),
            period.second.time.toString()
        )
        val sortOrder = Telephony.Sms.DATE

        val contentResolver = context.contentResolver
        val cursor = contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI,
            projection.toTypedArray(),
            selection,
            selectionArgs,
            sortOrder
        )

        val messages = mutableListOf<InboxMessage>()

        cursor?.use { cursor ->
            while (cursor.moveToNext()) {
                messages.add(
                    InboxMessage.Text(
                        address = cursor.getString(1),
                        date = Date(cursor.getLong(2)),
                        text = cursor.getString(3),
                        subscriptionId = when {
                            projection.size > 4 -> cursor.getInt(4).takeIf { it >= 0 }
                            else -> null
                        }
                    )
                )
            }
        }

        logsService.insert(
            LogEntry.Priority.DEBUG,
            MODULE_NAME,
            "ReceiverService::select - end",
            mapOf("messages" to messages.size)
        )

        return messages
    }
}
