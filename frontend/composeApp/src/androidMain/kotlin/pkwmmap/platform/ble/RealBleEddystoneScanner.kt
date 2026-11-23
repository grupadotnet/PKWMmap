package pkwmmap.platform.ble

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelUuid
import androidx.core.content.ContextCompat
import androidx.core.util.isEmpty
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import pkwmmap.domain.model.BeaconNode
import pkwmmap.domain.model.DetectedBeacon
import pkwmmap.domain.model.EddystoneFrame
import pkwmmap.domain.positioning.RssiDistanceEstimator
import java.util.concurrent.ConcurrentHashMap

/**
 * Real Android BLE scanner for Eddystone (0xFEAA), Apple iBeacon, and iNode BLE beacons.
 */
class RealBleEddystoneScanner(
    private val context: Context
) : BleScanner {
    private val bluetoothManager: BluetoothManager? =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? get() = bluetoothManager?.adapter
    private val bleScanner: BluetoothLeScanner? get() = bluetoothAdapter?.bluetoothLeScanner

    private val _status = MutableStateFlow(BleScanStatus.IDLE)
    override val status: StateFlow<BleScanStatus> = _status.asStateFlow()

    private val _statusDetail = MutableStateFlow("BLE scanner idle")
    override val statusDetail: StateFlow<String> = _statusDetail.asStateFlow()

    private val discoveredBeacons = ConcurrentHashMap<String, DetectedBeacon>()
    private val allDiscoveredBle = ConcurrentHashMap<String, DetectedBeacon>()
    private val telemetryByMac = ConcurrentHashMap<String, EddystoneFrame.TlmFrame>()
    private var isScanning = false

    private val scanCallback =
        object : ScanCallback() {
            override fun onScanResult(
                callbackType: Int,
                result: ScanResult
            ) {
                handleScanResult(result)
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>) {
                results.forEach(::handleScanResult)
            }

            override fun onScanFailed(errorCode: Int) {
                isScanning = false
                _status.value = BleScanStatus.ERROR
                _statusDetail.value = "BLE scan failed (error $errorCode)"
            }
        }

    override fun hasRequiredPermissions(): Boolean = missingPermissions().isEmpty()

    fun missingPermissions(): List<String> =
        requiredPermissions().filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }

    fun refreshPermissionStatus() {
        if (hasRequiredPermissions()) {
            if (_status.value == BleScanStatus.MISSING_PERMISSIONS) {
                _status.value = BleScanStatus.IDLE
                _statusDetail.value = "BLE permissions granted. Ready to scan."
            }
        } else if (!isScanning) {
            _status.value = BleScanStatus.MISSING_PERMISSIONS
            _statusDetail.value = "Grant Bluetooth & Location permissions to scan real beacons."
        }
    }

    @SuppressLint("MissingPermission")
    override fun startScanning() {
        if (isScanning) return

        val adapter = bluetoothAdapter
        if (adapter == null) {
            _status.value = BleScanStatus.NO_HARDWARE
            _statusDetail.value = "No Bluetooth hardware on this device."
            return
        }
        if (!hasRequiredPermissions()) {
            _status.value = BleScanStatus.MISSING_PERMISSIONS
            _statusDetail.value = "Grant Bluetooth & Location permissions to scan real beacons."
            return
        }
        if (!adapter.isEnabled) {
            _status.value = BleScanStatus.BLUETOOTH_OFF
            _statusDetail.value = "Bluetooth is turned off. Enable Bluetooth to scan."
            return
        }
        val scanner = bleScanner
        if (scanner == null) {
            _status.value = BleScanStatus.NO_HARDWARE
            _statusDetail.value = "BLE scanner unavailable."
            return
        }

        val settings =
            ScanSettings
                .Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build()

        // Scan without restrictive hardware filter so both Eddystone (0xFEAA) and iNode
        // advertisements are received reliably across Android chipsets.
        scanner.startScan(null, settings, scanCallback)
        isScanning = true
        _status.value = BleScanStatus.SCANNING
        _statusDetail.value = "Scanning real BLE for Eddystone & iNode beacons..."
    }

    @SuppressLint("MissingPermission")
    override fun stopScanning() {
        if (!isScanning) return
        if (hasRequiredPermissions()) {
            try {
                bleScanner?.stopScan(scanCallback)
            } catch (_: IllegalStateException) {
                // Bluetooth may already be turned off
            }
        }
        isScanning = false
        _status.value = BleScanStatus.IDLE
        _statusDetail.value = "BLE scanner idle"
    }

    override fun snapshot(
        placedNodes: List<BeaconNode>,
        includeAllBle: Boolean,
        maxAgeMillis: Long
    ): List<DetectedBeacon> {
        val now = System.currentTimeMillis()
        val placedById = placedNodes.associateBy { it.beaconId.lowercase() }
        val placedByMac =
            placedNodes
                .mapNotNull { node -> node.macAddress?.lowercase()?.let { it to node } }
                .toMap()

        pruneStale(discoveredBeacons, now, maxAgeMillis)
        pruneStale(allDiscoveredBle, now, maxAgeMillis)

        val source =
            if (includeAllBle) {
                val merged = LinkedHashMap<String, DetectedBeacon>()
                allDiscoveredBle.values.forEach { merged[it.macAddress] = it }
                discoveredBeacons.values.forEach { merged[it.macAddress] = it }
                merged.values
            } else {
                discoveredBeacons.values
            }

        return source
            .map { beacon ->
                val placed =
                    placedById[beacon.beaconId.lowercase()]
                        ?: placedByMac[beacon.macAddress.lowercase()]
                if (placed != null) beacon.copy(label = placed.label) else beacon
            }.sortedByDescending { it.rssi }
    }

    private fun pruneStale(
        map: ConcurrentHashMap<String, DetectedBeacon>,
        now: Long,
        maxAgeMillis: Long
    ) {
        val iterator = map.entries.iterator()
        while (iterator.hasNext()) {
            if (now - iterator.next().value.lastSeenMillis > maxAgeMillis) {
                iterator.remove()
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun handleScanResult(result: ScanResult) {
        val record = result.scanRecord ?: return
        val mac = result.device?.address ?: return
        val rssi = result.rssi
        val now = System.currentTimeMillis()
        val deviceName = record.deviceName ?: result.device?.name.orEmpty()
        val mfgHex = extractManufacturerHex(record.manufacturerSpecificData)

        val fallbackTx = if (record.txPowerLevel != Int.MIN_VALUE) record.txPowerLevel else -20
        allDiscoveredBle["BLE:$mac"] =
            DetectedBeacon(
                beaconId = "BLE:$mac",
                macAddress = mac,
                label = deviceName.ifBlank { "BLE $mac" },
                rssi = rssi,
                txPowerAt0m = fallbackTx,
                estimatedDistanceMeters = RssiDistanceEstimator.estimateDistanceMeters(rssi, fallbackTx),
                frame =
                    EddystoneFrame.InodeFrame(
                        deviceName = deviceName.ifBlank { mac },
                        manufacturerHex = mfgHex
                    ),
                telemetry = telemetryByMac[mac],
                lastSeenMillis = now
            )

        // 1. Check Eddystone Service Data (0xFEAA)
        val eddystoneData = record.getServiceData(EDDYSTONE_SERVICE_UUID)
        if (eddystoneData != null) {
            handleEddystoneData(eddystoneData, mac, deviceName, rssi, now)
            return
        }

        // 2. Check Apple iBeacon (Company ID 0x004C, prefix 0x02 0x15)
        val appleMfg = record.getManufacturerSpecificData(0x004C)
        val iBeaconFrame = appleMfg?.let { EddystonePacketParser.parseIBeacon(it) }
        if (iBeaconFrame != null) {
            val txAt0m = RssiDistanceEstimator.txPowerAt1mTo0m(iBeaconFrame.txPowerAt1m)
            val beaconId = "IBEACON:${iBeaconFrame.uuid}:${iBeaconFrame.major}:${iBeaconFrame.minor}"
            discoveredBeacons[beaconId] =
                DetectedBeacon(
                    beaconId = beaconId,
                    macAddress = mac,
                    label = deviceName.ifBlank { "iBeacon ${iBeaconFrame.major}:${iBeaconFrame.minor}" },
                    rssi = rssi,
                    txPowerAt0m = txAt0m,
                    estimatedDistanceMeters = RssiDistanceEstimator.estimateDistanceMeters(rssi, txAt0m),
                    frame = iBeaconFrame,
                    telemetry = telemetryByMac[mac],
                    lastSeenMillis = now
                )
            return
        }

        // 3. Check iNode BLE beacons by name or Elsat/iNode manufacturer ID
        val isInodeMfg =
            (0x9082..0x908F).any { record.getManufacturerSpecificData(it) != null } ||
                (0x8290..0x829F).any { record.getManufacturerSpecificData(it) != null }
        if (deviceName.contains("inode", ignoreCase = true) || isInodeMfg) {
            val beaconId = "INODE:$mac"
            discoveredBeacons[beaconId] =
                DetectedBeacon(
                    beaconId = beaconId,
                    macAddress = mac,
                    label = deviceName.ifBlank { "iNode $mac" },
                    rssi = rssi,
                    txPowerAt0m = fallbackTx,
                    estimatedDistanceMeters = RssiDistanceEstimator.estimateDistanceMeters(rssi, fallbackTx),
                    frame =
                        EddystoneFrame.InodeFrame(
                            deviceName = deviceName.ifBlank { mac },
                            manufacturerHex = mfgHex
                        ),
                    telemetry = telemetryByMac[mac],
                    lastSeenMillis = now
                )
        }
    }

    private fun handleEddystoneData(
        eddystoneData: ByteArray,
        mac: String,
        deviceName: String,
        rssi: Int,
        now: Long
    ) {
        val frame = EddystonePacketParser.parse(eddystoneData) ?: return
        when (frame) {
            is EddystoneFrame.TlmFrame -> updateTelemetry(mac, frame, rssi, now)
            is EddystoneFrame.UidFrame -> {
                val beaconId = "UID:${frame.instanceId}"
                val dist = RssiDistanceEstimator.estimateDistanceMeters(rssi, frame.rangingDataTxPower)
                val label = deviceName.ifBlank { "Eddystone ${frame.instanceId.takeLast(6)}" }
                discoveredBeacons[beaconId] =
                    DetectedBeacon(
                        beaconId = beaconId,
                        macAddress = mac,
                        label = label,
                        rssi = rssi,
                        txPowerAt0m = frame.rangingDataTxPower,
                        estimatedDistanceMeters = dist,
                        frame = frame,
                        telemetry = telemetryByMac[mac],
                        lastSeenMillis = now
                    )
            }
            is EddystoneFrame.UrlFrame -> {
                val beaconId = "URL:$mac"
                val dist = RssiDistanceEstimator.estimateDistanceMeters(rssi, frame.rangingDataTxPower)
                val label = deviceName.ifBlank { frame.url.removePrefix("https://").removePrefix("http://") }
                discoveredBeacons[beaconId] =
                    DetectedBeacon(
                        beaconId = beaconId,
                        macAddress = mac,
                        label = label,
                        rssi = rssi,
                        txPowerAt0m = frame.rangingDataTxPower,
                        estimatedDistanceMeters = dist,
                        frame = frame,
                        telemetry = telemetryByMac[mac],
                        lastSeenMillis = now
                    )
            }
            is EddystoneFrame.IBeaconFrame,
            is EddystoneFrame.InodeFrame,
            is EddystoneFrame.UnknownFrame -> Unit
        }
    }

    private fun updateTelemetry(
        mac: String,
        frame: EddystoneFrame.TlmFrame,
        rssi: Int,
        now: Long
    ) {
        telemetryByMac[mac] = frame
        discoveredBeacons.entries.forEach { (key, existing) ->
            if (existing.macAddress == mac) {
                discoveredBeacons[key] =
                    existing.copy(
                        telemetry = frame,
                        rssi = rssi,
                        lastSeenMillis = now
                    )
            }
        }
    }

    private fun extractManufacturerHex(sparse: android.util.SparseArray<ByteArray>?): String {
        if (sparse == null || sparse.isEmpty()) return ""
        val key = sparse.keyAt(0)
        val bytes = sparse.valueAt(0) ?: return ""
        return EddystonePacketParser.formatManufacturerHex(key, bytes)
    }

    companion object {
        val EDDYSTONE_SERVICE_UUID: ParcelUuid =
            ParcelUuid.fromString("0000FEAA-0000-1000-8000-00805F9B34FB")

        fun requiredPermissions(): List<String> =
            buildList {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    add(Manifest.permission.BLUETOOTH_SCAN)
                    add(Manifest.permission.BLUETOOTH_CONNECT)
                } else {
                    add(Manifest.permission.BLUETOOTH)
                    add(Manifest.permission.BLUETOOTH_ADMIN)
                }
                add(Manifest.permission.ACCESS_COARSE_LOCATION)
                add(Manifest.permission.ACCESS_FINE_LOCATION)
            }

        fun requiredRuntimePermissions(): List<String> =
            buildList {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    add(Manifest.permission.BLUETOOTH_SCAN)
                    add(Manifest.permission.BLUETOOTH_CONNECT)
                }
                add(Manifest.permission.ACCESS_COARSE_LOCATION)
                add(Manifest.permission.ACCESS_FINE_LOCATION)
            }
    }
}
