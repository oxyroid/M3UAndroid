package com.m3u.smartphone.ui.business.playlist.components

import kotlin.test.Test
import kotlin.test.assertEquals

class PlaylistTabRowPolicyTest {
    @Test
    fun `expanded menu reports expanded state and collapse action`() {
        val semantics = categoryMenuSemantics(
            isExpanded = true,
            expandedStateDescription = "expanded state",
            collapsedStateDescription = "collapsed state",
            expandActionLabel = "expand action",
            collapseActionLabel = "collapse action",
        )

        assertEquals("expanded state", semantics.stateDescription)
        assertEquals("collapse action", semantics.actionLabel)
    }

    @Test
    fun `collapsed menu reports collapsed state and expand action`() {
        val semantics = categoryMenuSemantics(
            isExpanded = false,
            expandedStateDescription = "expanded state",
            collapsedStateDescription = "collapsed state",
            expandActionLabel = "expand action",
            collapseActionLabel = "collapse action",
        )

        assertEquals("collapsed state", semantics.stateDescription)
        assertEquals("expand action", semantics.actionLabel)
    }
}
