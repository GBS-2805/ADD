package app.add.engine

import android.graphics.Path
import java.util.UUID
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * A small, deliberately explicit vector-network model.
 *
 * Points are vertices in a 0..1 layer space. Segments connect vertices by id, and
 * regions name the ordered segments that form one filled/stroked contour. This is
 * the shape Figma's VectorNetwork describes, reduced to the part the spike can
 * edit and export without a full boolean kernel.
 */
data class VecPoint(
    val id: String,
    val x: Float,
    val y: Float,
)

data class VecSegment(
    val id: String,
    val a: String,
    val b: String,
)

data class VecRegion(
    val id: String,
    val segments: List<String>,
    val fill: Int,
)

data class VectorNetwork(
    val points: List<VecPoint>,
    val segments: List<VecSegment>,
    val regions: List<VecRegion>,
) {
    private val pointById = points.associateBy { it.id }
    private val segmentById = segments.associateBy { it.id }

    fun point(id: String): VecPoint? = pointById[id]

    fun movePoint(id: String, x: Float, y: Float): VectorNetwork {
        if (pointById[id] == null) return this
        return copy(points = points.map { if (it.id == id) it.copy(x = x.coerceIn(-0.5f, 1.5f), y = y.coerceIn(-0.5f, 1.5f)) else it })
    }

    fun nearestPoint(x: Float, y: Float, radius: Float): VecPoint? =
        points.minByOrNull { hypot(it.x - x, it.y - y) }
            ?.takeIf { hypot(it.x - x, it.y - y) <= radius }

    /**
     * Splits the first segment at its midpoint. The region keeps its winding order,
     * so adding a node never opens a closed contour.
     */
    fun addNode(): VectorNetwork {
        val segment = segments.firstOrNull() ?: return this
        val a = point(segment.a) ?: return this
        val b = point(segment.b) ?: return this
        val pointId = "p-${UUID.randomUUID().toString().take(6)}"
        val firstId = "s-${UUID.randomUUID().toString().take(6)}"
        val secondId = "s-${UUID.randomUUID().toString().take(6)}"
        val vertex = VecPoint(pointId, (a.x + b.x) / 2f, (a.y + b.y) / 2f)
        val first = VecSegment(firstId, a.id, pointId)
        val second = VecSegment(secondId, pointId, b.id)
        return copy(
            points = points + vertex,
            segments = segments.filterNot { it.id == segment.id } + first + second,
            regions = regions.map { region ->
                if (segment.id !in region.segments) region
                else region.copy(
                    segments = region.segments.flatMap {
                        if (it == segment.id) listOf(firstId, secondId) else listOf(it)
                    }
                )
            },
        )
    }

    /**
     * Removes a vertex and bridges its two neighbours. A triangle is the smallest
     * editable closed region, so deletion stops there.
     */
    fun deleteNode(id: String): VectorNetwork {
        if (points.size <= 3) return this
        val incident = segments.filter { it.a == id || it.b == id }
        if (incident.size != 2) return this
        val neighbours = incident.map { if (it.a == id) it.b else it.a }.distinct()
        if (neighbours.size != 2) return this

        val bridgeId = "s-${UUID.randomUUID().toString().take(6)}"
        val bridge = VecSegment(bridgeId, neighbours[0], neighbours[1])
        val incidentIds = incident.map { it.id }.toSet()
        val remainingSegments = segments.filterNot { it.id in incidentIds } + bridge
        val remainingRegions = regions.map { region ->
            if (region.segments.none { it in incidentIds }) region
            else {
                val at = region.segments.indexOfFirst { it in incidentIds }.coerceAtLeast(0)
                val ordered = region.segments.filterNot { it in incidentIds }.toMutableList()
                ordered.add(at.coerceAtMost(ordered.size), bridgeId)
                region.copy(segments = ordered)
            }
        }
        return copy(
            points = points.filterNot { it.id == id },
            segments = remainingSegments,
            regions = remainingRegions,
        )
    }

    /**
     * Traces every region into one android.graphics.Path in local 0..1 space.
     * Android's Canvas is Skia-backed, so this is the path the exporter and screen
     * both draw.
     */
    fun buildPath(): Path {
        val path = Path()
        path.fillType = Path.FillType.EVEN_ODD
        regions.forEach { region ->
            val ordered = region.segments.mapNotNull { segmentById[it] }
            if (ordered.isEmpty()) return@forEach
            val first = point(ordered.first().a) ?: return@forEach
            path.moveTo(first.x, first.y)
            var cursor = ordered.first()
            ordered.forEach { segment ->
                val a = point(segment.a)
                val b = point(segment.b)
                if (a != null && b != null) {
                    val next = when {
                        cursor.a == segment.a && cursor.b == segment.b -> b
                        cursor.b == segment.a && cursor.a == segment.b -> a
                        else -> b
                    }
                    path.lineTo(next.x, next.y)
                    cursor = segment
                }
            }
            path.close()
        }
        return path
    }
}

fun starNetwork(): VectorNetwork {
    val points = mutableListOf<VecPoint>()
    val segments = mutableListOf<VecSegment>()
    val count = 10
    repeat(count) { index ->
        val angle = -PI / 2.0 + index * (2.0 * PI / count)
        val radius = if (index % 2 == 0) 0.48f else 0.205f
        points += VecPoint(
            id = "star-$index",
            x = (0.5 + cos(angle) * radius).toFloat(),
            y = (0.5 + sin(angle) * radius).toFloat(),
        )
    }
    repeat(count) { index ->
        segments += VecSegment("star-seg-$index", "star-$index", "star-${(index + 1) % count}")
    }
    return VectorNetwork(
        points = points,
        segments = segments,
        regions = listOf(VecRegion("star-region", segments.map { it.id }, EngineEdition.PlumArgb)),
    )
}

fun orbitNetwork(): VectorNetwork {
    val points = mutableListOf<VecPoint>()
    val segments = mutableListOf<VecSegment>()
    val count = 24
    repeat(count) { index ->
        val angle = index * (2.0 * PI / count)
        points += VecPoint(
            id = "orbit-$index",
            x = (0.5 + cos(angle) * 0.47).toFloat(),
            y = (0.5 + sin(angle) * 0.47).toFloat(),
        )
    }
    repeat(count) { index ->
        segments += VecSegment("orbit-seg-$index", "orbit-$index", "orbit-${(index + 1) % count}")
    }
    return VectorNetwork(
        points = points,
        segments = segments,
        regions = listOf(VecRegion("orbit-region", segments.map { it.id }, 0x00000000)),
    )
}

fun triangleNetwork(): VectorNetwork = VectorNetwork(
    points = listOf(
        VecPoint("triangle-a", 0.5f, 0.08f),
        VecPoint("triangle-b", 0.92f, 0.9f),
        VecPoint("triangle-c", 0.08f, 0.9f),
    ),
    segments = listOf(
        VecSegment("triangle-ab", "triangle-a", "triangle-b"),
        VecSegment("triangle-bc", "triangle-b", "triangle-c"),
        VecSegment("triangle-ca", "triangle-c", "triangle-a"),
    ),
    regions = listOf(VecRegion("triangle-region", listOf("triangle-ab", "triangle-bc", "triangle-ca"), EngineEdition.PlumHoverArgb)),
)
