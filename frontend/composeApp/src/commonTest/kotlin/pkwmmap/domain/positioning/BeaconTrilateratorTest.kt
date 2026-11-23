package pkwmmap.domain.positioning

import pkwmmap.domain.model.BeaconNode
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

class BeaconTrilateratorTest {

    @Test
    fun `weighted lerp pulls user toward closer beacons without overshooting`() {
        val b1 = BeaconNode("n1", "b1", "B1", 0f, 0f)
        val b2 = BeaconNode("n2", "b2", "B2", 10f, 0f)
        val b3 = BeaconNode("n3", "b3", "B3", 0f, 10f)

        val measurements = listOf(
            BeaconMeasurement(b1, 1.0), // weight 1.0
            BeaconMeasurement(b2, 5.0), // weight 0.04
            BeaconMeasurement(b3, 5.0)  // weight 0.04
        )

        val (x, y) = BeaconTrilaterator.estimateRaw(measurements)
        assertTrue(x in 0.0f..1.0f, "Expected x near B1 (0..1), got $x")
        assertTrue(y in 0.0f..1.0f, "Expected y near B1 (0..1), got $y")
    }

    @Test
    fun `weighted lerp is equidistant for equal distances`() {
        val b1 = BeaconNode("n1", "b1", "B1", 0f, 0f)
        val b2 = BeaconNode("n2", "b2", "B2", 10f, 0f)

        val measurements = listOf(
            BeaconMeasurement(b1, 2.0),
            BeaconMeasurement(b2, 2.0)
        )

        val (x, y) = BeaconTrilaterator.estimateRaw(measurements)
        assertTrue(abs(x - 5.0f) < 0.01f, "Expected x = 5.0, got $x")
        assertTrue(abs(y) < 0.01f, "Expected y = 0.0, got $y")
    }
}
