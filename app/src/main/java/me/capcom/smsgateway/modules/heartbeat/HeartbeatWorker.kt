package me.capcom.smsgateway.modules.heartbeat

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import me.capcom.smsgateway.modules.connection.CellularNetworkType
import me.capcom.smsgateway.modules.connection.ConnectionService
import me.capcom.smsgateway.modules.connection.TransportType
import me.capcom.smsgateway.modules.logs.LogsService
import me.capcom.smsgateway.modules.logs.db.LogEntry
import me.capcom.smsgateway.modules.webhooks.WebHooksService
import me.capcom.smsgateway.modules.webhooks.domain.WebHookEvent
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.concurrent.TimeUnit

/**
 * HeartbeatWorker sends periodic heartbeat webhooks with device status.
 *
 * Payload includes:
 * - Battery level and charging status
 * - Network connection type (WiFi, Cellular, etc.)
 * - Timestamp
 *
 * Runs every 15 minutes by default (configurable).
 */
class HeartbeatWorker(
    private val appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params), KoinComponent {

    private val webHooksService: WebHooksService by inject()
    private val connectionService: ConnectionService by inject()
    private val logsService: LogsService by inject()
    private val settings: HeartbeatSettings by inject()

    override suspend fun doWork(): Result {
        logsService.insert(
            LogEntry.Priority.DEBUG,
            MODULE_NAME,
            "HeartbeatWorker started"
        )

        settings.lastHeartbeatStatus = HeartbeatStatus.Pending

        return try {
            val payload = collectHeartbeatData()

            webHooksService.emit(
                appContext,
                WebHookEvent.SystemHeartbeat,
                payload
            )

            settings.lastHeartbeatTime = System.currentTimeMillis()
            settings.lastHeartbeatStatus = HeartbeatStatus.Success

            logsService.insert(
                LogEntry.Priority.DEBUG,
                MODULE_NAME,
                "Heartbeat sent successfully",
                mapOf(
                    "battery_level" to payload.batteryLevel,
                    "is_charging" to payload.isCharging,
                    "network_type" to payload.networkType
                )
            )

            Result.success()
        } catch (e: Exception) {
            settings.lastHeartbeatStatus = HeartbeatStatus.Failed

            logsService.insert(
                LogEntry.Priority.ERROR,
                MODULE_NAME,
                "Heartbeat failed: ${e.message}",
                mapOf("error" to e.stackTraceToString())
            )

            Result.retry()
        }
    }

    private fun collectHeartbeatData(): HeartbeatPayload {
        // Get battery info
        val batteryStatus: Intent? = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            .let { filter ->
                appContext.registerReceiver(null, filter)
            }

        val batteryLevel = batteryStatus?.let { intent ->
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            if (scale > 0) (level * 100 / scale) else -1
        } ?: -1

        val status = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL

        // Get network info
        val transportTypes = connectionService.transportType
        val networkType = when {
            transportTypes.contains(TransportType.WiFi) -> "WIFI"
            transportTypes.contains(TransportType.Ethernet) -> "ETHERNET"
            transportTypes.contains(TransportType.Cellular) -> "CELLULAR"
            transportTypes.contains(TransportType.Unknown) -> "UNKNOWN"
            else -> "NONE"
        }

        val cellularType = if (transportTypes.contains(TransportType.Cellular)) {
            connectionService.cellularNetworkType.toDisplayString()
        } else {
            null
        }

        return HeartbeatPayload(
            batteryLevel = batteryLevel,
            isCharging = isCharging,
            networkType = networkType,
            cellularType = cellularType
        )
    }

    private fun CellularNetworkType.toDisplayString(): String = when (this) {
        CellularNetworkType.None -> "NONE"
        CellularNetworkType.Unknown -> "UNKNOWN"
        CellularNetworkType.Mobile2G -> "2G"
        CellularNetworkType.Mobile3G -> "3G"
        CellularNetworkType.Mobile4G -> "4G"
        CellularNetworkType.Mobile5G -> "5G"
    }

    companion object {
        private const val WORK_NAME = "HeartbeatWorker"

        fun start(context: Context, intervalMinutes: Int) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val workRequest = PeriodicWorkRequestBuilder<HeartbeatWorker>(
                intervalMinutes.toLong(),
                TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    1,
                    TimeUnit.MINUTES
                )
                .build()

            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(
                    WORK_NAME,
                    ExistingPeriodicWorkPolicy.UPDATE,
                    workRequest
                )
        }

        fun stop(context: Context) {
            WorkManager.getInstance(context)
                .cancelUniqueWork(WORK_NAME)
        }
    }
}

const val MODULE_NAME = "heartbeat"
