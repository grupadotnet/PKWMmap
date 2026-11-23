package pkwmmap.platform.ble

import pkwmmap.domain.model.EddystoneFrame
import pkwmmap.domain.positioning.RssiDistanceEstimator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class EddystonePacketParserTest {
    @Test
    fun `parses Eddystone-UID frame accurately`() {
        val payload =
            MockEddystoneScanner.buildUidPayload(
                txPower = -20,
                namespaceHex = "EDD1EBEAC04E5DEFA017",
                instanceHex = "000000000005"
            )

        val frame = EddystonePacketParser.parse(payload)
        assertIs<EddystoneFrame.UidFrame>(frame)
        assertEquals(-20, frame.rangingDataTxPower)
        assertEquals("EDD1EBEAC04E5DEFA017", frame.namespaceId)
        assertEquals("000000000005", frame.instanceId)
    }

    @Test
    fun `parses Eddystone-URL frame with https prefix`() {
        val payload =
            MockEddystoneScanner.buildUrlPayload(
                txPower = -18,
                schemeCode = 0x03, // https://
                domain = "pkwm.local/lab"
            )

        val frame = EddystonePacketParser.parse(payload)
        assertIs<EddystoneFrame.UrlFrame>(frame)
        assertEquals(-18, frame.rangingDataTxPower)
        assertEquals("https://pkwm.local/lab", frame.url)
    }

    @Test
    fun `parses Eddystone-TLM frame`() {
        val payload =
            byteArrayOf(
                0x20, // TLM
                0x00, // version 0
                0x0B,
                0xB8.toByte(), // 3000 mV
                0x16,
                0x80.toByte(), // 22.5 C (22*256 + 128 = 5760 = 0x1680)
                0x00,
                0x00,
                0x03,
                0xE8.toByte(), // 1000 PDUs
                0x00,
                0x00,
                0x00,
                0x64 // 100 deciseconds = 10s
            )

        val frame = EddystonePacketParser.parse(payload)
        assertIs<EddystoneFrame.TlmFrame>(frame)
        assertEquals(3000, frame.batteryMv)
        assertEquals(22.5f, frame.temperatureCelsius)
        assertEquals(1000L, frame.pduCount)
        assertEquals(10L, frame.uptimeSeconds)
    }

    @Test
    fun `distance increases as RSSI drops`() {
        val close = RssiDistanceEstimator.estimateDistanceMeters(rssi = -58, txPowerAt0m = -20)
        val far = RssiDistanceEstimator.estimateDistanceMeters(rssi = -82, txPowerAt0m = -20)
        assertTrue(far > close, "Expected far ($far) > close ($close)")
    }

    @Test
    fun `parses Apple iBeacon manufacturer payload`() {
        val payload =
            byteArrayOf(
                0x02,
                0x15,
                0xFD.toByte(),
                0xA5.toByte(),
                0x06,
                0x93.toByte(),
                0xA4.toByte(),
                0xE2.toByte(),
                0x4F,
                0xB1.toByte(),
                0xAF.toByte(),
                0xCF.toByte(),
                0xC6.toByte(),
                0xEB.toByte(),
                0x07,
                0x64,
                0x78,
                0x25,
                0x00,
                0x0A, // major = 10
                0x00,
                0x14, // minor = 20
                0xC5.toByte() // -59 dBm at 1m
            )

        val frame = EddystonePacketParser.parseManufacturerData(EddystonePacketParser.APPLE_COMPANY_ID, payload)
        assertIs<EddystoneFrame.IBeaconFrame>(frame)
        assertEquals("FDA50693-A4E2-4FB1-AFCF-C6EB07647825", frame.uuid)
        assertEquals(10, frame.major)
        assertEquals(20, frame.minor)
        assertEquals(-59, frame.txPowerAt1m)
    }

    @Test
    fun `detects iNode manufacturer IDs and parses iNode frame`() {
        assertTrue(EddystonePacketParser.isInodeManufacturerId(0x9082))
        assertTrue(EddystonePacketParser.isInodeManufacturerId(0x829F))

        val frame =
            EddystonePacketParser.parseManufacturerData(
                companyId = 0x9082,
                payload = byteArrayOf(0x01, 0x02, 0xAA.toByte()),
                deviceName = "iNode-A01"
            )
        assertIs<EddystoneFrame.InodeFrame>(frame)
        assertEquals("iNode-A01", frame.deviceName)
        assertEquals("0x9082:0102AA", frame.manufacturerHex)
    }
}
