package pkwmmap.platform.ble

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.CoreBluetooth.CBCentralManager
import platform.CoreBluetooth.CBCentralManagerDelegateProtocol
import platform.CoreBluetooth.CBManager
import platform.CoreBluetooth.CBManagerAuthorizationAllowedAlways
import platform.CoreBluetooth.CBManagerAuthorizationDenied
import platform.CoreBluetooth.CBManagerAuthorizationNotDetermined
import platform.CoreBluetooth.CBManagerAuthorizationRestricted
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationOpenSettingsURLString
import platform.darwin.NSObject

/**
 * iOS CoreBluetooth permission controller implementing [BlePermissionController].
 */
class IosBlePermissionController : BlePermissionController {
    private val _status = MutableStateFlow(readAuthorizationStatus())
    override val status: StateFlow<BlePermissionStatus> = _status.asStateFlow()

    private val _missingPermissions = MutableStateFlow(computeMissingPermissions(_status.value))
    override val missingPermissions: StateFlow<List<String>> = _missingPermissions.asStateFlow()

    private val _shouldShowRationale = MutableStateFlow(_status.value == BlePermissionStatus.DENIED)
    override val shouldShowRationale: StateFlow<Boolean> = _shouldShowRationale.asStateFlow()

    private var permissionProbeManager: CBCentralManager? = null
    private var pendingCallback: ((Boolean) -> Unit)? = null

    private val delegate =
        object : NSObject(), CBCentralManagerDelegateProtocol {
            override fun centralManagerDidUpdateState(central: CBCentralManager) {
                refreshPermissions()
                val callback = pendingCallback
                if (callback != null && CBManager.authorization != CBManagerAuthorizationNotDetermined) {
                    pendingCallback = null
                    callback(hasAllPermissions())
                }
            }
        }

    override fun refreshPermissions() {
        val current = readAuthorizationStatus()
        _status.value = current
        _missingPermissions.value = computeMissingPermissions(current)
        _shouldShowRationale.value = current == BlePermissionStatus.DENIED
    }

    override fun requestPermissions(onResult: (Boolean) -> Unit) {
        refreshPermissions()
        when (_status.value) {
            BlePermissionStatus.GRANTED -> onResult(true)
            BlePermissionStatus.NOT_REQUESTED -> {
                pendingCallback = onResult
                if (permissionProbeManager == null) {
                    // Creating CBCentralManager triggers the iOS Bluetooth authorization prompt.
                    permissionProbeManager = CBCentralManager(delegate = delegate, queue = null)
                }
            }
            BlePermissionStatus.DENIED,
            BlePermissionStatus.PERMANENTLY_DENIED -> onResult(false)
        }
    }

    override fun openAppSettings() {
        val url = NSURL.URLWithString(UIApplicationOpenSettingsURLString) ?: return
        UIApplication.sharedApplication.openURL(
            url = url,
            options = emptyMap<Any?, Any?>(),
            completionHandler = null
        )
    }

    private fun readAuthorizationStatus(): BlePermissionStatus =
        when (CBManager.authorization) {
            CBManagerAuthorizationAllowedAlways -> BlePermissionStatus.GRANTED
            CBManagerAuthorizationNotDetermined -> BlePermissionStatus.NOT_REQUESTED
            CBManagerAuthorizationRestricted -> BlePermissionStatus.DENIED
            CBManagerAuthorizationDenied -> BlePermissionStatus.PERMANENTLY_DENIED
            else -> BlePermissionStatus.DENIED
        }

    private fun computeMissingPermissions(status: BlePermissionStatus): List<String> =
        if (status == BlePermissionStatus.GRANTED) emptyList() else listOf("NSBluetoothAlwaysUsageDescription")
}
