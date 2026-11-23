package pkwmmap.platform.ble

import kotlinx.coroutines.flow.StateFlow
import pkwmmap.domain.model.BeaconNode
import pkwmmap.domain.model.DetectedBeacon

enum class BleScanStatus {
    IDLE,
    SCANNING,
    NO_HARDWARE,
    BLUETOOTH_OFF,
    MISSING_PERMISSIONS,
    ERROR
}

/**
 * Unified BLE scanner interface for simulated ([MockEddystoneScanner]) and platform
 * (Android / iOS) BLE beacon scanners in `commonMain`.
 */
interface BleScanner {
    val status: StateFlow<BleScanStatus>
    val statusDetail: StateFlow<String>

    fun hasRequiredPermissions(): Boolean

    fun startScanning()

    fun stopScanning()

    fun snapshot(
        placedNodes: List<BeaconNode>,
        includeAllBle: Boolean = false,
        maxAgeMillis: Long = DEFAULT_MAX_AGE_MILLIS
    ): List<DetectedBeacon>

    companion object {
        const val DEFAULT_MAX_AGE_MILLIS: Long = 15_000L
    }
}
