package app.add.engine

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

data class StickerPreset(
    val id: String,
    val kind: String,
    val label: String,
    val network: VectorNetwork,
    val fill: Int,
    val stroke: Int,
    val strokeWidth: Float = 4f,
)

data class StickerCategory(val label: String, val presets: List<StickerPreset>)

/**
 * A deliberately small, local library for the engine spike. These are real vector
 * networks, so a placed sticker is editable with the same nodes as any other shape;
 * no icon font, network request or vendor asset is involved.
 */
object StickerLibrary {
    private val stickers = listOf(
        StickerPreset(
            id = "star",
            kind = "STICKER",
            label = "STAR",
            network = starNetwork(),
            fill = 0xFF0FBF92.toInt(),
            stroke = 0xFF11231C.toInt(),
        ),
        StickerPreset(
            id = "burst",
            kind = "STICKER",
            label = "BURST",
            network = radialNetwork("burst", 16, 0.49f, 0.22f),
            fill = 0xFFE86A4A.toInt(),
            stroke = 0xFF11231C.toInt(),
        ),
        StickerPreset(
            id = "heart",
            kind = "STICKER",
            label = "HEART",
            network = heartNetwork(),
            fill = 0xFFE86A4A.toInt(),
            stroke = 0xFF11231C.toInt(),
        ),
        StickerPreset(
            id = "bolt",
            kind = "STICKER",
            label = "BOLT",
            network = normalizedNetwork(
                "bolt",
                listOf(
                    0.56f to 0.03f,
                    0.18f to 0.56f,
                    0.45f to 0.56f,
                    0.33f to 0.97f,
                    0.82f to 0.42f,
                    0.55f to 0.42f,
                    0.68f to 0.03f,
                ),
            ),
            fill = 0xFFD6E381.toInt(),
            stroke = 0xFF11231C.toInt(),
        ),
    )

    private val icons = listOf(
        StickerPreset(
            id = "diamond",
            kind = "ICON",
            label = "DIAMOND",
            network = normalizedNetwork(
                "diamond",
                listOf(0.5f to 0.04f, 0.95f to 0.5f, 0.5f to 0.96f, 0.05f to 0.5f),
            ),
            fill = 0,
            stroke = 0xFF0FBF92.toInt(),
            strokeWidth = 5f,
        ),
        StickerPreset(
            id = "arrow",
            kind = "ICON",
            label = "ARROW",
            network = normalizedNetwork(
                "arrow",
                listOf(
                    0.05f to 0.38f,
                    0.53f to 0.38f,
                    0.53f to 0.14f,
                    0.95f to 0.5f,
                    0.53f to 0.86f,
                    0.53f to 0.62f,
                    0.05f to 0.62f,
                ),
            ),
            fill = 0,
            stroke = 0xFF0FBF92.toInt(),
            strokeWidth = 5f,
        ),
        StickerPreset(
            id = "plus",
            kind = "ICON",
            label = "PLUS",
            network = normalizedNetwork(
                "plus",
                listOf(
                    0.4f to 0.05f,
                    0.6f to 0.05f,
                    0.6f to 0.4f,
                    0.95f to 0.4f,
                    0.95f to 0.6f,
                    0.6f to 0.6f,
                    0.6f to 0.95f,
                    0.4f to 0.95f,
                    0.4f to 0.6f,
                    0.05f to 0.6f,
                    0.05f to 0.4f,
                    0.4f to 0.4f,
                ),
            ),
            fill = 0,
            stroke = 0xFF0FBF92.toInt(),
            strokeWidth = 5f,
        ),
        StickerPreset(
            id = "ring",
            kind = "ICON",
            label = "RING",
            network = radialNetwork("ring", 28, 0.47f, 0.47f),
            fill = 0,
            stroke = 0xFF0FBF92.toInt(),
            strokeWidth = 5f,
        ),
    )

    val categories = listOf(
        StickerCategory("STICKERS", stickers),
        StickerCategory("ICONS", icons),
    )

    val presets: List<StickerPreset> = categories.flatMap { it.presets }
}

private fun radialNetwork(prefix: String, count: Int, outer: Float, inner: Float): VectorNetwork {
    val raw = (0 until count).map { index ->
        val angle = -PI / 2.0 + index * (2.0 * PI / count)
        val radius = if (index % 2 == 0) outer else inner
        (0.5f + cos(angle).toFloat() * radius) to (0.5f + sin(angle).toFloat() * radius)
    }
    return normalizedNetwork(prefix, raw)
}

private fun heartNetwork(): VectorNetwork {
    val raw = (0 until 32).map { index ->
        val t = 2.0 * PI * index / 32.0
        val x = 16.0 * sin(t) * sin(t) * sin(t)
        val y = 13.0 * cos(t) - 5.0 * cos(2.0 * t) - 2.0 * cos(3.0 * t) - cos(4.0 * t)
        x.toFloat() to (-y).toFloat()
    }
    return normalizedNetwork("heart", raw)
}

private fun normalizedNetwork(prefix: String, raw: List<Pair<Float, Float>>): VectorNetwork {
    val minX = raw.minOf { it.first }
    val maxX = raw.maxOf { it.first }
    val minY = raw.minOf { it.second }
    val maxY = raw.maxOf { it.second }
    val span = max(maxX - minX, maxY - minY).coerceAtLeast(0.001f)
    val pad = 0.03f
    val points = raw.mapIndexed { index, (x, y) ->
        VecPoint(
            id = "$prefix-$index",
            x = pad + (x - minX) / span * (1f - pad * 2f),
            y = pad + (y - minY) / span * (1f - pad * 2f),
        )
    }
    val segments = points.indices.map { index ->
        VecSegment(
            id = "$prefix-seg-$index",
            a = points[index].id,
            b = points[(index + 1) % points.size].id,
        )
    }
    return VectorNetwork(
        points = points,
        segments = segments,
        regions = listOf(VecRegion("$prefix-region", segments.map { it.id }, 0xFF0FBF92.toInt())),
    )
}
