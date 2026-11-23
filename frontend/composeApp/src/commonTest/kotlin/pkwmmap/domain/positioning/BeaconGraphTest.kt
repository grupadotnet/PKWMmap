package pkwmmap.domain.positioning

import pkwmmap.domain.model.BeaconNode
import pkwmmap.domain.model.MapEdge
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class BeaconGraphTest {
    @Test
    fun `finds shortest path via Dijkstra instead of longer detour`() {
        val a = BeaconNode("A", "b1", "Lobby", 0f, 0f)
        val b = BeaconNode("B", "b2", "Hallway", 5f, 0f)
        val c = BeaconNode("C", "b3", "Lab", 10f, 0f)
        val d = BeaconNode("D", "b4", "Detour", 5f, 8f)

        val edges =
            listOf(
                MapEdge("A", "B", 5f),
                MapEdge("B", "C", 5f),
                MapEdge("A", "D", 9.4f),
                MapEdge("D", "C", 9.4f)
            )

        val path = BeaconGraph.findShortestPath(listOf(a, b, c, d), edges, "A", "C")
        assertNotNull(path)
        assertEquals(listOf("A", "B", "C"), path.nodeIds)
        assertEquals(10f, path.totalDistanceMeters)
        assertEquals(2, path.steps.size)
        assertEquals("East", path.steps.first().compassDirection)
    }

    @Test
    fun `returns null when nodes are disconnected`() {
        val a = BeaconNode("A", "b1", "Lobby", 0f, 0f)
        val b = BeaconNode("B", "b2", "Isolated", 10f, 10f)

        val path = BeaconGraph.findShortestPath(listOf(a, b), emptyList(), "A", "B")
        assertNull(path)
    }
}
