package pkwmmap.platform.ble

import pkwmmap.domain.model.BeaconNode
import pkwmmap.domain.model.DetectedBeacon
import pkwmmap.domain.model.EddystoneFrame
import pkwmmap.domain.positioning.RssiDistanceEstimator
import kotlin.math.hypot
import kotlin.random.Random

enum class BleScanStatus {
    IDLE,
    SCANNING,
    NO_HARDWARE,
    BLUETOOTH_OFF,
    MISSING_PERMISSIONS,
    ERROR
}

/**
 * Simulates nearby BLE Eddystone advertisements (UID, URL, and TLM frames) in pure KMP Kotlin.
 *
 * Distances and RSSI values dynamically react to the simulated user position relative to
 * placed map beacons as well as unplaced ambient beacons in the environment.
 */
class MockEddystoneScanner {
    data class SimulatedPhysicalBeacon(
        val beaconId: String,
        val macAddress: String,
        val defaultLabel: String,
        val txPowerAt0m: Int,
        val rawServiceData: ByteArray,
        val physicalX: Float,
        val physicalY: Float,
        val batteryMv: Int,
        val temperatureCelsius: Float,
        var pduCount: Long
    )

    private val random = Random(42)
    private var spawnCounter = 1
    private var tickCounter = 0L

    private val ambientBeacons =
        mutableListOf(
            SimulatedPhysicalBeacon(
                beaconId = "EDDY-UID-0001",
                macAddress = "D4:36:39:A1:00:01",
                defaultLabel = "Beacon #01 (Lobby)",
                txPowerAt0m = -20,
                rawServiceData = buildUidPayload(txPower = -20, namespaceHex = "EDD1EBEAC04E5DEFA017", instanceHex = "000000000001"),
                physicalX = 3.0f,
                physicalY = 14.0f,
                batteryMv = 3020,
                temperatureCelsius = 22.5f,
                pduCount = 14_200L
            ),
            SimulatedPhysicalBeacon(
                beaconId = "EDDY-UID-0002",
                macAddress = "D4:36:39:A1:00:02",
                defaultLabel = "Beacon #02 (Corridor)",
                txPowerAt0m = -21,
                rawServiceData = buildUidPayload(txPower = -21, namespaceHex = "EDD1EBEAC04E5DEFA017", instanceHex = "000000000002"),
                physicalX = 9.0f,
                physicalY = 14.0f,
                batteryMv = 2980,
                temperatureCelsius = 23.0f,
                pduCount = 18_450L
            ),
            SimulatedPhysicalBeacon(
                beaconId = "EDDY-UID-0003",
                macAddress = "D4:36:39:A1:00:03",
                defaultLabel = "Beacon #03 (Atrium)",
                txPowerAt0m = -19,
                rawServiceData = buildUidPayload(txPower = -19, namespaceHex = "EDD1EBEAC04E5DEFA017", instanceHex = "000000000003"),
                physicalX = 9.0f,
                physicalY = 7.0f,
                batteryMv = 2910,
                temperatureCelsius = 24.1f,
                pduCount = 9_110L
            ),
            SimulatedPhysicalBeacon(
                beaconId = "EDDY-URL-0004",
                macAddress = "D4:36:39:A1:00:04",
                defaultLabel = "Beacon #04 (Lab 204)",
                txPowerAt0m = -20,
                rawServiceData = buildUrlPayload(txPower = -20, schemeCode = 0x03, domain = "pkwm.local/lab204"),
                physicalX = 15.5f,
                physicalY = 7.0f,
                batteryMv = 2850,
                temperatureCelsius = 21.8f,
                pduCount = 22_004L
            ),
            SimulatedPhysicalBeacon(
                beaconId = "EDDY-UID-0005",
                macAddress = "D4:36:39:A1:00:05",
                defaultLabel = "Beacon #05 (Unplaced)",
                txPowerAt0m = -20,
                rawServiceData = buildUidPayload(txPower = -20, namespaceHex = "EDD1EBEAC04E5DEFA017", instanceHex = "000000000005"),
                physicalX = 4.5f,
                physicalY = 8.0f,
                batteryMv = 3100,
                temperatureCelsius = 22.0f,
                pduCount = 3_412L
            ),
            SimulatedPhysicalBeacon(
                beaconId = "EDDY-URL-0006",
                macAddress = "D4:36:39:A1:00:06",
                defaultLabel = "Beacon #06 (Unplaced)",
                txPowerAt0m = -22,
                rawServiceData = buildUrlPayload(txPower = -22, schemeCode = 0x03, domain = "pkwm.local/cafe"),
                physicalX = 14.0f,
                physicalY = 13.5f,
                batteryMv = 2790,
                temperatureCelsius = 25.2f,
                pduCount = 31_880L
            ),
            SimulatedPhysicalBeacon(
                beaconId = "EDDY-UID-0007",
                macAddress = "D4:36:39:A1:00:07",
                defaultLabel = "Beacon #07 (Unplaced)",
                txPowerAt0m = -20,
                rawServiceData = buildUidPayload(txPower = -20, namespaceHex = "EDD1EBEAC04E5DEFA017", instanceHex = "000000000007"),
                physicalX = 10.5f,
                physicalY = 3.0f,
                batteryMv = 3050,
                temperatureCelsius = 21.4f,
                pduCount = 1_240L
            )
        )

    fun scanTick(
        userX: Float,
        userY: Float,
        placedNodes: List<BeaconNode>,
        nowMillis: Long = nextTickMillis()
    ): List<DetectedBeacon> {
        val placedById = placedNodes.associateBy { it.beaconId }

        return ambientBeacons
            .mapNotNull { sim ->
                val placed = placedById[sim.beaconId]
                val bx = placed?.x ?: sim.physicalX
                val by = placed?.y ?: sim.physicalY
                val trueDistance = hypot((bx - userX).toDouble(), (by - userY).toDouble()).coerceAtLeast(0.3)

                if (trueDistance > 28.0) return@mapNotNull null

                val jitter = random.nextInt(-2, 3)
                val rssi =
                    (RssiDistanceEstimator.rssiForDistanceMeters(trueDistance, sim.txPowerAt0m) + jitter)
                        .coerceIn(-99, -28)
                val estimatedDist = RssiDistanceEstimator.estimateDistanceMeters(rssi, sim.txPowerAt0m)

                val parsedFrame =
                    EddystonePacketParser.parse(sim.rawServiceData)
                        ?: EddystoneFrame.UnknownFrame(0xFF)

                sim.pduCount += random.nextLong(1, 5)
                val tlm =
                    EddystoneFrame.TlmFrame(
                        version = 0,
                        batteryMv = sim.batteryMv,
                        temperatureCelsius = sim.temperatureCelsius,
                        pduCount = sim.pduCount,
                        uptimeSeconds = nowMillis / 1000L
                    )

                DetectedBeacon(
                    beaconId = sim.beaconId,
                    macAddress = sim.macAddress,
                    label = placed?.label ?: sim.defaultLabel,
                    rssi = rssi,
                    txPowerAt0m = sim.txPowerAt0m,
                    estimatedDistanceMeters = estimatedDist,
                    frame = parsedFrame,
                    telemetry = tlm,
                    lastSeenMillis = nowMillis
                )
            }.sortedByDescending { it.rssi }
    }

    fun spawnNearbyBeacon(
        nearX: Float,
        nearY: Float
    ): DetectedBeacon {
        val idx = 7 + spawnCounter++
        val suffixHex = idx.toString(16).padStart(2, '0').uppercase()
        val instanceHex = idx.toString(16).padStart(12, '0').uppercase()
        val id = "EDDY-UID-NEW-$idx"
        val offsetX = (nearX + random.nextFloat() * 3f - 1.5f).coerceIn(1f, 19f)
        val offsetY = (nearY + random.nextFloat() * 3f - 1.5f).coerceIn(1f, 19f)

        val sim =
            SimulatedPhysicalBeacon(
                beaconId = id,
                macAddress = "D4:36:39:B2:00:$suffixHex",
                defaultLabel = "New Beacon #$idx",
                txPowerAt0m = -20,
                rawServiceData = buildUidPayload(-20, "EDD1EBEAC04E5DEFA017", instanceHex),
                physicalX = offsetX,
                physicalY = offsetY,
                batteryMv = 3150,
                temperatureCelsius = 22.1f,
                pduCount = 42L
            )
        ambientBeacons.add(sim)

        val dist = hypot((offsetX - nearX).toDouble(), (offsetY - nearY).toDouble()).coerceAtLeast(0.4)
        val rssi = RssiDistanceEstimator.rssiForDistanceMeters(dist, sim.txPowerAt0m)
        return DetectedBeacon(
            beaconId = sim.beaconId,
            macAddress = sim.macAddress,
            label = sim.defaultLabel,
            rssi = rssi,
            txPowerAt0m = sim.txPowerAt0m,
            estimatedDistanceMeters = dist,
            frame = EddystonePacketParser.parse(sim.rawServiceData)!!,
            telemetry = EddystoneFrame.TlmFrame(0, sim.batteryMv, sim.temperatureCelsius, sim.pduCount, 10L),
            lastSeenMillis = nextTickMillis()
        )
    }

    private fun nextTickMillis(): Long {
        tickCounter += 600L
        return tickCounter
    }

    companion object {
        fun buildUidPayload(
            txPower: Int,
            namespaceHex: String,
            instanceHex: String
        ): ByteArray {
            val nsBytes = hexToBytes(namespaceHex.padEnd(20, '0').take(20))
            val instBytes = hexToBytes(instanceHex.padEnd(12, '0').take(12))
            return byteArrayOf(
                EddystonePacketParser.FRAME_TYPE_UID.toByte(),
                txPower.toByte()
            ) + nsBytes + instBytes + byteArrayOf(0x00, 0x00)
        }

        fun buildUrlPayload(
            txPower: Int,
            schemeCode: Int,
            domain: String
        ): ByteArray =
            byteArrayOf(
                EddystonePacketParser.FRAME_TYPE_URL.toByte(),
                txPower.toByte(),
                schemeCode.toByte()
            ) + domain.encodeToByteArray()

        private fun hexToBytes(hex: String): ByteArray {
            val result = ByteArray(hex.length / 2)
            for (i in result.indices) {
                result[i] = hex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
            }
            return result
        }
    }
}
