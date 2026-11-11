package app.add.engine

/**
 * A real group node. Its children are stored in the group's own normalised space:
 * the origin is the group's top-left, one unit is one artboard width, and neither
 * the group's rotation nor its scale is baked into a child. That is what lets the
 * group move, turn and scale without touching a single child coordinate, and why a
 * child's motion path never has to be rewritten when its group moves.
 *
 * The local-to-world map is
 *
 *   T(centre) * R(rotation) * SkewX(skewX) * S(scaleX, scaleY) * T(-w/2, -h/2)
 *
 * so a point given from the group's top-left lands where the painter draws it.
 */
data class GroupLayer(
    override val id: String,
    override val name: String,
    override val visible: Boolean = true,
    override val x: Float,
    override val y: Float,
    override val w: Float,
    override val h: Float,
    override val opacity: Float = 1f,
    override val rotation: Float = 0f,
    override val skewX: Float = 0f,
    val scaleX: Float = 1f,
    val scaleY: Float = 1f,
    val children: List<Layer> = emptyList(),
    override val style: LayerStyle = LayerStyle(),
) : Layer

val GroupLayer.localToWorld: Affine
    get() = Affine.translate(centerX, centerY) *
        Affine.rotate(rotation) *
        Affine.skewX(skewX) *
        Affine.scale(scaleX, scaleY) *
        Affine.translate(-w / 2f, -h / 2f)

fun GroupLayer.containsWorldPoint(x: Float, y: Float): Boolean {
    val (lx, ly) = localToWorld.inverse()?.map(x, y) ?: return false
    return lx in 0f..w && ly in 0f..h
}

fun GroupLayer.worldCorners(): List<Pair<Float, Float>> {
    val matrix = localToWorld
    return listOf(
        matrix.map(0f, 0f),
        matrix.map(w, 0f),
        matrix.map(w, h),
        matrix.map(0f, h),
    )
}

fun GroupLayer.worldBounds(): FrameBounds {
    val corners = worldCorners()
    return FrameBounds(
        left = corners.minOf { it.first },
        top = corners.minOf { it.second },
        right = corners.maxOf { it.first },
        bottom = corners.maxOf { it.second },
    )
}

/** Matrix of a leaf in its parent's space: a centred frame with rotation and skew. */
fun Layer.leafMatrix(): Affine =
    Affine.translate(centerX, centerY) * Affine.rotate(rotation) * Affine.skewX(skewX)

/** Matrix of any layer in its parent's space, including a group's own frame. */
fun Layer.localMatrix(): Affine = if (this is GroupLayer) localToWorld else leafMatrix()

// ----------------------------------------------------------------- tree traversal

fun List<Layer>.flattenTree(): List<Layer> = buildList {
    fun visit(list: List<Layer>) {
        list.forEach { layer ->
            add(layer)
            if (layer is GroupLayer) visit(layer.children)
        }
    }
    visit(this@flattenTree)
}

fun List<Layer>.findLayer(id: String): Layer? =
    find { it.id == id } ?: filterIsInstance<GroupLayer>().firstNotNullOfOrNull { it.children.findLayer(id) }

fun List<Layer>.parentGroup(id: String): GroupLayer? =
    filterIsInstance<GroupLayer>().firstNotNullOfOrNull { group ->
        if (group.children.any { it.id == id }) group else group.children.parentGroup(id)
    }

/** Groups around [id], nearest parent first. */
fun List<Layer>.ancestors(id: String): List<GroupLayer> {
    val direct = parentGroup(id) ?: return emptyList()
    return listOf(direct) + ancestors(direct.id)
}

fun List<Layer>.mapLayer(id: String, transform: (Layer) -> Layer): List<Layer> =
    map { layer ->
        when {
            layer.id == id -> transform(layer)
            layer is GroupLayer -> layer.copy(children = layer.children.mapLayer(id, transform))
            else -> layer
        }
    }

fun List<Layer>.removeLayer(id: String): Pair<List<Layer>, Layer?> {
    var removed: Layer? = null
    val next = mapNotNull { layer ->
        when {
            layer.id == id -> {
                removed = layer
                null
            }
            layer is GroupLayer -> {
                val (children, found) = layer.children.removeLayer(id)
                if (found != null) removed = found
                layer.copy(children = children)
            }
            else -> layer
        }
    }
    return next to removed
}

fun List<Layer>.insertLayer(parentId: String?, index: Int, layer: Layer): List<Layer> {
    if (parentId == null) {
        return toMutableList().also { it.add(index.coerceIn(0, it.size), layer) }
    }
    return map { item ->
        if (item is GroupLayer && item.id == parentId) {
            item.copy(children = item.children.toMutableList().also {
                it.add(index.coerceIn(0, it.size), layer)
            })
        } else if (item is GroupLayer) {
            item.copy(children = item.children.insertLayer(parentId, index, layer))
        } else {
            item
        }
    }
}

fun List<Layer>.replaceChildren(parentId: String?, children: List<Layer>): List<Layer> {
    if (parentId == null) return children
    return map { item ->
        if (item is GroupLayer && item.id == parentId) item.copy(children = children)
        else if (item is GroupLayer) item.copy(children = item.children.replaceChildren(parentId, children))
        else item
    }
}

// ----------------------------------------------------------------- group / ungroup

/**
 * Groups [ids] when they are siblings. The group takes the union frame, sits at the
 * highest selected index in paint order, and the children are translated into its
 * local space without changing a single world coordinate.
 */
fun List<Layer>.groupSelected(
    ids: Set<String>,
    name: String = "GROUP",
    groupId: String = "group-${java.util.UUID.randomUUID().toString().take(6)}",
): Pair<List<Layer>, GroupLayer>? {
    if (ids.size < 2) return null
    val parent = parentGroup(ids.first())
    if (ids.any { parentGroup(it)?.id != parent?.id }) return null
    val siblings = parent?.children ?: this
    val chosen = siblings.filter { it.id in ids }
    if (chosen.size != ids.size) return null

    val left = chosen.minOf { it.x }
    val top = chosen.minOf { it.y }
    val right = chosen.maxOf { it.x + it.w }
    val bottom = chosen.maxOf { it.y + it.h }
    val group = GroupLayer(
        id = groupId,
        name = name,
        x = left,
        y = top,
        w = (right - left).coerceAtLeast(0.02f),
        h = (bottom - top).coerceAtLeast(0.02f),
        children = chosen.map { it.withBounds(it.x - left, it.y - top, it.w, it.h) },
    )
    val highest = siblings.indexOfLast { it.id in ids }
    val remaining = siblings.filterNot { it.id in ids }
    val insertAt = siblings.take(highest + 1).count { it.id !in ids }
    val next = remaining.toMutableList().also { it.add(insertAt, group) }
    return replaceChildren(parent?.id, next) to group
}

/**
 * Dissolves [groupId] into its parent. Each child is flattened through the group's
 * matrix, so a rotated, skewed or non-uniformly scaled group returns children with
 * the same four world corners they had while grouped.
 */
fun List<Layer>.ungroupSelected(groupId: String): List<Layer>? {
    val group = findLayer(groupId) as? GroupLayer ?: return null
    val parent = parentGroup(groupId)
    val siblings = parent?.children ?: this
    val index = siblings.indexOfFirst { it.id == groupId }
    if (index < 0) return null
    val flattened = group.children.map { child -> flattenChild(child, group) }
    val next = siblings.toMutableList().also {
        it.removeAt(index)
        it.addAll(index, flattened)
    }
    return replaceChildren(parent?.id, next)
}

private fun flattenChild(child: Layer, group: GroupLayer): Layer {
    val world = group.localToWorld * child.localMatrix()
    // A leaf's local matrix is already centred, so its centre is the origin; a group's
    // matrix maps its top-left, so its centre is half its frame.
    val centre = if (child is GroupLayer) world.map(child.w / 2f, child.h / 2f) else world.map(0f, 0f)
    val parts = LayerAffine.decompose(
        Affine(world.a, world.b, world.c, world.d, centre.first, centre.second)
    ) ?: return child
    return if (child is GroupLayer) {
        child.copy(
            x = centre.first - child.w / 2f,
            y = centre.second - child.h / 2f,
            rotation = parts.rotation,
            skewX = parts.skewX,
            scaleX = parts.scaleX * child.scaleX,
            scaleY = parts.scaleY * child.scaleY,
        )
    } else {
        child.withFrame(
            x = centre.first - child.w * parts.scaleX / 2f,
            y = centre.second - child.h * parts.scaleY / 2f,
            w = child.w * parts.scaleX,
            h = child.h * parts.scaleY,
            rotation = parts.rotation,
            skewX = parts.skewX,
        )
    }
}

/** Frame edit that keeps the layer's kind-specific fields. */
fun Layer.withFrame(
    x: Float,
    y: Float,
    w: Float,
    h: Float,
    rotation: Float,
    skewX: Float,
): Layer = when (this) {
    is VectorLayer -> copy(x = x, y = y, w = w, h = h, rotation = rotation, skewX = skewX)
    is TextLayer -> copy(x = x, y = y, w = w, h = h, rotation = rotation, skewX = skewX)
    is IconLayer -> copy(x = x, y = y, w = w, h = h, rotation = rotation, skewX = skewX)
    is ImageLayer -> copy(x = x, y = y, w = w, h = h, rotation = rotation, skewX = skewX)
    is ModelLayer -> copy(x = x, y = y, w = w, h = h, rotation = rotation, skewX = skewX)
    is GroupLayer -> copy(x = x, y = y, w = w, h = h, rotation = rotation, skewX = skewX)
}

fun Layer.deepCopy(newId: (String) -> String, dx: Float = 0.04f, dy: Float = 0.04f): Layer = when (this) {
    is GroupLayer -> copy(
        id = newId(id),
        name = "$name COPY",
        x = x + dx,
        y = y + dy,
        children = children.map { it.deepCopy(newId, 0f, 0f) },
    )
    is VectorLayer -> copy(id = newId(id), name = "$name COPY", x = x + dx, y = y + dy)
    is TextLayer -> copy(id = newId(id), name = "$name COPY", x = x + dx, y = y + dy)
    is IconLayer -> copy(id = newId(id), name = "$name COPY", x = x + dx, y = y + dy)
    is ImageLayer -> copy(id = newId(id), name = "$name COPY", x = x + dx, y = y + dy)
    is ModelLayer -> copy(id = newId(id), name = "$name COPY", x = x + dx, y = y + dy)
}

// --------------------------------------------------------------------- rows

data class LayerRow(val layer: Layer, val depth: Int, val parentId: String?)

/** Paint order is top-down on screen, so the reversed list is the display order. */
fun List<Layer>.visibleRows(expanded: Set<String>, depth: Int = 0, parentId: String? = null): List<LayerRow> =
    asReversed().flatMap { layer ->
        val row = LayerRow(layer, depth, parentId)
        if (layer is GroupLayer && layer.id in expanded) {
            listOf(row) + layer.children.visibleRows(expanded, depth + 1, layer.id)
        } else {
            listOf(row)
        }
    }

// -------------------------------------------------------------- motion in a tree

/**
 * A child's path lives in group-local space, so this maps it out through every
 * ancestor without changing the stored points. A group move therefore carries the
 * path exactly as it carries the child.
 */
fun List<Layer>.motionInWorld(layerId: String, path: MotionPath): MotionPath {
    var mapped = path
    ancestors(layerId).forEach { group ->
        mapped = MotionAnchoring.outOfGroup(mapped, group.localToWorld)
    }
    return mapped
}
