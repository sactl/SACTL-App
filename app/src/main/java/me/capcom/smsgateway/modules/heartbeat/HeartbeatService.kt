package me.capcom.smsgateway.modules.heartbeat

import android.content.Context
import me.capcom.smsgateway.modules.logs.LogsService
import me.capcom.smsgateway.modules.logs.db.LogEntry

/**
 * HeartbeatService manages the HeartbeatWorker lifecycle.
 *
 * The heartbeat sends periodic status updates to configured webhooks,
 * allowing the cloud agent to monitor if the physical node is online.
 */
class HeartbeatService(
    private val settings: HeartbeatSettings,
    private val logsService: LogsService,
) {
    fun start(context: Context) {
        if (!settings.enabled) {
            logsService.insert(
                LogEntry.Priority.DEBUG,
                MODULE_NAME,
                "Heartbeat disabled, not starting worker"
            )
            return
        }

        val intervalMinutes = settings.intervalMinutes ?: HeartbeatSettings.DEFAULT_INTERVAL_MINUTES

        logsService.insert(
            LogEntry.Priority.INFO,
            MODULE_NAME,
            "Starting HeartbeatWorker",
            mapOf("interval_minutes" to intervalMinutes)
        )

        HeartbeatWorker.start(context, intervalMinutes)
    }

    fun stop(context: Context) {
        logsService.insert(
            LogEntry.Priority.INFO,
            MODULE_NAME,
            "Stopping HeartbeatWorker"
        )

        HeartbeatWorker.stop(context)
    }

    /**
     * Restart the worker with potentially new settings.
     */
    fun restart(context: Context) {
        stop(context)
        start(context)
    }

    /**
     * Get the last heartbeat timestamp, or null if never sent.
     */
    fun getLastHeartbeatTime(): Long? = settings.lastHeartbeatTime

    /**
     * Get the status of the last heartbeat attempt.
     */
    fun getLastHeartbeatStatus(): HeartbeatStatus = settings.lastHeartbeatStatus

    /**
     * Get a human-readable description of the signal status.
     */
    fun getSignalStatusDescription(): String {
        val lastTime = settings.lastHeartbeatTime
        val status = settings.lastHeartbeatStatus

        return when {
            lastTime == null -> "Never sent"
            status == HeartbeatStatus.Success -> "Active"
            status == HeartbeatStatus.Failed -> "Failed"
            status == HeartbeatStatus.Pending -> "Pending..."
            else -> "Unknown"
        }
    }

    /**
     * Get a human-readable description of when the last heartbeat was sent.
     */
    fun getLastHeartbeatDescription(): String {
        val lastTime = settings.lastHeartbeatTime ?: return "Never"

        val elapsedMs = System.currentTimeMillis() - lastTime
        val elapsedSeconds = elapsedMs / 1000
        val elapsedMinutes = elapsedSeconds / 60
        val elapsedHours = elapsedMinutes / 60

        return when {
            elapsedSeconds < 60 -> "${elapsedSeconds}s ago"
            elapsedMinutes < 60 -> "${elapsedMinutes}m ago"
            elapsedHours < 24 -> "${elapsedHours}h ago"
            else -> "${elapsedHours / 24}d ago"
        }
    }
}
