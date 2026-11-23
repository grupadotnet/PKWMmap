package pkwmmap.domain.model

/**
 * A physical location on the indoor floorplan bound to a BLE beacon.
 * Coordinates [x] and [y] are either in meters or normalized (0.0..1.0) per the PKWMmap API contract.
 */
data class BeaconNode(
    val id: String,
    val beaconId: String,
    val label: String,
    val x: Float,
    val y: Float,
    val frameType: String = "UID",
    val macAddress: String? = null,
    val buildingId: String? = null,
    val floorId: String? = null,
    val txPowerAt1m: Int = -59
)

/**
 * Bidirectional walkable connection between two mapped beacon locations.
 */
data class MapEdge(
    val fromNodeId: String,
    val toNodeId: String,
    val distanceMeters: Float
) {
    fun connects(
        a: String,
        b: String
    ): Boolean = (fromNodeId == a && toNodeId == b) || (fromNodeId == b && toNodeId == a)
}

/**
 * Complete floorplan graph state.
 */
data class FloorPlanState(
    val widthMeters: Float = 20f,
    val heightMeters: Float = 20f,
    val nodes: List<BeaconNode> = emptyList(),
    val edges: List<MapEdge> = emptyList()
)

data class TraversalStep(
    val fromNode: BeaconNode,
    val toNode: BeaconNode,
    val distanceMeters: Float,
    val compassDirection: String
)

data class TraversalPath(
    val nodeIds: List<String>,
    val steps: List<TraversalStep>,
    val totalDistanceMeters: Float
)
