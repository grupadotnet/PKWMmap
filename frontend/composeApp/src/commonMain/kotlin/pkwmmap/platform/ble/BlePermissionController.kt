package pkwmmap.platform.ble

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class BlePermissionStatus {
    NOT_REQUESTED,
    GRANTED,
    DENIED,
    PERMANENTLY_DENIED
}

/**
 * Platform-agnostic contract for checking and requesting BLE & location runtime permissions.
 */
interface BlePermissionController {
    val status: StateFlow<BlePermissionStatus>
    val missingPermissions: StateFlow<List<String>>
    val shouldShowRationale: StateFlow<Boolean>

    fun hasAllPermissions(): Boolean = status.value == BlePermissionStatus.GRANTED

    fun refreshPermissions()

    fun requestPermissions(onResult: (Boolean) -> Unit = {})

    fun openAppSettings()
}

/**
 * Mock [BlePermissionController] for mock-first development and common unit tests.
 */
class MockBlePermissionController(
    initialGranted: Boolean = true,
    private val grantOnRequest: Boolean = true
) : BlePermissionController {
    private val _status =
        MutableStateFlow(
            if (initialGranted) BlePermissionStatus.GRANTED else BlePermissionStatus.NOT_REQUESTED
        )
    override val status: StateFlow<BlePermissionStatus> = _status.asStateFlow()

    private val _missingPermissions =
        MutableStateFlow(
            if (initialGranted) emptyList() else listOf("BLUETOOTH_SCAN", "ACCESS_FINE_LOCATION")
        )
    override val missingPermissions: StateFlow<List<String>> = _missingPermissions.asStateFlow()

    private val _shouldShowRationale = MutableStateFlow(false)
    override val shouldShowRationale: StateFlow<Boolean> = _shouldShowRationale.asStateFlow()

    var openSettingsInvocations: Int = 0
        private set

    override fun refreshPermissions() = Unit

    override fun requestPermissions(onResult: (Boolean) -> Unit) {
        if (_status.value == BlePermissionStatus.GRANTED) {
            onResult(true)
            return
        }
        if (grantOnRequest) {
            _status.value = BlePermissionStatus.GRANTED
            _missingPermissions.value = emptyList()
            _shouldShowRationale.value = false
            onResult(true)
        } else {
            _status.value = BlePermissionStatus.DENIED
            _shouldShowRationale.value = true
            onResult(false)
        }
    }

    override fun openAppSettings() {
        openSettingsInvocations++
    }
}
