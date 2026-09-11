package com.sparklet.android.model

import kotlinx.serialization.Serializable

// Mirrors GET /api/map (sparklet/src/app/api/map/route.ts), which ports
// getKnowledgeMap() plus the server-side settled forceLayout() out of the
// web's src/app/map/page.tsx.
@Serializable
data class MapResponse(
    val nodes: List<MapNode>,
    val edges: List<MapEdge>,
    val totalLearned: Int,
    // Server-settled starting layout (220 Fruchterman-Reingold iterations).
    // The web and iOS clients run live wake-on-touch physics on top of these;
    // this client renders them as-is — see KnowledgeMapScreen.
    val positions: List<MapPosition>,
)

@Serializable
data class MapNode(
    val id: String,
    val title: String,
    val body: String,
    val category: Category,
)

@Serializable
data class MapEdge(
    val source: String,
    val target: String,
)

@Serializable
data class MapPosition(
    val id: String,
    val x: Double,
    val y: Double,
)
