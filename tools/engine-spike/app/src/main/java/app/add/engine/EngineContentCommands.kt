package app.add.engine

/** Add, duplicate, delete and reorder commands. */

    internal fun EngineDocument.addText() {
        act("TYPE+")
        val id = newId("type")
        val layer = TextLayer(
            id = id,
            name = "TYPE / NEW",
            x = 0.18f,
            y = 0.16f,
            w = 0.54f,
            h = 0.16f,
            text = "NEW LINE",
            fontSize = 58f,
            lineHeight = 0.95f,
            color = EngineEdition.InkArgb,
        )
        commit { it + layer }
        selectedId = id
        selectedIds = setOf(id)
        // An add is a finished action, not an armed tool: the new line is selected and
        // ready to move. Leaving Type armed made every later tap feel like another add.
        tool = Tool.Select
        status = "TYPE ADDED · DRAG TO PLACE"
    }

    internal fun EngineDocument.addVector() {
        act("VECTOR+")
        val id = newId("vector")
        val layer = VectorLayer(
            id = id,
            name = "VECTOR / NEW",
            x = 0.18f,
            y = 0.18f,
            w = 0.32f,
            h = squareFrameHeight(0.32f),
            network = triangleNetwork(),
            fill = EngineEdition.PlumHoverArgb,
            stroke = EngineEdition.InkArgb,
            strokeWidth = 4f,
        )
        commit { it + layer }
        selectedId = id
        selectedIds = setOf(id)
        tool = Tool.Select
        status = "VECTOR ADDED · 3 NODES · DRAG TO PLACE"
    }

    internal fun EngineDocument.addSticker(preset: StickerPreset) {
        act("STICKER+")
        val id = newId("sticker")
        val offset = (layers.count { it.name.startsWith(preset.kind) } * 0.03f).coerceAtMost(0.18f)
        val layer = VectorLayer(
            id = id,
            name = "${preset.kind} / ${preset.label}",
            x = 0.2f + offset,
            y = 0.2f + offset,
            w = 0.26f,
            h = squareFrameHeight(0.26f),
            network = preset.network,
            fill = preset.fill,
            stroke = preset.stroke,
            strokeWidth = preset.strokeWidth,
        )
        commit { it + layer }
        selectedId = id
        selectedIds = setOf(id)
        selectedNodeId = null
        tool = Tool.Select
        status = "${preset.kind} ADDED · ${preset.label}"
    }

    internal fun EngineDocument.addIcon(glyph: IconGlyph) {
        act("ICON+")
        val id = newId("icon")
        val offset = (layers.count { it is IconLayer } * 0.025f).coerceAtMost(0.16f)
        val layer = IconLayer(
            id = id,
            name = "ICON / ${glyph.name.uppercase()}",
            x = (0.16f + offset).coerceAtMost(0.7f),
            y = (0.22f + offset).coerceAtMost(0.7f),
            w = 0.22f,
            h = squareFrameHeight(0.22f),
            glyph = glyph,
            tint = EngineEdition.InkArgb,
        )
        commit { it + layer }
        selectedId = id
        selectedIds = setOf(id)
        selectedNodeId = null
        tool = Tool.Select
        status = "ICON PLACED · ${glyph.brandLabel.ifBlank { glyph.brandSlug }} / ${glyph.name} · ${glyph.paths.size} PATHS"
    }

    internal fun EngineDocument.addModel(asset: ModelAsset) {
        act("3D+")
        val id = newId("model")
        val offset = (modelLayers.size * 0.05f).coerceAtMost(0.14f)
        val layer = ModelLayer(
            id = id,
            name = "3D / ${asset.label.uppercase()}",
            x = (0.18f + offset).coerceAtMost(0.58f),
            y = (0.18f + offset).coerceAtMost(0.58f),
            w = 0.34f,
            h = 0.32f,
            scaleToUnits = 0.92f,
            asset = asset,
        )
        commit { it + layer }
        selectedId = id
        selectedIds = setOf(id)
        tool = Tool.Select
        status = "3D ADDED · ${asset.label.uppercase()} · DRAG TO PLACE"
    }

    /** Steps a type layer through the three bundled Poppins faces. */
    internal fun EngineDocument.setWeight(weight: Int) {
        val id = selectedId ?: return
        act("WEIGHT")
        val face = weight.coerceIn(400, 600)
        commit { layers -> layers.map { if (it.id == id && it is TextLayer) it.copy(weight = face) else it } }
        status = "TYPE FACE $face"
    }

    internal fun EngineDocument.duplicateSelected() {
        val layer = selected ?: return
        val parent = layers.parentGroup(selectedId)
        val siblings = parent?.children ?: layers
        val index = siblings.indexOfFirst { it.id == selectedId }
        act("DUP")
        val copy = layer.duplicate()
        val next = layers.insertLayer(parent?.id, index + 1, copy)
        commit { next }
        selectedIds = setOf(copy.id)
        selectedId = copy.id
        selectedNodeId = null
        editingModelId = null
        status = when (copy) {
            is ModelLayer -> "3D LAYER DUPLICATED · ${modelLayers.size} VIEWPORTS"
            is IconLayer -> "ICON LAYER DUPLICATED"
            is GroupLayer -> "GROUP DUPLICATED · ${copy.children.size} LAYERS"
            else -> "LAYER DUPLICATED"
        }
    }

    internal fun EngineDocument.deleteSelected() {
        val id = selectedId
        val target = layers.findLayer(id) ?: return
        val next = layers.removeLayer(id).first
        if (next.flattenTree().isEmpty()) return
        act("DEL", target.name)
        commit { next }
        if (editingModelId == id) editingModelId = null
        if (editingGroupId == id || layers.findLayer(editingGroupId.orEmpty()) == null) editingGroupId = null
        selectedId = next.flattenTree().lastOrNull()?.id.orEmpty()
        selectedIds = setOf(selectedId).filter { it.isNotBlank() }.toSet()
        selectedNodeId = null
        status = "LAYER DELETED"
    }

    internal fun EngineDocument.reorderSelected(delta: Int) = moveLayer(selectedId, delta)

    /** Moves any row, because the layer list reorders in place and not only the selection. */
    internal fun EngineDocument.moveLayer(id: String, delta: Int) {
        val parent = layers.parentGroup(id)
        val siblings = parent?.children ?: layers
        val index = siblings.indexOfFirst { it.id == id }
        if (index < 0) return
        val target = (index + delta).coerceIn(0, siblings.lastIndex)
        if (target == index) return
        act(if (delta > 0) "FRONT" else "BACK", siblings[index].name)
        val reordered = siblings.toMutableList().also {
            val moving = it.removeAt(index)
            it.add(target, moving)
        }
        val next = layers.replaceChildren(parent?.id, reordered)
        commit { next }
        status = if (delta > 0) "LAYER MOVED FORWARD" else "LAYER MOVED BACK"
    }
