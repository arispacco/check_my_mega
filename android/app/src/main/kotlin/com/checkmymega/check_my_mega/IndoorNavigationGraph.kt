package com.checkmymega.check_my_mega

import org.json.JSONArray
import org.json.JSONObject
import java.util.PriorityQueue
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

data class GraphNode(
    val id: Int,
    val x: Double,
    val y: Double,
    val spotId: String? = null,
    val createdAtMs: Long = 0L
)

data class GraphEdge(
    val id: Int,
    val a: Int,
    val b: Int,
    val lengthM: Double,
    var penaltyMultiplier: Double = 1.0
) {
    val isBlocked: Boolean get() = penaltyMultiplier > 1.5
    val effectiveCost: Double get() = lengthM * penaltyMultiplier
}

data class RouteStep(
    val nodeId: Int,
    val x: Double,
    val y: Double
)

data class GraphRouteResult(
    val waypoints: List<RouteStep>,
    val nextWaypointX: Double,
    val nextWaypointY: Double,
    val activeEdgeFromId: Int,
    val activeEdgeToId: Int,
    val totalRouteDistanceM: Double,
    val nextTurnDistanceM: Double,
    val straightDistanceM: Double,
    val isDirectFallback: Boolean,
    val justRerouted: Boolean,
    val hasBlockedSegments: Boolean
)

data class GraphRenderSnapshot(
    val nodes: List<GraphNode>,
    val edges: List<GraphEdge>
)

/**
 * Topological indoor corridor graph built automatically from dead-reckoning motion steps and
 * saved spots. Computes shortest walkable routes with A* and detects obstacle detours for live
 * Google-Maps-style re-routing.
 */
class IndoorNavigationGraph {

    private val lock = Any()
    private val nodes = linkedMapOf<Int, GraphNode>()
    private val edges = linkedMapOf<Long, GraphEdge>()
    private val adjacency = hashMapOf<Int, MutableSet<Int>>()
    private val spotToNodeId = hashMapOf<String, Int>()

    private var nextNodeId = 0
    private var nextEdgeId = 0
    private var currentNodeId: Int? = null
    private var prevNodeId: Int? = null

    // Live route-tracking state used to detect off-route obstacle detours
    private var trackedTargetSpotId: String? = null
    private var trackedEdgeKey: Long = -1L
    private var trackedEdgeFromId: Int = -1
    private var trackedEdgeToId: Int = -1
    private var minDistToTrackedEndM: Double = Double.POSITIVE_INFINITY
    private var lastAutoPenalizeMs: Long = 0L
    private var lastPenalizedEdgeKey: Long = -1L
    private var lastRerouteBannerUntilMs: Long = 0L

    fun nodeCount(): Int = synchronized(lock) { nodes.size }

    fun hasBlockedEdges(): Boolean = synchronized(lock) {
        edges.values.any { it.isBlocked }
    }

    fun clear(seedOrigin: Boolean = true) {
        synchronized(lock) {
            nodes.clear()
            edges.clear()
            adjacency.clear()
            spotToNodeId.clear()
            nextNodeId = 0
            nextEdgeId = 0
            currentNodeId = null
            prevNodeId = null
            resetRouteTrackingLocked()
            lastRerouteBannerUntilMs = 0L
            if (seedOrigin) {
                createNodeLocked(0.0, 0.0, null, System.currentTimeMillis())
                currentNodeId = 0
            }
        }
    }

    fun resetRoutePenalties() {
        synchronized(lock) {
            for (e in edges.values) {
                e.penaltyMultiplier = 1.0
            }
            resetRouteTrackingLocked()
            lastPenalizedEdgeKey = -1L
            lastRerouteBannerUntilMs = 0L
        }
    }

    /**
     * Records a new position along the user's walked trajectory. Automatically closes corridor loops
     * when revisiting existing intersections and links any newly captured [spotId].
     */
    fun recordMotionPoint(
        x: Double,
        y: Double,
        spotId: String? = null,
        nowMs: Long = System.currentTimeMillis()
    ): Int {
        synchronized(lock) {
            if (nodes.isEmpty()) {
                val rootId = createNodeLocked(x, y, spotId, nowMs)
                currentNodeId = rootId
                return rootId
            }

            val lastId = currentNodeId ?: findNearestNodeLocked(x, y)?.id ?: 0
            val lastNode = nodes[lastId] ?: nodes.values.first()
            val distFromLast = hypot(x - lastNode.x, y - lastNode.y)

            // Guard against spurious large GPS leaps warping through walls unless explicitly anchoring a spot
            if (spotId == null && distFromLast > MAX_SINGLE_STEP_JUMP_M) {
                val nearExisting = findNearestNodeLocked(x, y)
                if (nearExisting != null && hypot(x - nearExisting.x, y - nearExisting.y) <= LOOP_CLOSURE_RADIUS_M) {
                    prevNodeId = null
                    currentNodeId = nearExisting.id
                    return nearExisting.id
                }
                // Re-anchor softly near current point without adding a phantom corridor across walls
                return lastId
            }

            // Spot capture: always bind or create an exact navigable node anchored at this spot
            if (spotId != null) {
                val existingAtSpot = nodes.values.minByOrNull { hypot(x - it.x, y - it.y) }
                val boundId = if (existingAtSpot != null &&
                    hypot(x - existingAtSpot.x, y - existingAtSpot.y) <= SPOT_REUSE_RADIUS_M &&
                    (existingAtSpot.spotId == null || existingAtSpot.spotId == spotId)
                ) {
                    nodes[existingAtSpot.id] = existingAtSpot.copy(spotId = spotId)
                    spotToNodeId[spotId] = existingAtSpot.id
                    if (existingAtSpot.id != lastId && distFromLast <= MAX_EDGE_CONNECT_M) {
                        addOrClearEdgeLocked(lastId, existingAtSpot.id)
                    }
                    existingAtSpot.id
                } else {
                    val created = createNodeLocked(x, y, spotId, nowMs)
                    if (distFromLast <= MAX_EDGE_CONNECT_M) {
                        addOrClearEdgeLocked(lastId, created)
                    }
                    connectNearbyJunctionsLocked(created, x, y, excludeId = lastId)
                    created
                }
                prevNodeId = if (boundId != lastId) lastId else prevNodeId
                currentNodeId = boundId
                return boundId
            }

            // Walking motion: first check if the user deviated past the active suggested segment
            evaluateDeviationLocked(x, y, nowMs)

            // Check loop closure / revisiting an existing hallway intersection
            val loopCandidate = findLoopClosureCandidateLocked(x, y, lastId)
            if (loopCandidate != null) {
                addOrClearEdgeLocked(lastId, loopCandidate.id)
                prevNodeId = lastId
                currentNodeId = loopCandidate.id
                return loopCandidate.id
            }

            // Check whether to emit a new breadcrumb node (distance or sharp corner turn)
            val prevNode = prevNodeId?.let { nodes[it] }
            val turnDeg = if (prevNode != null && prevNode.id != lastId && distFromLast >= MIN_CORNER_DIST_M) {
                angleBetweenVectorsDeg(
                    lastNode.x - prevNode.x, lastNode.y - prevNode.y,
                    x - lastNode.x, y - lastNode.y
                )
            } else {
                0.0
            }

            if (distFromLast >= NODE_STEP_SPACING_M || (distFromLast >= MIN_CORNER_DIST_M && turnDeg >= CORNER_TURN_DEG)) {
                val newId = createNodeLocked(x, y, null, nowMs)
                addOrClearEdgeLocked(lastId, newId)
                connectNearbyJunctionsLocked(newId, x, y, excludeId = lastId)
                prevNodeId = lastId
                currentNodeId = newId
                pruneExcessNodesLocked()
                return newId
            }

            return lastId
        }
    }

    /** Snaps navigation tracking onto the saved [spotId] when the user taps recalibrate. */
    fun snapToSpot(spotId: String, x: Double, y: Double, nowMs: Long = System.currentTimeMillis()) {
        synchronized(lock) {
            val existing = spotToNodeId[spotId]?.let { nodes[it] }
                ?: nodes.values.firstOrNull { it.spotId == spotId }
                ?: findNearestNodeLocked(x, y, maxDist = 1.6)
            val targetId = existing?.id ?: createNodeLocked(x, y, spotId, nowMs)
            nodes[targetId]?.let { n ->
                if (n.spotId == null) nodes[targetId] = n.copy(spotId = spotId)
            }
            spotToNodeId[spotId] = targetId
            currentNodeId = targetId
            prevNodeId = null
            resetRouteTrackingLocked()
        }
    }

    /** Manually flags the currently active suggested corridor segment as blocked by an obstacle. */
    fun markCurrentRouteBlocked(nowMs: Long = System.currentTimeMillis()): Boolean {
        synchronized(lock) {
            val edge = edges[trackedEdgeKey] ?: return false
            edge.penaltyMultiplier = max(edge.penaltyMultiplier, OBSTACLE_PENALTY_FACTOR)
            lastPenalizedEdgeKey = trackedEdgeKey
            lastAutoPenalizeMs = nowMs
            lastRerouteBannerUntilMs = nowMs + REROUTE_BANNER_DURATION_MS
            resetRouteTrackingLocked()
            return true
        }
    }

    /** Flags a specific edge between [nodeA] and [nodeB] with an obstacle penalty. */
    fun penalizeEdge(nodeA: Int, nodeB: Int, factor: Double = OBSTACLE_PENALTY_FACTOR, nowMs: Long = System.currentTimeMillis()): Boolean {
        synchronized(lock) {
            val key = edgeKey(nodeA, nodeB)
            val edge = edges[key] ?: return false
            edge.penaltyMultiplier = max(edge.penaltyMultiplier, factor)
            lastPenalizedEdgeKey = key
            lastAutoPenalizeMs = nowMs
            lastRerouteBannerUntilMs = nowMs + REROUTE_BANNER_DURATION_MS
            if (trackedEdgeKey == key) {
                resetRouteTrackingLocked()
            }
            return true
        }
    }

    /** Computes the optimal walkable route to [targetSpot] from ([curX], [curY]). */
    fun computeRoute(
        curX: Double,
        curY: Double,
        targetSpot: HouseRadarSpot,
        nowMs: Long = System.currentTimeMillis()
    ): GraphRouteResult {
        synchronized(lock) {
            val straightDist = hypot(targetSpot.posX - curX, targetSpot.posY - curY)
            val isRerouteActive = nowMs <= lastRerouteBannerUntilMs
            val anyBlocked = edges.values.any { it.isBlocked }

            val goalNode = spotToNodeId[targetSpot.id]?.let { nodes[it] }
                ?: nodes.values.firstOrNull { it.spotId == targetSpot.id }
                ?: findNearestNodeLocked(targetSpot.posX, targetSpot.posY, maxDist = 2.4)

            val startNode = selectStartNodeLocked(curX, curY)

            if (startNode == null || goalNode == null || startNode.id == goalNode.id) {
                resetRouteTrackingLocked(targetSpot.id)
                val step = RouteStep(goalNode?.id ?: -1, targetSpot.posX, targetSpot.posY)
                return GraphRouteResult(
                    waypoints = listOf(step),
                    nextWaypointX = targetSpot.posX,
                    nextWaypointY = targetSpot.posY,
                    activeEdgeFromId = -1,
                    activeEdgeToId = -1,
                    totalRouteDistanceM = straightDist,
                    nextTurnDistanceM = straightDist,
                    straightDistanceM = straightDist,
                    isDirectFallback = (startNode == null || goalNode == null),
                    justRerouted = isRerouteActive,
                    hasBlockedSegments = anyBlocked
                )
            }

            // First try an obstacle-free A* search; only if none exists allow penalized edges cleanly
            val clearPath = findAStarPathLocked(startNode.id, goalNode.id, allowPenalized = false)
            val graphPath = clearPath ?: findAStarPathLocked(startNode.id, goalNode.id, allowPenalized = true)

            if (graphPath == null || graphPath.size < 2) {
                resetRouteTrackingLocked(targetSpot.id)
                val step = RouteStep(goalNode.id, targetSpot.posX, targetSpot.posY)
                return GraphRouteResult(
                    waypoints = listOf(step),
                    nextWaypointX = targetSpot.posX,
                    nextWaypointY = targetSpot.posY,
                    activeEdgeFromId = -1,
                    activeEdgeToId = -1,
                    totalRouteDistanceM = straightDist,
                    nextTurnDistanceM = straightDist,
                    straightDistanceM = straightDist,
                    isDirectFallback = true,
                    justRerouted = isRerouteActive,
                    hasBlockedSegments = anyBlocked
                )
            }

            val usesPenalized = clearPath == null
            if (usesPenalized && anyBlocked) {
                // Every known graph path crosses a blocked doorway and the user hasn't closed a loop
                // yet: guide them smoothly around the obstacle instead of sending them through it.
                resetRouteTrackingLocked(targetSpot.id)
                val step = RouteStep(goalNode.id, targetSpot.posX, targetSpot.posY)
                return GraphRouteResult(
                    waypoints = listOf(step),
                    nextWaypointX = targetSpot.posX,
                    nextWaypointY = targetSpot.posY,
                    activeEdgeFromId = -1,
                    activeEdgeToId = -1,
                    totalRouteDistanceM = straightDist,
                    nextTurnDistanceM = straightDist,
                    straightDistanceM = straightDist,
                    isDirectFallback = true,
                    justRerouted = isRerouteActive,
                    hasBlockedSegments = true
                )
            }

            // Skip startNode if the user already passed along the first corridor segment toward node[1]
            val trimmedIds = trimPassedLeadingNodesLocked(curX, curY, graphPath)
            val routeSteps = mutableListOf<RouteStep>()
            for (nid in trimmedIds) {
                nodes[nid]?.let { n -> routeSteps.add(RouteStep(n.id, n.x, n.y)) }
            }
            if (routeSteps.isNotEmpty()) {
                val tail = routeSteps.last()
                if (hypot(targetSpot.posX - tail.x, targetSpot.posY - tail.y) > 0.25) {
                    routeSteps.add(RouteStep(-1, targetSpot.posX, targetSpot.posY))
                }
            }

            val nextTarget = selectCollinearLookahead(curX, curY, routeSteps)
            val totalDist = computePolylineDistance(curX, curY, routeSteps)
            val turnDist = computeDistanceToNextTurn(curX, curY, routeSteps)

            val legFromId = if (trimmedIds.size >= 2) trimmedIds[0] else startNode.id
            val legToId = if (trimmedIds.size >= 2) trimmedIds[1] else trimmedIds.first()
            updateTrackedLegLocked(targetSpot.id, curX, curY, legFromId, legToId)

            return GraphRouteResult(
                waypoints = routeSteps,
                nextWaypointX = nextTarget.x,
                nextWaypointY = nextTarget.y,
                activeEdgeFromId = legFromId,
                activeEdgeToId = legToId,
                totalRouteDistanceM = totalDist,
                nextTurnDistanceM = turnDist,
                straightDistanceM = straightDist,
                isDirectFallback = false,
                justRerouted = isRerouteActive,
                hasBlockedSegments = anyBlocked
            )
        }
    }

    fun renderSnapshot(): GraphRenderSnapshot = synchronized(lock) {
        GraphRenderSnapshot(
            nodes = nodes.values.toList(),
            edges = edges.values.toList()
        )
    }

    fun toJsonString(): String = synchronized(lock) {
        val root = JSONObject()
        root.put("nextNodeId", nextNodeId)
        root.put("nextEdgeId", nextEdgeId)
        root.put("currentNodeId", currentNodeId ?: -1)

        val nArr = JSONArray()
        for (n in nodes.values) {
            nArr.put(JSONObject().apply {
                put("id", n.id)
                put("x", n.x)
                put("y", n.y)
                if (n.spotId != null) put("spotId", n.spotId)
                put("t", n.createdAtMs)
            })
        }
        root.put("nodes", nArr)

        val eArr = JSONArray()
        for (e in edges.values) {
            eArr.put(JSONObject().apply {
                put("id", e.id)
                put("a", e.a)
                put("b", e.b)
                put("l", e.lengthM)
                put("p", e.penaltyMultiplier)
            })
        }
        root.put("edges", eArr)
        root.toString()
    }

    fun loadFromJsonString(raw: String?) {
        synchronized(lock) {
            nodes.clear()
            edges.clear()
            adjacency.clear()
            spotToNodeId.clear()
            resetRouteTrackingLocked()
            if (raw.isNullOrBlank()) {
                clear(seedOrigin = true)
                return
            }
            try {
                val root = JSONObject(raw)
                nextNodeId = root.optInt("nextNodeId", 0)
                nextEdgeId = root.optInt("nextEdgeId", 0)
                val savedCur = root.optInt("currentNodeId", -1)

                val nArr = root.optJSONArray("nodes")
                if (nArr != null) {
                    for (i in 0 until nArr.length()) {
                        val o = nArr.getJSONObject(i)
                        val id = o.getInt("id")
                        val sid = o.optString("spotId", "").takeIf { it.isNotEmpty() }
                        val node = GraphNode(
                            id = id,
                            x = o.getDouble("x"),
                            y = o.getDouble("y"),
                            spotId = sid,
                            createdAtMs = o.optLong("t", 0L)
                        )
                        nodes[id] = node
                        adjacency.getOrPut(id) { linkedSetOf() }
                        if (sid != null) spotToNodeId[sid] = id
                        if (id >= nextNodeId) nextNodeId = id + 1
                    }
                }

                val eArr = root.optJSONArray("edges")
                if (eArr != null) {
                    for (i in 0 until eArr.length()) {
                        val o = eArr.getJSONObject(i)
                        val a = o.getInt("a")
                        val b = o.getInt("b")
                        if (!nodes.containsKey(a) || !nodes.containsKey(b) || a == b) continue
                        val edge = GraphEdge(
                            id = o.optInt("id", nextEdgeId++),
                            a = min(a, b),
                            b = max(a, b),
                            lengthM = o.getDouble("l"),
                            penaltyMultiplier = o.optDouble("p", 1.0).coerceAtLeast(1.0)
                        )
                        edges[edgeKey(edge.a, edge.b)] = edge
                        adjacency.getOrPut(edge.a) { linkedSetOf() }.add(edge.b)
                        adjacency.getOrPut(edge.b) { linkedSetOf() }.add(edge.a)
                        if (edge.id >= nextEdgeId) nextEdgeId = edge.id + 1
                    }
                }
                currentNodeId = if (savedCur in nodes) savedCur else nodes.keys.lastOrNull()
                if (nodes.isEmpty()) clear(seedOrigin = true)
            } catch (_: Exception) {
                clear(seedOrigin = true)
            }
        }
    }

    // -------------------------------------------------------------------------
    // Internal Topology & A* Helpers (must be called under [lock])
    // -------------------------------------------------------------------------

    private fun createNodeLocked(x: Double, y: Double, spotId: String?, createdAtMs: Long): Int {
        val id = nextNodeId++
        nodes[id] = GraphNode(id = id, x = x, y = y, spotId = spotId, createdAtMs = createdAtMs)
        adjacency.getOrPut(id) { linkedSetOf() }
        if (spotId != null) {
            spotToNodeId[spotId] = id
        }
        return id
    }

    private fun addOrClearEdgeLocked(u: Int, v: Int) {
        if (u == v || !nodes.containsKey(u) || !nodes.containsKey(v)) return
        val a = min(u, v)
        val b = max(u, v)
        val key = edgeKey(a, b)
        val existing = edges[key]
        if (existing != null) {
            existing.penaltyMultiplier = 1.0
            return
        }
        val na = nodes[a]!!
        val nb = nodes[b]!!
        val dist = hypot(nb.x - na.x, nb.y - na.y).coerceAtLeast(0.05)
        edges[key] = GraphEdge(id = nextEdgeId++, a = a, b = b, lengthM = dist, penaltyMultiplier = 1.0)
        adjacency.getOrPut(a) { linkedSetOf() }.add(b)
        adjacency.getOrPut(b) { linkedSetOf() }.add(a)
    }

    private fun connectNearbyJunctionsLocked(nodeId: Int, x: Double, y: Double, excludeId: Int) {
        for (other in nodes.values) {
            if (other.id == nodeId || other.id == excludeId) continue
            val d = hypot(other.x - x, other.y - y)
            if (d <= LOOP_CLOSURE_RADIUS_M && !areWithinHopsLocked(nodeId, other.id, maxHops = 2)) {
                addOrClearEdgeLocked(nodeId, other.id)
            }
        }
    }

    private fun findLoopClosureCandidateLocked(x: Double, y: Double, lastId: Int): GraphNode? {
        var best: GraphNode? = null
        var bestDist = LOOP_CLOSURE_RADIUS_M
        for (candidate in nodes.values) {
            if (candidate.id == lastId || candidate.id == prevNodeId) continue
            val d = hypot(x - candidate.x, y - candidate.y)
            if (d <= bestDist && !areWithinHopsLocked(lastId, candidate.id, maxHops = 2)) {
                bestDist = d
                best = candidate
            }
        }
        return best
    }

    private fun areWithinHopsLocked(startId: Int, targetId: Int, maxHops: Int): Boolean {
        if (startId == targetId) return true
        val visited = hashSetOf(startId)
        val queue = ArrayDeque<Pair<Int, Int>>()
        queue.add(startId to 0)
        while (queue.isNotEmpty()) {
            val (cur, depth) = queue.removeFirst()
            if (depth >= maxHops) continue
            for (nbr in adjacency[cur].orEmpty()) {
                if (nbr == targetId) return true
                if (visited.add(nbr)) {
                    queue.add(nbr to (depth + 1))
                }
            }
        }
        return false
    }

    private fun findNearestNodeLocked(x: Double, y: Double, maxDist: Double = Double.POSITIVE_INFINITY): GraphNode? {
        var best: GraphNode? = null
        var bestD = maxDist
        for (n in nodes.values) {
            val d = hypot(x - n.x, y - n.y)
            if (d < bestD) {
                bestD = d
                best = n
            }
        }
        return best
    }

    private fun selectStartNodeLocked(curX: Double, curY: Double): GraphNode? {
        val cur = currentNodeId?.let { nodes[it] }
        if (cur != null && hypot(curX - cur.x, curY - cur.y) <= 2.0) {
            return cur
        }
        return findNearestNodeLocked(curX, curY)
    }

    private fun evaluateDeviationLocked(curX: Double, curY: Double, nowMs: Long) {
        if (trackedTargetSpotId == null || trackedEdgeKey < 0L) return
        val u = nodes[trackedEdgeFromId] ?: return
        val v = nodes[trackedEdgeToId] ?: return
        val edge = edges[trackedEdgeKey] ?: return

        val distToEnd = hypot(v.x - curX, v.y - curY)
        if (distToEnd < minDistToTrackedEndM) {
            minDistToTrackedEndM = distToEnd
        }

        val edgeLen = hypot(v.x - u.x, v.y - u.y)
        if (edgeLen < 0.8) return

        val perpDist = pointToSegmentDistance(curX, curY, u.x, u.y, v.x, v.y)
        val awayFromTargetProgress = distToEnd - minDistToTrackedEndM
        val offCorridor = perpDist > DETOUR_LATERAL_THRESHOLD_M && min(hypot(curX - u.x, curY - u.y), distToEnd) > 1.8
        val retreatedBackward = awayFromTargetProgress > DETOUR_BACKTRACK_THRESHOLD_M

        val cooldownElapsed = (nowMs - lastAutoPenalizeMs) > 2500L || trackedEdgeKey != lastPenalizedEdgeKey
        if ((offCorridor || retreatedBackward) && cooldownElapsed) {
            edge.penaltyMultiplier = max(edge.penaltyMultiplier, OBSTACLE_PENALTY_FACTOR)
            lastAutoPenalizeMs = nowMs
            lastPenalizedEdgeKey = trackedEdgeKey
            lastRerouteBannerUntilMs = nowMs + REROUTE_BANNER_DURATION_MS
            resetRouteTrackingLocked(trackedTargetSpotId)
        }
    }

    private fun updateTrackedLegLocked(spotId: String, curX: Double, curY: Double, fromId: Int, toId: Int) {
        val key = edgeKey(fromId, toId)
        if (key !in edges) {
            resetRouteTrackingLocked(spotId)
            return
        }
        if (trackedTargetSpotId != spotId || trackedEdgeKey != key) {
            trackedTargetSpotId = spotId
            trackedEdgeKey = key
            trackedEdgeFromId = fromId
            trackedEdgeToId = toId
            val v = nodes[toId]
            minDistToTrackedEndM = if (v != null) hypot(v.x - curX, v.y - curY) else Double.POSITIVE_INFINITY
        }
    }

    private fun resetRouteTrackingLocked(spotId: String? = null) {
        trackedTargetSpotId = spotId
        trackedEdgeKey = -1L
        trackedEdgeFromId = -1
        trackedEdgeToId = -1
        minDistToTrackedEndM = Double.POSITIVE_INFINITY
    }

    private fun findAStarPathLocked(startId: Int, goalId: Int, allowPenalized: Boolean): List<Int>? {
        val goal = nodes[goalId] ?: return null
        val gScore = hashMapOf<Int, Double>(startId to 0.0)
        val cameFrom = hashMapOf<Int, Int>()
        val closed = hashSetOf<Int>()
        val open = PriorityQueue<Pair<Int, Double>>(compareBy { it.second })

        val startNode = nodes[startId] ?: return null
        open.add(startId to hypot(goal.x - startNode.x, goal.y - startNode.y))

        while (open.isNotEmpty()) {
            val (curId, _) = open.poll() ?: break
            if (!closed.add(curId)) continue
            if (curId == goalId) {
                val path = mutableListOf<Int>()
                var step: Int? = goalId
                while (step != null) {
                    path.add(step)
                    step = cameFrom[step]
                }
                path.reverse()
                return path
            }

            val curG = gScore[curId] ?: continue
            for (nbrId in adjacency[curId].orEmpty()) {
                if (nbrId in closed) continue
                val edge = edges[edgeKey(curId, nbrId)] ?: continue
                if (!allowPenalized && edge.isBlocked) continue

                val tentativeG = curG + edge.effectiveCost
                val prevG = gScore[nbrId]
                if (prevG == null || tentativeG < prevG) {
                    gScore[nbrId] = tentativeG
                    cameFrom[nbrId] = curId
                    val nbrNode = nodes[nbrId] ?: continue
                    val h = hypot(goal.x - nbrNode.x, goal.y - nbrNode.y)
                    open.add(nbrId to (tentativeG + h))
                }
            }
        }
        return null
    }

    private fun trimPassedLeadingNodesLocked(curX: Double, curY: Double, pathIds: List<Int>): List<Int> {
        var idx = 0
        while (idx + 1 < pathIds.size) {
            val a = nodes[pathIds[idx]] ?: break
            val b = nodes[pathIds[idx + 1]] ?: break
            val dA = hypot(a.x - curX, a.y - curY)
            val abX = b.x - a.x
            val abY = b.y - a.y
            val abLen2 = abX * abX + abY * abY
            val tProj = if (abLen2 > 1e-6) ((curX - a.x) * abX + (curY - a.y) * abY) / abLen2 else 0.0
            // Skip node a if the user is standing on it or already moved partway down [a -> b]
            if (dA <= WAYPOINT_PASS_RADIUS_M || tProj > 0.35) {
                idx++
            } else {
                break
            }
        }
        return pathIds.subList(idx, pathIds.size)
    }

    private fun pruneExcessNodesLocked() {
        while (nodes.size > MAX_GRAPH_NODES) {
            val victim = nodes.values.firstOrNull { n ->
                n.spotId == null && n.id != currentNodeId && (adjacency[n.id]?.size ?: 0) <= 1
            } ?: break
            val neighbors = adjacency.remove(victim.id).orEmpty().toList()
            for (nbr in neighbors) {
                edges.remove(edgeKey(victim.id, nbr))
                adjacency[nbr]?.remove(victim.id)
            }
            nodes.remove(victim.id)
        }
    }

    private fun selectCollinearLookahead(curX: Double, curY: Double, steps: List<RouteStep>): RouteStep {
        if (steps.isEmpty()) return RouteStep(-1, curX, curY)
        var chosenIdx = 0
        for (candIdx in 1 until steps.size) {
            val end = steps[candIdx]
            var staysInCorridor = true
            for (midIdx in 0 until candIdx) {
                val mid = steps[midIdx]
                if (pointToSegmentDistance(mid.x, mid.y, curX, curY, end.x, end.y) > CORRIDOR_LOOKAHEAD_TOLERANCE_M) {
                    staysInCorridor = false
                    break
                }
            }
            if (staysInCorridor) {
                chosenIdx = candIdx
            } else {
                break
            }
        }
        return steps[chosenIdx]
    }

    private fun computePolylineDistance(curX: Double, curY: Double, steps: List<RouteStep>): Double {
        if (steps.isEmpty()) return 0.0
        var total = hypot(steps[0].x - curX, steps[0].y - curY)
        for (i in 0 until steps.size - 1) {
            total += hypot(steps[i + 1].x - steps[i].x, steps[i + 1].y - steps[i].y)
        }
        return total
    }

    private fun computeDistanceToNextTurn(curX: Double, curY: Double, steps: List<RouteStep>): Double {
        if (steps.isEmpty()) return 0.0
        if (steps.size == 1) return hypot(steps[0].x - curX, steps[0].y - curY)

        var dist = hypot(steps[0].x - curX, steps[0].y - curY)
        val initVx = steps[0].x - curX
        val initVy = steps[0].y - curY
        var prevX = steps[0].x
        var prevY = steps[0].y

        for (i in 1 until steps.size) {
            val segVx = steps[i].x - prevX
            val segVy = steps[i].y - prevY
            val segLen = hypot(segVx, segVy)
            val turn = angleBetweenVectorsDeg(initVx, initVy, segVx, segVy)
            if (turn >= CORNER_TURN_DEG && dist > 0.6) {
                return dist
            }
            dist += segLen
            prevX = steps[i].x
            prevY = steps[i].y
        }
        return dist
    }

    private fun angleBetweenVectorsDeg(ax: Double, ay: Double, bx: Double, by: Double): Double {
        val lenA = hypot(ax, ay)
        val lenB = hypot(bx, by)
        if (lenA < 1e-4 || lenB < 1e-4) return 0.0
        val dot = ((ax * bx + ay * by) / (lenA * lenB)).coerceIn(-1.0, 1.0)
        return Math.toDegrees(acos(dot))
    }

    private fun pointToSegmentDistance(px: Double, py: Double, ax: Double, ay: Double, bx: Double, by: Double): Double {
        val vx = bx - ax
        val vy = by - ay
        val len2 = vx * vx + vy * vy
        if (len2 < 1e-8) return hypot(px - ax, py - ay)
        val t = (((px - ax) * vx + (py - ay) * vy) / len2).coerceIn(0.0, 1.0)
        val projX = ax + t * vx
        val projY = ay + t * vy
        return hypot(px - projX, py - projY)
    }

    private fun edgeKey(a: Int, b: Int): Long {
        val lo = min(a, b).toLong()
        val hi = max(a, b).toLong()
        return (lo shl 32) or (hi and 0xFFFF_FFFFL)
    }

    companion object {
        const val NODE_STEP_SPACING_M = 1.50
        const val MIN_CORNER_DIST_M = 0.85
        const val CORNER_TURN_DEG = 32.0
        const val LOOP_CLOSURE_RADIUS_M = 1.05
        const val SPOT_REUSE_RADIUS_M = 0.75
        const val MAX_EDGE_CONNECT_M = 4.50
        const val MAX_SINGLE_STEP_JUMP_M = 5.50
        const val WAYPOINT_PASS_RADIUS_M = 1.15
        const val CORRIDOR_LOOKAHEAD_TOLERANCE_M = 0.85
        const val DETOUR_LATERAL_THRESHOLD_M = 2.25
        const val DETOUR_BACKTRACK_THRESHOLD_M = 2.15
        const val OBSTACLE_PENALTY_FACTOR = 15.0
        const val REROUTE_BANNER_DURATION_MS = 6_000L
        const val MAX_GRAPH_NODES = 450
    }
}
