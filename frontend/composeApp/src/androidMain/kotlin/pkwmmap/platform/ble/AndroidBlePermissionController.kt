package pkwmmap.platform.ble

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Android runtime permission controller for BLE scanning (`BLUETOOTH_SCAN`, `BLUETOOTH_CONNECT`,
 * `ACCESS_COARSE_LOCATION`, `ACCESS_FINE_LOCATION`, and legacy pre-Android 12 permissions).
 */
class AndroidBlePermissionController(
    private val context: Context,
    private val scanner: RealBleEddystoneScanner? = null
) : BlePermissionController {
    private val appContext: Context = context.applicationContext
    private val activity: Activity? = context.findActivity()

    private val _missingPermissions = MutableStateFlow(computeMissingPermissions())
    override val missingPermissions: StateFlow<List<String>> = _missingPermissions.asStateFlow()

    private val _shouldShowRationale = MutableStateFlow(false)
    override val shouldShowRationale: StateFlow<Boolean> = _shouldShowRationale.asStateFlow()

    private val _status =
        MutableStateFlow(
            if (_missingPermissions.value.isEmpty()) {
                BlePermissionStatus.GRANTED
            } else {
                BlePermissionStatus.NOT_REQUESTED
            }
        )
    override val status: StateFlow<BlePermissionStatus> = _status.asStateFlow()

    private var hasRequestedPermissions = false
    private var requestLauncher: ((Array<String>) -> Unit)? = null
    private var pendingCallback: ((Boolean) -> Unit)? = null

    fun bindLauncher(launcher: (Array<String>) -> Unit) {
        requestLauncher = launcher
    }

    fun unbindLauncher() {
        requestLauncher = null
    }

    override fun refreshPermissions() {
        val missing = computeMissingPermissions()
        _missingPermissions.value = missing

        val rationale = computeShouldShowRationale(missing)
        _shouldShowRationale.value = rationale

        _status.value =
            when {
                missing.isEmpty() -> BlePermissionStatus.GRANTED
                !hasRequestedPermissions -> BlePermissionStatus.NOT_REQUESTED
                rationale -> BlePermissionStatus.DENIED
                else -> BlePermissionStatus.PERMANENTLY_DENIED
            }
        scanner?.refreshPermissionStatus()
    }

    override fun requestPermissions(onResult: (Boolean) -> Unit) {
        refreshPermissions()
        val missing = _missingPermissions.value
        if (missing.isEmpty()) {
            onResult(true)
            return
        }

        val launcher = requestLauncher
        if (launcher == null) {
            _status.value = BlePermissionStatus.DENIED
            onResult(false)
            return
        }

        hasRequestedPermissions = true
        pendingCallback = onResult
        launcher(missing.toTypedArray())
    }

    fun onPermissionResults(
        @Suppress("UNUSED_PARAMETER") results: Map<String, Boolean>
    ) {
        hasRequestedPermissions = true
        refreshPermissions()
        val granted = _missingPermissions.value.isEmpty()
        val callback = pendingCallback
        pendingCallback = null
        callback?.invoke(granted)
    }

    override fun openAppSettings() {
        val intent =
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", appContext.packageName, null)
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        appContext.startActivity(intent)
    }

    private fun computeMissingPermissions(): List<String> =
        RealBleEddystoneScanner.requiredRuntimePermissions().filter { permission ->
            ContextCompat.checkSelfPermission(appContext, permission) != PackageManager.PERMISSION_GRANTED
        }

    private fun computeShouldShowRationale(missing: List<String>): Boolean {
        val hostActivity = activity ?: return false
        return missing.any { permission ->
            ActivityCompat.shouldShowRequestPermissionRationale(hostActivity, permission)
        }
    }

    companion object {
        /**
         * Creates and binds an [AndroidBlePermissionController] to [activity] via
         * `registerForActivityResult`. Must be called before the activity reaches `STARTED`
         * (e.g. inside `onCreate` or as a property initializer).
         */
        fun createWithActivity(
            activity: ComponentActivity,
            scanner: RealBleEddystoneScanner? = null
        ): AndroidBlePermissionController {
            val controller = AndroidBlePermissionController(context = activity, scanner = scanner)
            val launcher: ActivityResultLauncher<Array<String>> =
                activity.registerForActivityResult(
                    ActivityResultContracts.RequestMultiplePermissions()
                ) { results ->
                    controller.onPermissionResults(results)
                }
            controller.bindLauncher { permissions -> launcher.launch(permissions) }
            return controller
        }
    }
}

/**
 * Remembers an [AndroidBlePermissionController] wired into Compose's activity-result launcher
 * and lifecycle resume events so returning from system settings updates permission state.
 */
@Composable
fun rememberAndroidBlePermissionController(
    scanner: RealBleEddystoneScanner? = null,
    onPermissionResult: (Boolean) -> Unit = {}
): AndroidBlePermissionController {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val controller = remember(context, scanner) { AndroidBlePermissionController(context, scanner) }

    val launcher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestMultiplePermissions()
        ) { results ->
            controller.onPermissionResults(results)
            onPermissionResult(controller.hasAllPermissions())
        }

    DisposableEffect(controller, launcher) {
        controller.bindLauncher { permissions -> launcher.launch(permissions) }
        onDispose {
            controller.unbindLauncher()
        }
    }

    DisposableEffect(controller, lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    controller.refreshPermissions()
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    return controller
}

private tailrec fun Context.findActivity(): Activity? =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
