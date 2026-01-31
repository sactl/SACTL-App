package me.capcom.smsgateway.modules.heartbeat

import com.google.gson.annotations.SerializedName
import java.util.Date

/**
 * Payload for heartbeat webhook events.
 *
 * Contains device status information:
 * - Battery level and charging status
 * - Network connection type
 * - Timestamp
 */
data class HeartbeatPayload(
    @SerializedName("type")
    val type: String = "heartbeat",

    @SerializedName("battery_level")
    val batteryLevel: Int,

    @SerializedName("is_charging")
    val isCharging: Boolean,

    @SerializedName("network_type")
    val networkType: String,

    @SerializedName("cellular_type")
    val cellularType: String? = null,

    @SerializedName("timestamp")
    val timestamp: Date = Date(),
)
