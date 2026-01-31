package me.capcom.smsgateway.modules.heartbeat

import me.capcom.smsgateway.modules.settings.Exporter
import me.capcom.smsgateway.modules.settings.Importer
import me.capcom.smsgateway.modules.settings.KeyValueStorage
import me.capcom.smsgateway.modules.settings.get

class HeartbeatSettings(
    private val storage: KeyValueStorage,
) : Exporter, Importer {

    /**
     * Whether heartbeat is enabled.
     * Heartbeat is enabled when interval is set and > 0.
     */
    val enabled: Boolean
        get() = intervalMinutes != null && intervalMinutes!! > 0

    /**
     * Heartbeat interval in minutes. Default is 15 minutes per PROJECT_SPEC.
     */
    var intervalMinutes: Int?
        get() = storage.get<Int>(INTERVAL_MINUTES)?.takeIf { it > 0 } ?: DEFAULT_INTERVAL_MINUTES
        set(value) = storage.set(INTERVAL_MINUTES, value)

    /**
     * Timestamp of the last successful heartbeat.
     */
    var lastHeartbeatTime: Long?
        get() = storage.get<Long>(LAST_HEARTBEAT_TIME)
        set(value) = storage.set(LAST_HEARTBEAT_TIME, value)

    /**
     * Status of the last heartbeat attempt.
     */
    var lastHeartbeatStatus: HeartbeatStatus
        get() = storage.get<String>(LAST_HEARTBEAT_STATUS)?.let {
            try {
                HeartbeatStatus.valueOf(it)
            } catch (e: Exception) {
                HeartbeatStatus.Unknown
            }
        } ?: HeartbeatStatus.Unknown
        set(value) = storage.set(LAST_HEARTBEAT_STATUS, value.name)

    companion object {
        const val DEFAULT_INTERVAL_MINUTES = 15

        private const val INTERVAL_MINUTES = "interval_minutes"
        private const val LAST_HEARTBEAT_TIME = "last_heartbeat_time"
        private const val LAST_HEARTBEAT_STATUS = "last_heartbeat_status"
    }

    override fun export(): Map<String, *> {
        return mapOf(
            INTERVAL_MINUTES to intervalMinutes,
        )
    }

    override fun import(data: Map<String, *>): Boolean {
        return data.map { (key, value) ->
            when (key) {
                INTERVAL_MINUTES -> {
                    val newValue = value?.toString()?.toFloat()?.toInt()?.takeIf { it > 0 }
                    val changed = this.intervalMinutes != newValue
                    storage.set(key, newValue?.toString())
                    changed
                }
                else -> false
            }
        }.any { it }
    }
}

enum class HeartbeatStatus {
    Unknown,
    Success,
    Failed,
    Pending
}
