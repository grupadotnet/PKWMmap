package pkwmmap.platform.ble

import pkwmmap.domain.model.EddystoneFrame

/**
 * Parses raw Eddystone BLE Service Data payloads (Service UUID 0xFEAA),
 * Apple iBeacon manufacturer payloads, and iNode manufacturer IDs in pure KMP Kotlin.
 */
object EddystonePacketParser {
    const val FRAME_TYPE_UID: Int = 0x00
    const val FRAME_TYPE_URL: Int = 0x10
    const val FRAME_TYPE_TLM: Int = 0x20
    const val APPLE_COMPANY_ID: Int = 0x004C

    private const val HEX_CHARS = "0123456789ABCDEF"

    private val URL_SCHEMES =
        arrayOf(
            "http://www.",
            "https://www.",
            "http://",
            "https://"
        )

    private val URL_EXPANSIONS =
        mapOf(
            0x00 to ".com/",
            0x01 to ".org/",
            0x02 to ".edu/",
            0x03 to ".net/",
            0x04 to ".info/",
            0x05 to ".biz/",
            0x06 to ".gov/",
            0x07 to ".com",
            0x08 to ".org",
            0x09 to ".edu",
            0x0A to ".net",
            0x0B to ".info",
            0x0C to ".biz",
            0x0D to ".gov"
        )

    fun parse(serviceData: ByteArray): EddystoneFrame? {
        if (serviceData.isEmpty()) return null
        val frameType = serviceData[0].toInt() and 0xFF
        return when (frameType) {
            FRAME_TYPE_UID -> parseUid(serviceData)
            FRAME_TYPE_URL -> parseUrl(serviceData)
            FRAME_TYPE_TLM -> parseTlm(serviceData)
            else -> EddystoneFrame.UnknownFrame(frameType)
        }
    }

    fun parseIBeacon(appleManufacturerData: ByteArray): EddystoneFrame.IBeaconFrame? {
        if (appleManufacturerData.size < 23) return null
        if ((appleManufacturerData[0].toInt() and 0xFF) != 0x02) return null
        if ((appleManufacturerData[1].toInt() and 0xFF) != 0x15) return null

        val uuid = formatUuid(appleManufacturerData.copyOfRange(2, 18))
        val major = readUInt16BigEndian(appleManufacturerData, 18)
        val minor = readUInt16BigEndian(appleManufacturerData, 20)
        val txAt1m = appleManufacturerData[22].toInt()
        return EddystoneFrame.IBeaconFrame(
            uuid = uuid,
            major = major,
            minor = minor,
            txPowerAt1m = txAt1m
        )
    }

    fun isInodeManufacturerId(companyId: Int): Boolean = companyId in 0x9082..0x908F || companyId in 0x8290..0x829F

    fun formatManufacturerHex(
        companyId: Int,
        payload: ByteArray
    ): String = "0x${companyId.toString(16).uppercase()}:${payload.toHexString()}"

    fun parseManufacturerData(
        companyId: Int,
        payload: ByteArray,
        deviceName: String = ""
    ): EddystoneFrame? {
        if (companyId == APPLE_COMPANY_ID) {
            val iBeacon = parseIBeacon(payload)
            if (iBeacon != null) return iBeacon
        }
        if (isInodeManufacturerId(companyId) || deviceName.contains("inode", ignoreCase = true)) {
            return EddystoneFrame.InodeFrame(
                deviceName = deviceName,
                manufacturerHex = formatManufacturerHex(companyId, payload)
            )
        }
        return null
    }

    fun ByteArray.toHexString(): String {
        val chars = CharArray(size * 2)
        for (i in indices) {
            val v = this[i].toInt() and 0xFF
            chars[i * 2] = HEX_CHARS[v ushr 4]
            chars[i * 2 + 1] = HEX_CHARS[v and 0x0F]
        }
        return chars.concatToString()
    }

    private fun parseUid(data: ByteArray): EddystoneFrame.UidFrame? {
        // 1 byte frameType + 1 byte txPower + 10 bytes namespace + 6 bytes instance = 18 bytes
        if (data.size < 18) return null
        val txPower = data[1].toInt() // signed byte
        val namespace = data.copyOfRange(2, 12).toHexString()
        val instance = data.copyOfRange(12, 18).toHexString()
        return EddystoneFrame.UidFrame(
            rangingDataTxPower = txPower,
            namespaceId = namespace,
            instanceId = instance
        )
    }

    private fun parseUrl(data: ByteArray): EddystoneFrame.UrlFrame? {
        // Minimum: frameType (1) + txPower (1) + scheme (1) + 1 char
        if (data.size < 4) return null
        val txPower = data[1].toInt()
        val schemeCode = data[2].toInt() and 0xFF
        val scheme = URL_SCHEMES.getOrNull(schemeCode) ?: return null

        val builder = StringBuilder(scheme)
        for (i in 3 until data.size) {
            val b = data[i].toInt() and 0xFF
            val expansion = URL_EXPANSIONS[b]
            if (expansion != null) {
                builder.append(expansion)
            } else if (b in 0x21..0x7E) {
                builder.append(b.toChar())
            }
        }
        return EddystoneFrame.UrlFrame(
            rangingDataTxPower = txPower,
            url = builder.toString()
        )
    }

    private fun parseTlm(data: ByteArray): EddystoneFrame.TlmFrame? {
        // Unencrypted TLM: frameType(1) + version(1) + vbatt(2) + temp(2) + advCnt(4) + secCnt(4) = 14 bytes
        if (data.size < 14) return null
        val version = data[1].toInt() and 0xFF
        if (version != 0x00) return null

        val batteryMv = readUInt16BigEndian(data, 2)
        val tempFixed88 = readInt16BigEndian(data, 4)
        val temperature = tempFixed88 / 256.0f
        val pduCount = readUInt32BigEndian(data, 6)
        val deciseconds = readUInt32BigEndian(data, 10)

        return EddystoneFrame.TlmFrame(
            version = version,
            batteryMv = batteryMv,
            temperatureCelsius = temperature,
            pduCount = pduCount,
            uptimeSeconds = deciseconds / 10L
        )
    }

    private fun formatUuid(b: ByteArray): String {
        val hex = b.toHexString()
        return "${hex.substring(0, 8)}-${hex.substring(8, 12)}-${hex.substring(12, 16)}-${hex.substring(16, 20)}-${hex.substring(20, 32)}"
    }

    private fun readUInt16BigEndian(
        data: ByteArray,
        offset: Int
    ): Int = ((data[offset].toInt() and 0xFF) shl 8) or (data[offset + 1].toInt() and 0xFF)

    private fun readInt16BigEndian(
        data: ByteArray,
        offset: Int
    ): Int = (data[offset].toInt() shl 8) or (data[offset + 1].toInt() and 0xFF)

    private fun readUInt32BigEndian(
        data: ByteArray,
        offset: Int
    ): Long =
        ((data[offset].toLong() and 0xFFL) shl 24) or
            ((data[offset + 1].toLong() and 0xFFL) shl 16) or
            ((data[offset + 2].toLong() and 0xFFL) shl 8) or
            (data[offset + 3].toLong() and 0xFFL)
}
