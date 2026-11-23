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
        val payload = MockEddystoneScanner.buildUidPayload(
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
        val payload = MockEddystoneScanner.buildUrlPayload(
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
        val payload = byteArrayOf(
            0x20, // TLM
            0x00, // version 0
            0x0B, 0xB8.toByte(), // 3000 mV
            0x16, 0x80.toByte(), // 22.5 C (22*256 + 128 = 5760 = 0x1680)
            0x00, 0x00, 0x03, 0xE8.toByte(), // 1000 PDUs
            0x00, 0x00, 0x00, 0x64 // 100 deciseconds = 10s
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
}
