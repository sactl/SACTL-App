package me.capcom.smsgateway.modules.messages

import me.capcom.smsgateway.modules.settings.Exporter
import me.capcom.smsgateway.modules.settings.Importer
import me.capcom.smsgateway.modules.settings.KeyValueStorage
import me.capcom.smsgateway.modules.settings.get

class MessagesSettings(
    private val storage: KeyValueStorage,
) : Exporter, Importer {

    val logLifetimeDays: Int?
        get() = storage.get<Int?>(LOG_LIFETIME_DAYS)?.takeIf { it > 0 }

    companion object {
        private const val LOG_LIFETIME_DAYS = "log_lifetime_days"
    }

    override fun export(): Map<String, *> {
        return mapOf(
            LOG_LIFETIME_DAYS to logLifetimeDays,
        )
    }

    override fun import(data: Map<String, *>): Boolean {
        return data.map {
            val key = it.key
            val value = it.value

            when (key) {
                LOG_LIFETIME_DAYS -> {
                    val logLifetimeDays = value?.toString()?.toFloat()?.toInt()
                    if (logLifetimeDays != null && logLifetimeDays < 1) {
                        throw IllegalArgumentException("Log lifetime days must be >= 1")
                    }

                    val changed = this.logLifetimeDays != logLifetimeDays
                    storage.set(key, logLifetimeDays?.toString())

                    changed
                }

                else -> false
            }
        }.any { it }
    }
}
