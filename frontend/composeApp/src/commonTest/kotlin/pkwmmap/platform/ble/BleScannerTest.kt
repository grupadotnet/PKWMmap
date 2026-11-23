package pkwmmap.platform.ble

import pkwmmap.domain.model.BeaconNode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BleScannerTest {
    @Test
    fun `MockEddystoneScanner implements BleScanner lifecycle and returns beacons while scanning`() {
        val scanner: BleScanner = MockEddystoneScanner()
        assertEquals(BleScanStatus.IDLE, scanner.status.value)
        assertTrue(scanner.hasRequiredPermissions())
        assertTrue(scanner.snapshot(emptyList()).isEmpty())

        val placed =
            listOf(
                BeaconNode(
                    id = "node-1",
                    beaconId = "EDDY-UID-0001",
                    label = "Budynek A - Lobby",
                    x = 3.0f,
                    y = 14.0f
                )
            )

        scanner.startScanning()
        assertEquals(BleScanStatus.SCANNING, scanner.status.value)

        val beacons = scanner.snapshot(placed)
        assertTrue(beacons.isNotEmpty())
        assertEquals("Budynek A - Lobby", beacons.first { it.beaconId == "EDDY-UID-0001" }.label)

        scanner.stopScanning()
        assertEquals(BleScanStatus.IDLE, scanner.status.value)
        assertTrue(scanner.snapshot(placed).isEmpty())
    }

    @Test
    fun `MockEddystoneScanner reports MISSING_PERMISSIONS when permissions are denied`() {
        val scanner: BleScanner = MockEddystoneScanner(permissionsGranted = false)
        assertFalse(scanner.hasRequiredPermissions())

        scanner.startScanning()
        assertEquals(BleScanStatus.MISSING_PERMISSIONS, scanner.status.value)
    }

    @Test
    fun `MockEddystoneScanner updateSimulatedPosition changes nearest beacon RSSI`() {
        val scanner = MockEddystoneScanner()
        scanner.startScanning()

        scanner.updateSimulatedPosition(userX = 3.0f, userY = 14.0f)
        val nearLobby = scanner.snapshot(emptyList()).first()
        assertEquals("EDDY-UID-0001", nearLobby.beaconId)

        scanner.updateSimulatedPosition(userX = 15.5f, userY = 7.0f)
        val nearLab = scanner.snapshot(emptyList()).first()
        assertEquals("EDDY-URL-0004", nearLab.beaconId)
    }

    @Test
    fun `MockBlePermissionController transitions through request flow`() {
        val grantController: BlePermissionController =
            MockBlePermissionController(initialGranted = false, grantOnRequest = true)
        assertEquals(BlePermissionStatus.NOT_REQUESTED, grantController.status.value)
        assertFalse(grantController.hasAllPermissions())

        var grantedResult = false
        grantController.requestPermissions { grantedResult = it }
        assertTrue(grantedResult)
        assertTrue(grantController.hasAllPermissions())
        assertEquals(BlePermissionStatus.GRANTED, grantController.status.value)
        assertTrue(grantController.missingPermissions.value.isEmpty())

        val denyController = MockBlePermissionController(initialGranted = false, grantOnRequest = false)
        var deniedResult = true
        denyController.requestPermissions { deniedResult = it }
        assertFalse(deniedResult)
        assertEquals(BlePermissionStatus.DENIED, denyController.status.value)
        assertTrue(denyController.shouldShowRationale.value)

        denyController.openAppSettings()
        assertEquals(1, denyController.openSettingsInvocations)
    }
}
