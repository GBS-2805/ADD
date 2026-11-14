package app.add.engine

/** Motion-path commands; paths stay in parent-local space. */

    // --------------------------------------------------------------- motion

    /** Stores a world-space path in the layer's parent space, where it is kept. */
    internal fun EngineDocument.setMotionPath(layerId: String, path: MotionPath) {
        if (layers.findLayer(layerId) == null) return
        var local = path
        layers.ancestors(layerId).asReversed().forEach { group ->
            local = MotionAnchoring.intoGroup(local, group.localToWorld) ?: local
        }
        stopMotion()
        commitMotion { it + (layerId to clampToParent(layerId, local)) }
        status = "MOTION PATH · ${layers.findLayer(layerId)?.name ?: layerId}"
    }

    internal fun EngineDocument.clearMotionPath(layerId: String) {
        stopMotion()
        commitMotion { it - layerId }
    }

    /** The path in artboard space, carried by every group that contains the layer. */
    internal fun EngineDocument.motionPathInWorld(layerId: String): MotionPath? {
        val path = motion[layerId] ?: return null
        return layers.motionInWorld(layerId, path)
    }

    /** Adds a cubic path at the layer's current centre. */
    internal fun EngineDocument.createMotionPath(layerId: String = selectedId) {
        val (x, y) = worldCentre(layerId) ?: return
        setMotionPath(layerId, EngineMotion.defaultPath(x, y))
    }

    internal fun EngineDocument.playMotion(layerId: String = selectedId) {
        if (!motion.containsKey(layerId)) {
            status = "ADD A PATH FIRST"
            return
        }
        playingLayerId = layerId
        motionProgress = 0f
        status = "PLAYING PATH · ${layers.findLayer(layerId)?.name ?: layerId}"
    }

    internal fun EngineDocument.seekMotion(value: Float) {
        motionProgress = value.coerceIn(0f, 1f)
    }

    internal fun EngineDocument.stopMotion() {
        if (playingLayerId == null && motionProgress == 0f) return
        playingLayerId = null
        motionProgress = 0f
    }

    /** Puts the start back on the layer centre without deleting the curve. */
    internal fun EngineDocument.resetMotionPath(layerId: String = selectedId) {
        val layer = layers.findLayer(layerId) ?: return
        val path = motion[layerId] ?: return
        stopMotion()
        commitMotion { it + (layerId to MotionAnchoring.anchoredTo(layer.centerX, layer.centerY, path)) }
        status = "PATH RESET · ${layer.name}"
    }

    /** One rigid delta in the layer's parent space. A group transform never calls this. */
    internal fun EngineDocument.translateMotionPath(layerId: String, dx: Float, dy: Float) {
        val path = motion[layerId] ?: return
        motion = motion + (layerId to clampToParent(layerId, path.translated(dx, dy)))
    }

    /** The parent-space displacement the painter uses while previewing. */
    internal fun EngineDocument.motionOffset(layerId: String): Pair<Float, Float>? {
        if (playingLayerId != layerId) return null
        val path = motion[layerId] ?: return null
        return EngineMotion.offset(path, motionProgress)
    }

    /** Moves one control dot. Index 0 is the start, which stays on the layer centre. */
    internal fun EngineDocument.setMotionPoint(layerId: String, index: Int, worldX: Float, worldY: Float) {
        val layer = layers.findLayer(layerId) ?: return
        val path = motion[layerId] ?: return
        val (px, py) = parentPoint(layerId, worldX, worldY) ?: return
        if (index == 0) {
            updateLayer(layerId) {
                it.withPosition(it.x + px - layer.centerX, it.y + py - layer.centerY)
            }
            return
        }
        val next = when (index) {
            1 -> path.copy(c1 = MotionPoint(px, py))
            2 -> path.copy(c2 = MotionPoint(px, py))
            else -> path.copy(end = MotionPoint(px, py))
        }
        motion = motion + (layerId to clampToParent(layerId, next))
    }

    /** Maps an artboard point through every ancestor group, outermost first. */
    internal fun EngineDocument.parentPoint(layerId: String, x: Float, y: Float): Pair<Float, Float>? {
        var px = x
        var py = y
        layers.ancestors(layerId).asReversed().forEach { group ->
            val inverse = group.localToWorld.inverse() ?: return null
            val mapped = inverse.map(px, py)
            px = mapped.first
            py = mapped.second
        }
        return px to py
    }

    internal fun EngineDocument.clampToParent(layerId: String, path: MotionPath): MotionPath {
        val parent = layers.parentGroup(layerId)
        return if (parent == null) {
            MotionAnchoring.clampedToArtboard(path)
        } else {
            MotionAnchoring.clampedToArtboard(path, parent.w, parent.h)
        }
    }

    internal fun EngineDocument.commitMotion(transform: (Map<String, MotionPath>) -> Map<String, MotionPath>) {
        val before = snapshot()
        val next = transform(motion)
        if (next == motion) return
        motion = next
        record(before)
        pruneMotion()
        syncHistory()
    }

    internal fun EngineDocument.pruneMotion() {
        val ids = layers.flattenTree().map { it.id }.toSet()
        motion = motion.filterKeys { it in ids }
        if (playingLayerId != null && playingLayerId !in ids) stopMotion()
    }

    /** A layer's centre in artboard space, through every ancestor group. */
    internal fun EngineDocument.worldCentre(id: String): Pair<Float, Float>? {
        val layer = layers.findLayer(id) ?: return null
        var x = layer.centerX
        var y = layer.centerY
        layers.ancestors(id).forEach { group ->
            val mapped = group.localToWorld.map(x, y)
            x = mapped.first
            y = mapped.second
        }
        return x to y
    }

    /** A layer's four corners in artboard space, through every ancestor group. */
    internal fun EngineDocument.worldCorners(id: String): List<Pair<Float, Float>>? {
        val layer = layers.findLayer(id) ?: return null
        val local = if (layer is GroupLayer) {
            layer.worldCorners()
        } else {
            val matrix = layer.leafMatrix()
            listOf(
                matrix.map(-layer.w / 2f, -layer.h / 2f),
                matrix.map(layer.w / 2f, -layer.h / 2f),
                matrix.map(layer.w / 2f, layer.h / 2f),
                matrix.map(-layer.w / 2f, layer.h / 2f),
            )
        }
        var points = local
        layers.ancestors(id).forEach { group ->
            points = points.map { group.localToWorld.map(it.first, it.second) }
        }
        return points
    }
