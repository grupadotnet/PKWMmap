package pkwmmap.platform.ble

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.CoreBluetooth.CBAdvertisementDataLocalNameKey
import platform.CoreBluetooth.CBAdvertisementDataManufacturerDataKey
import platform.CoreBluetooth.CBAdvertisementDataServiceDataKey
import platform.CoreBluetooth.CBAdvertisementDataTxPowerLevelKey
import platform.CoreBluetooth.CBCentralManager
import platform.CoreBluetooth.CBCentralManagerDelegateProtocol
import platform.CoreBluetooth.CBCentralManagerScanOptionAllowDuplicatesKey
import platform.CoreBluetooth.CBManager
import platform.CoreBluetooth.CBManagerAuthorizationAllowedAlways
import platform.CoreBluetooth.CBManagerAuthorizationDenied
import platform.CoreBluetooth.CBManagerAuthorizationNotDetermined
import platform.CoreBluetooth.CBManagerAuthorizationRestricted
import platform.CoreBluetooth.CBManagerStatePoweredOff
import platform.CoreBluetooth.CBManagerStatePoweredOn
import platform.CoreBluetooth.CBManagerStateResetting
import platform.CoreBluetooth.CBManagerStateUnauthorized
import platform.CoreBluetooth.CBManagerStateUnknown
import platform.CoreBluetooth.CBManagerStateUnsupported
import platform.CoreBluetooth.CBPeripheral
import platform.CoreBluetooth.CBUUID
import platform.Foundation.NSData
import platform.Foundation.NSDate
import platform.Foundation.NSLock
import platform.Foundation.NSNumber
import platform.Foundation.timeIntervalSince1970
import platform.darwin.NSObject
import platform.posix.memcpy
import pkwmmap.domain.model.BeaconNode
import pkwmmap.domain.model.DetectedBeacon
import pkwmmap.domain.model.EddystoneFrame
import pkwmmap.domain.positioning.RssiDistanceEstimator

/**
 * Real iOS CoreBluetooth BLE scanner for Eddystone (0xFEAA), iBeacon (when exposed),
 * and iNode BLE beacons.
 *
 * Requires `NSBluetoothAlwaysUsageDescription` in the iOS app's `Info.plist`.
 */
class IosBleEddystoneScanner : BleScanner {
    private val _status = MutableStateFlow(BleScanStatus.IDLE)
    override val status: StateFlow<BleScanStatus> = _status.asStateFlow()

    private val _statusDetail = MutableStateFlow("BLE scanner idle")
    override val statusDetail: StateFlow<String> = _statusDetail.asStateFlow()

    private val stateLock = NSLock()
    private val discoveredBeacons = LinkedHashMap<String, DetectedBeacon>()
    private val allDiscoveredBle = LinkedHashMap<String, DetectedBeacon>()
    private val telemetryByPeripheral = LinkedHashMap<String, EddystoneFrame.TlmFrame>()

    private var centralManager: CBCentralManager? = null
    private var isScanning = false
    private var pendingStartScan = false

    private val delegate =
        object : NSObject(), CBCentralManagerDelegateProtocol {
            override fun centralManagerDidUpdateState(central: CBCentralManager) {
                handleStateUpdate(central)
            }

            override fun centralManager(
                central: CBCentralManager,
                didDiscoverPeripheral: CBPeripheral,
                advertisementData: Map<Any?, *>,
                RSSI: NSNumber
            ) {
                handlePeripheralDiscovery(
                    peripheral = didDiscoverPeripheral,
                    advertisementData = advertisementData,
                    rssiValue = RSSI.intValue
                )
            }
        }

    override fun hasRequiredPermissions(): Boolean =
        when (CBManager.authorization) {
            CBManagerAuthorizationAllowedAlways,
            CBManagerAuthorizationNotDetermined -> true
            CBManagerAuthorizationDenied,
            CBManagerAuthorizationRestricted -> false
            else -> false
        }

    override fun startScanning() {
        if (isScanning) return

        if (!hasRequiredPermissions()) {
            pendingStartScan = false
            _status.value = BleScanStatus.MISSING_PERMISSIONS
            _statusDetail.value = "Grant Bluetooth permissions in iOS Settings to scan real beacons."
            return
        }

        val manager = ensureCentralManager()
        when (manager.state) {
            CBManagerStatePoweredOn -> {
                pendingStartScan = false
                beginCoreBluetoothScan(manager)
            }
            CBManagerStateUnknown,
            CBManagerStateResetting -> {
                pendingStartScan = true
                _status.value = BleScanStatus.IDLE
                _statusDetail.value = "Initializing CoreBluetooth..."
            }
            CBManagerStateUnsupported -> {
                pendingStartScan = false
                _status.value = BleScanStatus.NO_HARDWARE
                _statusDetail.value = "No Bluetooth LE hardware on this iOS device."
            }
            CBManagerStateUnauthorized -> {
                pendingStartScan = false
                _status.value = BleScanStatus.MISSING_PERMISSIONS
                _statusDetail.value = "Bluetooth permission denied. Enable it in iOS Settings."
            }
            CBManagerStatePoweredOff -> {
                pendingStartScan = false
                _status.value = BleScanStatus.BLUETOOTH_OFF
                _statusDetail.value = "Bluetooth is turned off. Enable Bluetooth to scan."
            }
            else -> {
                pendingStartScan = false
                _status.value = BleScanStatus.ERROR
                _statusDetail.value = "Unexpected CoreBluetooth state (${manager.state})."
            }
        }
    }

    override fun stopScanning() {
        pendingStartScan = false
        if (!isScanning) {
            if (_status.value == BleScanStatus.SCANNING) {
                _status.value = BleScanStatus.IDLE
                _statusDetail.value = "BLE scanner idle"
            }
            return
        }
        centralManager?.stopScan()
        isScanning = false
        _status.value = BleScanStatus.IDLE
        _statusDetail.value = "BLE scanner idle"
    }

    override fun snapshot(
        placedNodes: List<BeaconNode>,
        includeAllBle: Boolean,
        maxAgeMillis: Long
    ): List<DetectedBeacon> {
        val now = currentTimeMillis()
        val placedById = placedNodes.associateBy { it.beaconId.lowercase() }
        val placedByMac =
            placedNodes
                .mapNotNull { node -> node.macAddress?.lowercase()?.let { it to node } }
                .toMap()

        stateLock.lock()
        val source =
            try {
                pruneStale(discoveredBeacons, now, maxAgeMillis)
                pruneStale(allDiscoveredBle, now, maxAgeMillis)

                if (includeAllBle) {
                    val merged = LinkedHashMap<String, DetectedBeacon>()
                    allDiscoveredBle.values.forEach { merged[it.macAddress] = it }
                    discoveredBeacons.values.forEach { merged[it.macAddress] = it }
                    merged.values.toList()
                } else {
                    discoveredBeacons.values.toList()
                }
            } finally {
                stateLock.unlock()
            }

        return source
            .map { beacon ->
                val placed =
                    placedById[beacon.beaconId.lowercase()]
                        ?: placedByMac[beacon.macAddress.lowercase()]
                if (placed != null) beacon.copy(label = placed.label) else beacon
            }.sortedByDescending { it.rssi }
    }

    private fun ensureCentralManager(): CBCentralManager {
        val existing = centralManager
        if (existing != null) return existing
        val created = CBCentralManager(delegate = delegate, queue = null)
        centralManager = created
        return created
    }

    private fun beginCoreBluetoothScan(manager: CBCentralManager) {
        val options = mapOf<Any?, Any>(CBCentralManagerScanOptionAllowDuplicatesKey to true)
        manager.scanForPeripheralsWithServices(serviceUUIDs = null, options = options)
        isScanning = true
        _status.value = BleScanStatus.SCANNING
        _statusDetail.value = "Scanning CoreBluetooth for Eddystone & iNode beacons..."
    }

    private fun handleStateUpdate(central: CBCentralManager) {
        when (central.state) {
            CBManagerStatePoweredOn -> {
                if (pendingStartScan && !isScanning) {
                    pendingStartScan = false
                    beginCoreBluetoothScan(central)
                } else if (!isScanning) {
                    _status.value = BleScanStatus.IDLE
                    _statusDetail.value = "Bluetooth ready. Scanner idle."
                }
            }
            CBManagerStatePoweredOff -> {
                isScanning = false
                pendingStartScan = false
                _status.value = BleScanStatus.BLUETOOTH_OFF
                _statusDetail.value = "Bluetooth is turned off. Enable Bluetooth to scan."
            }
            CBManagerStateUnauthorized -> {
                isScanning = false
                pendingStartScan = false
                _status.value = BleScanStatus.MISSING_PERMISSIONS
                _statusDetail.value = "Bluetooth permission denied. Enable it in iOS Settings."
            }
            CBManagerStateUnsupported -> {
                isScanning = false
                pendingStartScan = false
                _status.value = BleScanStatus.NO_HARDWARE
                _statusDetail.value = "No Bluetooth LE hardware on this iOS device."
            }
            CBManagerStateResetting,
            CBManagerStateUnknown -> {
                isScanning = false
                _status.value = BleScanStatus.IDLE
                _statusDetail.value = "Resetting CoreBluetooth..."
            }
            else -> {
                isScanning = false
                _status.value = BleScanStatus.ERROR
                _statusDetail.value = "CoreBluetooth state changed (${central.state})."
            }
        }
    }

    private fun handlePeripheralDiscovery(
        peripheral: CBPeripheral,
        advertisementData: Map<Any?, *>,
        rssiValue: Int
    ) {
        if (rssiValue == UNAVAILABLE_RSSI || rssiValue > 0) return

        val peripheralUuid = peripheral.identifier.UUIDString
        val now = currentTimeMillis()
        val deviceName =
            (advertisementData[CBAdvertisementDataLocalNameKey] as? String)
                ?: peripheral.name.orEmpty()
        val fallbackTx =
            (advertisementData[CBAdvertisementDataTxPowerLevelKey] as? NSNumber)?.intValue
                ?: DEFAULT_TX_POWER_AT_0M

        val mfgBytes = (advertisementData[CBAdvertisementDataManufacturerDataKey] as? NSData)?.toByteArray()
        val companyId = extractCompanyId(mfgBytes)
        val mfgPayload =
            if (mfgBytes != null && mfgBytes.size > 2) {
                mfgBytes.copyOfRange(2, mfgBytes.size)
            } else {
                ByteArray(0)
            }
        val mfgHex =
            if (companyId != null) {
                EddystonePacketParser.formatManufacturerHex(companyId, mfgPayload)
            } else {
                ""
            }

        stateLock.lock()
        try {
            allDiscoveredBle["BLE:$peripheralUuid"] =
                DetectedBeacon(
                    beaconId = "BLE:$peripheralUuid",
                    macAddress = peripheralUuid,
                    label = deviceName.ifBlank { "BLE $peripheralUuid" },
                    rssi = rssiValue,
                    txPowerAt0m = fallbackTx,
                    estimatedDistanceMeters = RssiDistanceEstimator.estimateDistanceMeters(rssiValue, fallbackTx),
                    frame =
                        EddystoneFrame.InodeFrame(
                            deviceName = deviceName.ifBlank { peripheralUuid },
                            manufacturerHex = mfgHex
                        ),
                    telemetry = telemetryByPeripheral[peripheralUuid],
                    lastSeenMillis = now
                )

            val eddystoneData = extractEddystoneServiceData(advertisementData)
            if (eddystoneData != null) {
                handleEddystoneData(eddystoneData, peripheralUuid, deviceName, rssiValue, now)
                return
            }

            if (companyId != null) {
                val frame = EddystonePacketParser.parseManufacturerData(companyId, mfgPayload, deviceName)
                if (frame != null) {
                    recordManufacturerBeacon(frame, peripheralUuid, deviceName, rssiValue, fallbackTx, now)
                    return
                }
            }

            if (deviceName.contains("inode", ignoreCase = true)) {
                val beaconId = "INODE:$peripheralUuid"
                discoveredBeacons[beaconId] =
                    DetectedBeacon(
                        beaconId = beaconId,
                        macAddress = peripheralUuid,
                        label = deviceName.ifBlank { "iNode $peripheralUuid" },
                        rssi = rssiValue,
                        txPowerAt0m = fallbackTx,
                        estimatedDistanceMeters = RssiDistanceEstimator.estimateDistanceMeters(rssiValue, fallbackTx),
                        frame =
                            EddystoneFrame.InodeFrame(
                                deviceName = deviceName,
                                manufacturerHex = mfgHex
                            ),
                        telemetry = telemetryByPeripheral[peripheralUuid],
                        lastSeenMillis = now
                    )
            }
        } finally {
            stateLock.unlock()
        }
    }

    private fun handleEddystoneData(
        eddystoneData: ByteArray,
        peripheralUuid: String,
        deviceName: String,
        rssi: Int,
        now: Long
    ) {
        val frame = EddystonePacketParser.parse(eddystoneData) ?: return
        when (frame) {
            is EddystoneFrame.TlmFrame -> {
                telemetryByPeripheral[peripheralUuid] = frame
                discoveredBeacons.entries.forEach { (key, existing) ->
                    if (existing.macAddress == peripheralUuid) {
                        discoveredBeacons[key] =
                            existing.copy(
                                telemetry = frame,
                                rssi = rssi,
                                lastSeenMillis = now
                            )
                    }
                }
            }
            is EddystoneFrame.UidFrame -> {
                val beaconId = "UID:${frame.instanceId}"
                val dist = RssiDistanceEstimator.estimateDistanceMeters(rssi, frame.rangingDataTxPower)
                val label = deviceName.ifBlank { "Eddystone ${frame.instanceId.takeLast(6)}" }
                discoveredBeacons[beaconId] =
                    DetectedBeacon(
                        beaconId = beaconId,
                        macAddress = peripheralUuid,
                        label = label,
                        rssi = rssi,
                        txPowerAt0m = frame.rangingDataTxPower,
                        estimatedDistanceMeters = dist,
                        frame = frame,
                        telemetry = telemetryByPeripheral[peripheralUuid],
                        lastSeenMillis = now
                    )
            }
            is EddystoneFrame.UrlFrame -> {
                val beaconId = "URL:$peripheralUuid"
                val dist = RssiDistanceEstimator.estimateDistanceMeters(rssi, frame.rangingDataTxPower)
                val label = deviceName.ifBlank { frame.url.removePrefix("https://").removePrefix("http://") }
                discoveredBeacons[beaconId] =
                    DetectedBeacon(
                        beaconId = beaconId,
                        macAddress = peripheralUuid,
                        label = label,
                        rssi = rssi,
                        txPowerAt0m = frame.rangingDataTxPower,
                        estimatedDistanceMeters = dist,
                        frame = frame,
                        telemetry = telemetryByPeripheral[peripheralUuid],
                        lastSeenMillis = now
                    )
            }
            is EddystoneFrame.IBeaconFrame,
            is EddystoneFrame.InodeFrame,
            is EddystoneFrame.UnknownFrame -> Unit
        }
    }

    private fun recordManufacturerBeacon(
        frame: EddystoneFrame,
        peripheralUuid: String,
        deviceName: String,
        rssi: Int,
        fallbackTx: Int,
        now: Long
    ) {
        when (frame) {
            is EddystoneFrame.IBeaconFrame -> {
                val txAt0m = RssiDistanceEstimator.txPowerAt1mTo0m(frame.txPowerAt1m)
                val beaconId = "IBEACON:${frame.uuid}:${frame.major}:${frame.minor}"
                discoveredBeacons[beaconId] =
                    DetectedBeacon(
                        beaconId = beaconId,
                        macAddress = peripheralUuid,
                        label = deviceName.ifBlank { "iBeacon ${frame.major}:${frame.minor}" },
                        rssi = rssi,
                        txPowerAt0m = txAt0m,
                        estimatedDistanceMeters = RssiDistanceEstimator.estimateDistanceMeters(rssi, txAt0m),
                        frame = frame,
                        telemetry = telemetryByPeripheral[peripheralUuid],
                        lastSeenMillis = now
                    )
            }
            is EddystoneFrame.InodeFrame -> {
                val beaconId = "INODE:$peripheralUuid"
                discoveredBeacons[beaconId] =
                    DetectedBeacon(
                        beaconId = beaconId,
                        macAddress = peripheralUuid,
                        label = deviceName.ifBlank { "iNode $peripheralUuid" },
                        rssi = rssi,
                        txPowerAt0m = fallbackTx,
                        estimatedDistanceMeters = RssiDistanceEstimator.estimateDistanceMeters(rssi, fallbackTx),
                        frame = frame.copy(deviceName = deviceName.ifBlank { peripheralUuid }),
                        telemetry = telemetryByPeripheral[peripheralUuid],
                        lastSeenMillis = now
                    )
            }
            else -> Unit
        }
    }

    private fun extractEddystoneServiceData(advertisementData: Map<Any?, *>): ByteArray? {
        val serviceData = advertisementData[CBAdvertisementDataServiceDataKey] as? Map<*, *> ?: return null
        for ((key, value) in serviceData) {
            val uuid = (key as? CBUUID)?.UUIDString?.uppercase() ?: continue
            if (uuid == EDDYSTONE_UUID_SHORT || uuid == EDDYSTONE_UUID_FULL) {
                return (value as? NSData)?.toByteArray()
            }
        }
        return null
    }

    private fun extractCompanyId(manufacturerData: ByteArray?): Int? {
        if (manufacturerData == null || manufacturerData.size < 2) return null
        return (manufacturerData[0].toInt() and 0xFF) or ((manufacturerData[1].toInt() and 0xFF) shl 8)
    }

    private fun pruneStale(
        map: MutableMap<String, DetectedBeacon>,
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

    private fun currentTimeMillis(): Long = (NSDate().timeIntervalSince1970 * 1000.0).toLong()

    @OptIn(ExperimentalForeignApi::class)
    private fun NSData.toByteArray(): ByteArray {
        val size = length.toInt()
        if (size == 0) return ByteArray(0)
        val bytes = ByteArray(size)
        bytes.usePinned { pinned ->
            memcpy(pinned.addressOf(0), this.bytes, length)
        }
        return bytes
    }

    companion object {
        private const val EDDYSTONE_UUID_SHORT = "FEAA"
        private const val EDDYSTONE_UUID_FULL = "0000FEAA-0000-1000-8000-00805F9B34FB"
        private const val UNAVAILABLE_RSSI = 127
        private const val DEFAULT_TX_POWER_AT_0M = -20
    }
}
