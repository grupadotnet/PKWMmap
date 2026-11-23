package pkwmmap.domain.positioning

import kotlin.math.log10
import kotlin.math.pow

/**
 * Estimates physical distance from BLE RSSI and calibrated Tx power.
 *
 * Eddystone specifies Tx Power at 0m, which is roughly 41 dBm higher than the 1m reference RSSI.
 * The PKWMmap backend API (`tx_power`) provides calibrated RSSI at 1m (e.g. -59 dBm).
 */
object RssiDistanceEstimator {
    private const val ONE_METER_PATH_LOSS_DB = 41.0
    private const val INDOOR_PATH_LOSS_EXPONENT = 2.4

    fun txPowerAt1mTo0m(txPowerAt1m: Int): Int = txPowerAt1m + ONE_METER_PATH_LOSS_DB.toInt()

    fun estimateDistanceFromTxAt1m(
        rssi: Int,
        txPowerAt1m: Int = -59
    ): Double {
        if (rssi == 0) return -1.0
        val exponent = (txPowerAt1m - rssi) / (10.0 * INDOOR_PATH_LOSS_EXPONENT)
        return 10.0.pow(exponent).coerceIn(0.2, 50.0)
    }

    fun estimateDistanceMeters(
        rssi: Int,
        txPowerAt0m: Int
    ): Double {
        if (rssi == 0) return -1.0
        val txPowerAt1m = txPowerAt0m - ONE_METER_PATH_LOSS_DB
        val exponent = (txPowerAt1m - rssi) / (10.0 * INDOOR_PATH_LOSS_EXPONENT)
        return 10.0.pow(exponent).coerceIn(0.2, 50.0)
    }

    fun rssiForDistanceMeters(
        distanceMeters: Double,
        txPowerAt0m: Int
    ): Int {
        val clamped = distanceMeters.coerceAtLeast(0.2)
        val txPowerAt1m = txPowerAt0m - ONE_METER_PATH_LOSS_DB
        val rssi = txPowerAt1m - 10.0 * INDOOR_PATH_LOSS_EXPONENT * log10(clamped)
        return rssi.toInt().coerceIn(-100, -25)
    }
}
