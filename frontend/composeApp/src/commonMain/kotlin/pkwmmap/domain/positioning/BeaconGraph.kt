package pkwmmap.domain.positioning

import pkwmmap.domain.model.BeaconNode
import pkwmmap.domain.model.MapEdge
import pkwmmap.domain.model.TraversalPath
import pkwmmap.domain.model.TraversalStep
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.hypot

/**
 * Graph utilities and Dijkstra shortest-path traversal between mapped beacons.
 */
object BeaconGraph {

    fun distanceBetween(a: BeaconNode, b: BeaconNode): Float =
        hypot((b.x - a.x).toDouble(), (b.y - a.y).toDouble()).toFloat()

    /**
     * Finds the shortest walkable path between [startNodeId] and [targetNodeId] using Dijkstra.
     * Returns null if either node does not exist or no path connects them.
     */
    fun findShortestPath(
        nodes: List<BeaconNode>,
        edges: List<MapEdge>,
        startNodeId: String,
        targetNodeId: String
    ): TraversalPath? {
        val nodeById = nodes.associateBy { it.id }
        if (startNodeId !in nodeById || targetNodeId !in nodeById) return null

        if (startNodeId == targetNodeId) {
            return TraversalPath(
                nodeIds = listOf(startNodeId),
                steps = emptyList(),
                totalDistanceMeters = 0f
            )
        }

        val adjacency = buildMap<String, MutableList<Pair<String, Float>>> {
            nodes.forEach { put(it.id, mutableListOf()) }
            edges.forEach { edge ->
                if (edge.fromNodeId in nodeById && edge.toNodeId in nodeById) {
                    get(edge.fromNodeId)?.add(edge.toNodeId to edge.distanceMeters)
                    get(edge.toNodeId)?.add(edge.fromNodeId to edge.distanceMeters)
                }
            }
        }

        val distances = mutableMapOf<String, Float>()
        val previous = mutableMapOf<String, String>()
        val unvisited = nodes.map { it.id }.toMutableSet()

        distances[startNodeId] = 0f

        while (unvisited.isNotEmpty()) {
            val currentId = unvisited.minByOrNull { distances[it] ?: Float.POSITIVE_INFINITY } ?: break
            val currentDist = distances[currentId] ?: Float.POSITIVE_INFINITY
            if (currentDist.isInfinite()) break

            unvisited.remove(currentId)
            if (currentId == targetNodeId) break

            for ((neighborId, weight) in adjacency[currentId].orEmpty()) {
                if (neighborId !in unvisited) continue
                val candidate = currentDist + weight
                val existing = distances[neighborId] ?: Float.POSITIVE_INFINITY
                if (candidate < existing) {
                    distances[neighborId] = candidate
                    previous[neighborId] = currentId
                }
            }
        }

        if (targetNodeId !in previous) return null

        val pathIds = mutableListOf<String>()
        var cursor: String? = targetNodeId
        while (cursor != null) {
            pathIds.add(cursor)
            cursor = previous[cursor]
        }
        pathIds.reverse()

        val steps = pathIds.zipWithNext { fromId, toId ->
            val fromNode = nodeById.getValue(fromId)
            val toNode = nodeById.getValue(toId)
            val dist = edges.firstOrNull { it.connects(fromId, toId) }?.distanceMeters
                ?: distanceBetween(fromNode, toNode)
            TraversalStep(
                fromNode = fromNode,
                toNode = toNode,
                distanceMeters = dist,
                compassDirection = compassDirection(fromNode, toNode)
            )
        }

        return TraversalPath(
            nodeIds = pathIds,
            steps = steps,
            totalDistanceMeters = distances.getValue(targetNodeId)
        )
    }

    /**
     * Computes a human-readable compass direction from [from] to [to] in screen/map coordinates
     * where +X is East and +Y is South.
     */
    fun compassDirection(from: BeaconNode, to: BeaconNode): String {
        val dx = (to.x - from.x).toDouble()
        val dy = (to.y - from.y).toDouble()
        // atan2(dy, dx): 0 = East, 90 = South, -90 = North, 180 = West
        val deg = atan2(dy, dx) * (180.0 / PI)
        return when {
            deg in -22.5..22.5 -> "East"
            deg in 22.5..67.5 -> "Southeast"
            deg in 67.5..112.5 -> "South"
            deg in 112.5..157.5 -> "Southwest"
            deg >= 157.5 || deg <= -157.5 -> "West"
            deg in -157.5..-112.5 -> "Northwest"
            deg in -112.5..-67.5 -> "North"
            else -> "Northeast"
        }
    }
}
