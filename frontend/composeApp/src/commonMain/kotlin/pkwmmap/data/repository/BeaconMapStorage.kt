package pkwmmap.data.repository

import pkwmmap.domain.model.BeaconNode
import pkwmmap.domain.model.FloorPlanState

/**
 * In-memory KMP cache for floorplan beacon placements and cached `GET /beacons` responses.
 */
class BeaconMapStorage {
    private var cachedFloorPlan: FloorPlanState? = null
    private val cachedBeaconsById = LinkedHashMap<String, BeaconNode>()

    fun saveFloorPlan(plan: FloorPlanState) {
        cachedFloorPlan = plan
        cacheBeacons(plan.nodes)
    }

    fun loadFloorPlan(): FloorPlanState? = cachedFloorPlan

    fun cacheBeacons(beacons: List<BeaconNode>) {
        beacons.forEach { beacon ->
            cachedBeaconsById[beacon.beaconId.lowercase()] = beacon
            cachedBeaconsById[beacon.id.lowercase()] = beacon
        }
    }

    fun getBeaconByIdOrMac(idOrMac: String): BeaconNode? {
        val key = idOrMac.lowercase()
        return cachedBeaconsById[key]
            ?: cachedBeaconsById.values.firstOrNull { it.macAddress?.lowercase() == key }
    }

    fun getBeaconsForFloor(floorId: String): List<BeaconNode> =
        cachedBeaconsById.values
            .distinctBy { it.id }
            .filter { it.floorId == floorId }

    fun clear() {
        cachedFloorPlan = null
        cachedBeaconsById.clear()
    }
}
