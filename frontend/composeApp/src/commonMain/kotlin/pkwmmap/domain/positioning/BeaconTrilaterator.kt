package pkwmmap.domain.positioning

import pkwmmap.domain.model.BeaconNode
import pkwmmap.domain.model.DetectedBeacon

data class BeaconMeasurement(
    val node: BeaconNode,
    val distanceMeters: Double
)

/**
 * Estimates user (x, y) position on the floorplan using inverse-distance-squared
 * weighted interpolation (weighted lerp) across detected placed beacons.
 */
object BeaconTrilaterator {
    fun estimatePosition(
        detectedBeacons: List<DetectedBeacon>,
        placedNodes: List<BeaconNode>,
        widthMeters: Float,
        heightMeters: Float,
        previousPosition: Pair<Float, Float>? = null,
        smoothingAlpha: Float = 0.35f
    ): Pair<Float, Float>? {
        val measurements = matchMeasurements(detectedBeacons, placedNodes)
        if (measurements.isEmpty()) return null

        val target = estimateRaw(measurements)
        val clampedX = target.first.coerceIn(0.5f, widthMeters - 0.5f)
        val clampedY = target.second.coerceIn(0.5f, heightMeters - 0.5f)

        return applySmoothing(clampedX, clampedY, previousPosition, smoothingAlpha)
    }

    /**
     * Estimates normalized user (x, y) coordinates in `0.0..1.0` per the PKWMmap API contract,
     * optionally filtering placed beacons to [floorId].
     */
    fun estimateNormalizedPosition(
        detectedBeacons: List<DetectedBeacon>,
        placedNodes: List<BeaconNode>,
        floorId: String? = null,
        previousPosition: Pair<Float, Float>? = null,
        smoothingAlpha: Float = 0.35f
    ): Pair<Float, Float>? {
        val floorNodes =
            if (floorId != null) {
                placedNodes.filter { it.floorId == null || it.floorId == floorId }
            } else {
                placedNodes
            }
        val measurements = matchMeasurements(detectedBeacons, floorNodes)
        if (measurements.isEmpty()) return null

        val target = estimateRaw(measurements)
        val clampedX = target.first.coerceIn(0.0f, 1.0f)
        val clampedY = target.second.coerceIn(0.0f, 1.0f)

        return applySmoothing(clampedX, clampedY, previousPosition, smoothingAlpha)
    }

    internal fun estimateRaw(measurements: List<BeaconMeasurement>): Pair<Float, Float> {
        if (measurements.size == 1) {
            val only = measurements[0].node
            return only.x to only.y
        }

        var sumW = 0.0
        var sumX = 0.0
        var sumY = 0.0
        for (m in measurements) {
            val d = m.distanceMeters.coerceAtLeast(0.25)
            val w = 1.0 / (d * d)
            sumW += w
            sumX += m.node.x * w
            sumY += m.node.y * w
        }
        return (sumX / sumW).toFloat() to (sumY / sumW).toFloat()
    }

    private fun matchMeasurements(
        detectedBeacons: List<DetectedBeacon>,
        placedNodes: List<BeaconNode>
    ): List<BeaconMeasurement> {
        val byBeaconId = placedNodes.associateBy { it.beaconId.lowercase() }
        val byId = placedNodes.associateBy { it.id.lowercase() }
        val byMac =
            placedNodes
                .mapNotNull { node -> node.macAddress?.lowercase()?.let { it to node } }
                .toMap()

        return detectedBeacons.mapNotNull { beacon ->
            val node =
                byBeaconId[beacon.beaconId.lowercase()]
                    ?: byId[beacon.beaconId.lowercase()]
                    ?: byMac[beacon.macAddress.lowercase()]
                    ?: return@mapNotNull null
            val dist = beacon.estimatedDistanceMeters.coerceIn(0.25, 35.0)
            BeaconMeasurement(node = node, distanceMeters = dist)
        }
    }

    private fun applySmoothing(
        x: Float,
        y: Float,
        previousPosition: Pair<Float, Float>?,
        smoothingAlpha: Float
    ): Pair<Float, Float> =
        if (previousPosition != null) {
            lerp(previousPosition.first, x, smoothingAlpha) to
                lerp(previousPosition.second, y, smoothingAlpha)
        } else {
            x to y
        }

    private fun lerp(
        start: Float,
        end: Float,
        fraction: Float
    ): Float = start + (end - start) * fraction
}
