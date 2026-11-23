package pkwmmap.domain.model

/**
 * Represents a decoded Eddystone BLE Service Data (UUID 0xFEAA) or companion beacon frame.
 */
sealed interface EddystoneFrame {
    val frameTypeLabel: String

    data class UidFrame(
        val rangingDataTxPower: Int,
        val namespaceId: String,
        val instanceId: String
    ) : EddystoneFrame {
        override val frameTypeLabel: String = "UID"
        val uidKey: String get() = "$namespaceId:$instanceId"
    }

    data class UrlFrame(
        val rangingDataTxPower: Int,
        val url: String
    ) : EddystoneFrame {
        override val frameTypeLabel: String = "URL"
    }

    data class TlmFrame(
        val version: Int,
        val batteryMv: Int,
        val temperatureCelsius: Float,
        val pduCount: Long,
        val uptimeSeconds: Long
    ) : EddystoneFrame {
        override val frameTypeLabel: String = "TLM"
    }

    data class IBeaconFrame(
        val uuid: String,
        val major: Int,
        val minor: Int,
        val txPowerAt1m: Int
    ) : EddystoneFrame {
        override val frameTypeLabel: String = "iBeacon"
    }

    data class InodeFrame(
        val deviceName: String,
        val manufacturerHex: String
    ) : EddystoneFrame {
        override val frameTypeLabel: String = "iNode"
    }

    data class UnknownFrame(val rawFrameType: Int) : EddystoneFrame {
        override val frameTypeLabel: String = "0x${rawFrameType.toString(16).uppercase()}"
    }
}

/**
 * Represents a nearby BLE beacon observed by the scanner.
 */
data class DetectedBeacon(
    val beaconId: String,
    val macAddress: String,
    val label: String,
    val rssi: Int,
    val txPowerAt0m: Int,
    val estimatedDistanceMeters: Double,
    val frame: EddystoneFrame,
    val telemetry: EddystoneFrame.TlmFrame? = null,
    val lastSeenMillis: Long = 0L
) {
    val signalQualityPercent: Int
        get() = ((rssi + 100).coerceIn(0, 60) * 100) / 60
}
