package app.add.engine

/** Selection and group commands, kept separate from document state. */

    internal fun EngineDocument.select(id: String, additive: Boolean = false) {
        val target = layers.findLayer(id) ?: return
        if (editingModelId != null && editingModelId != id) editingModelId = null
        if (additive) {
            selectedIds = if (id in selectedIds) selectedIds - id else selectedIds + id
            if (selectedIds.isEmpty()) selectedIds = setOf(id)
        } else {
            selectedIds = setOf(id)
        }
        selectedId = id
        selectedNodeId = null
        // Selecting a nested row edits at that row's level; there is no way to have a
        // child selected while the stage still believes it is at the artboard.
        editingGroupId = layers.parentGroup(id)?.id
        if (tool == Tool.Sticker) tool = Tool.Select
    }

    /** Tapping bare stage returns to Create, so the deck is never a one-time screen. */
    internal fun EngineDocument.clearSelection() {
        selectedId = ""
        selectedIds = emptySet()
        selectedNodeId = null
        editingModelId = null
        status = "NOTHING SELECTED · TAP CREATE OR A LAYER"
    }

    // ------------------------------------------------------------------- groups

    /** Enters the selected group so its children become the stage's editing level. */
    internal fun EngineDocument.enterGroup(): Boolean {
        val group = selected as? GroupLayer ?: return false
        editingGroupId = group.id
        selectedIds = emptySet()
        group.children.lastOrNull { it.visible }?.let { select(it.id) }
        status = "GROUP ENTERED · ${group.name}"
        return true
    }

    /** Leaves one group and selects the group that was left. */
    internal fun EngineDocument.exitGroup(): Boolean {
        val id = editingGroupId ?: return false
        val group = layers.findLayer(id) as? GroupLayer ?: return false
        editingGroupId = layers.parentGroup(id)?.id
        select(group.id)
        status = "GROUP LEFT · ${group.name}"
        return true
    }

    /** Repeated click: a child selects its parent, a group leaves the child level. */
    internal fun EngineDocument.selectParent(): Boolean {
        val parent = layers.parentGroup(selectedId) ?: return false
        if (editingGroupId == parent.id) editingGroupId = layers.parentGroup(parent.id)?.id
        select(parent.id)
        status = "SELECTED GROUP · ${parent.name}"
        return true
    }

    /** Groups the selected siblings and returns the new group's id. */
    internal fun EngineDocument.groupSelection(): String? {
        val (next, group) = layers.groupSelected(selectedIds) ?: run {
            status = "SELECT TWO LAYERS TO GROUP"
            return null
        }
        val movedPaths = motion.filterKeys { it in selectedIds }
        act("GROUP", group.name)
        commit { next }
        // The children's paths were stored in the parent they just left; the group's
        // local space is the only frame that keeps them on their layers now.
        val inverse = group.localToWorld.inverse()
        motion = if (inverse == null) {
            motion - movedPaths.keys
        } else {
            motion + movedPaths.mapValues { (_, path) -> path.mapped(inverse) }
        }
        editingGroupId = null
        selectedIds = setOf(group.id)
        selectedId = group.id
        status = "GROUPED · ${group.children.size} LAYERS"
        return group.id
    }

    /** Ungroups the selected group and selects the children it released. */
    internal fun EngineDocument.ungroupSelection(): Boolean {
        val group = selected as? GroupLayer ?: run {
            status = "SELECT A GROUP TO UNGROUP"
            return false
        }
        val next = layers.ungroupSelected(group.id) ?: return false
        val releasedPaths = motion.filterKeys { id -> group.children.any { it.id == id } }
        act("UNGROUP", group.name)
        commit { next }
        motion = (motion - group.id) + releasedPaths.mapValues { (_, path) ->
            path.mapped(group.localToWorld)
        }
        if (editingGroupId == group.id) editingGroupId = null
        val released = group.children.map { it.id }.filter { next.findLayer(it) != null }
        selectedIds = released.toSet()
        selectedId = released.lastOrNull().orEmpty()
        status = "UNGROUPED · ${released.size} LAYERS"
        return true
    }

    /** Drops a dragged row on a target row: into a group, or beside a sibling. */
    internal fun EngineDocument.dropLayer(draggedId: String, targetId: String): Boolean {
        if (draggedId == targetId) return true
        val dragged = layers.findLayer(draggedId) ?: return false
        if (dragged is GroupLayer && dragged.children.flattenTree().any { it.id == targetId }) return false
        val target = layers.findLayer(targetId) ?: return false
        val targetParent = layers.parentGroup(targetId)
        val intoGroup = target is GroupLayer
        val newParent = if (intoGroup) target.id else targetParent?.id
        val siblings = targetParent?.children ?: layers
        val index = if (intoGroup) target.children.size else siblings.indexOfFirst { it.id == targetId }
        val without = layers.removeLayer(draggedId).first
        val next = without.insertLayer(newParent, index.coerceAtLeast(0), dragged)
        if (next == layers) return true
        act("MOVE", dragged.name)
        commit { next }
        editingGroupId = newParent
        select(draggedId)
        status = if (intoGroup) "MOVED INTO ${target.name}" else "LAYER REORDERED"
        return true
    }
