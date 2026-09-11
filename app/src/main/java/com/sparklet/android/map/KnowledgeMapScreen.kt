package com.sparklet.android.map

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.sparklet.android.model.MapNode
import com.sparklet.android.model.MapResponse
import com.sparklet.android.ui.theme.SparkletColors
import com.sparklet.android.ui.theme.categoryColor
import kotlin.math.max
import kotlin.math.min

// Renders the server's settled layout (220 Fruchterman-Reingold iterations,
// computed in /api/map) with pan/zoom and tap-to-preview.
//
// Deliberately does NOT port the live wake-on-touch physics the web and iOS
// clients run on top of those positions. The server layout is already the
// graph's resting shape, which is what the simulation converges back to
// anyway, so the difference at rest is nil — and reimplementing the force
// constants by eye would more likely drift from the other clients than match
// them. If this is ported later, take the constants from the web's
// forceLayout(), which is the shared source both other clients follow.
@Composable
fun KnowledgeMapScreen(viewModel: KnowledgeMapViewModel) {
    val response by viewModel.response.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    LaunchedEffect(Unit) { viewModel.loadIfNeeded() }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(
            "🗺️ Your knowledge map",
            style = MaterialTheme.typography.titleLarge,
            color = SparkletColors.TextPrimary,
        )
        response?.let {
            Text(
                "${it.totalLearned} learned facts as a growing constellation",
                style = MaterialTheme.typography.bodySmall,
                color = SparkletColors.TextTertiary,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
            )
        }

        val current = response
        when {
            current == null && isLoading -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator(color = SparkletColors.TextTertiary) }

            current == null -> Text(
                "Couldn't load your map.",
                style = MaterialTheme.typography.bodyMedium,
                color = SparkletColors.TextTertiary,
            )

            current.nodes.isEmpty() -> Text(
                "Read a few cards and they'll start joining up here.",
                style = MaterialTheme.typography.bodyMedium,
                color = SparkletColors.TextTertiary,
            )

            else -> MapCanvas(current)
        }
    }
}

@Composable
private fun MapCanvas(response: MapResponse) {
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var selected by remember { mutableStateOf<MapNode?>(null) }

    val positions = remember(response) { response.positions.associateBy { it.id } }
    val nodesById = remember(response) { response.nodes.associateBy { it.id } }

    // The server's coordinate space is arbitrary, so normalise it into the
    // unit square once and let the canvas size do the rest.
    val bounds = remember(response) {
        val xs = response.positions.map { it.x }
        val ys = response.positions.map { it.y }
        if (xs.isEmpty()) doubleArrayOf(0.0, 1.0, 0.0, 1.0)
        else doubleArrayOf(xs.min(), xs.max(), ys.min(), ys.max())
    }

    // One scale factor for both axes, then centre the result. Normalising x
    // and y independently would stretch the graph to fill the canvas, which
    // on a tall phone turns the layout's roughly circular resting shape into
    // an ellipse — the graph's own proportions carry meaning here, so the
    // fit preserves them and letterboxes instead.
    fun projector(width: Float, height: Float, inset: Float): (Double, Double) -> Offset {
        val spanX = (bounds[1] - bounds[0]).takeIf { it > 0 } ?: 1.0
        val spanY = (bounds[3] - bounds[2]).takeIf { it > 0 } ?: 1.0
        val w = width - inset * 2
        val h = height - inset * 2
        val unitScale = min(w / spanX.toFloat(), h / spanY.toFloat())
        val padX = (w - spanX.toFloat() * unitScale) / 2f
        val padY = (h - spanY.toFloat() * unitScale) / 2f
        return { x, y ->
            Offset(
                inset + padX + ((x - bounds[0]).toFloat() * unitScale),
                inset + padY + ((y - bounds[2]).toFloat() * unitScale),
            )
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(12.dp))
                .background(SparkletColors.Panel)
                .pointerInput(response) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(0.5f, 6f)
                        offset += pan
                    }
                }
                .pointerInput(response) {
                    detectTapGestures { tap ->
                        // Same projection the canvas draws with, so hit-testing
                        // can't drift from what's on screen.
                        val project = projector(size.width.toFloat(), size.height.toFloat(), 24f)
                        var best: MapNode? = null
                        var bestDistance = Float.MAX_VALUE
                        for (position in response.positions) {
                            val point = project(position.x, position.y) * scale + offset
                            val distance = (point - tap).getDistance()
                            if (distance < bestDistance) {
                                bestDistance = distance
                                best = nodesById[position.id]
                            }
                        }
                        selected = if (bestDistance <= 60f) best else null
                    }
                },
        ) {
            val projectPoint = projector(size.width, size.height, 24f)

            fun project(id: String): Offset? {
                val position = positions[id] ?: return null
                return projectPoint(position.x, position.y) * scale + offset
            }

            for (edge in response.edges) {
                val a = project(edge.source) ?: continue
                val b = project(edge.target) ?: continue
                drawLine(
                    color = SparkletColors.Border,
                    start = a,
                    end = b,
                    strokeWidth = 1f,
                )
            }

            val radius = max(3f, min(8f, 5f * scale))
            for (node in response.nodes) {
                val point = project(node.id) ?: continue
                drawCircle(
                    color = categoryColor(node.category.colorHex),
                    radius = if (node.id == selected?.id) radius * 2 else radius,
                    center = point,
                )
            }
        }

        selected?.let { node ->
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(12.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(SparkletColors.PanelAlt)
                    .padding(16.dp),
            ) {
                Text(
                    "${node.category.icon} ${node.category.name}",
                    style = MaterialTheme.typography.labelMedium,
                    color = categoryColor(node.category.colorHex),
                )
                Text(
                    node.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = SparkletColors.TextPrimary,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}
