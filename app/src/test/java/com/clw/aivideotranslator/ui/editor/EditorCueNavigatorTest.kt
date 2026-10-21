package com.clw.aivideotranslator.ui.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorCueNavigatorTest {
    @Test fun firstAvailableCueBecomesSelected() {
        val navigator = EditorCueNavigator()

        val selection = navigator.reconcile(listOf("u1", "u2", "u3"))

        assertEquals("u1", selection.unitId)
        assertEquals(0, selection.index)
        assertEquals(3, selection.count)
        assertFalse(selection.canPrevious)
        assertTrue(selection.canNext)
    }

    @Test fun nextAndPreviousUseStableCueIdentity() {
        val navigator = EditorCueNavigator()
        navigator.reconcile(listOf("u1", "u2", "u3"))

        assertEquals("u2", navigator.next(listOf("u1", "u2", "u3")).unitId)
        assertEquals("u3", navigator.next(listOf("u1", "u2", "u3")).unitId)
        assertEquals("u3", navigator.next(listOf("u1", "u2", "u3")).unitId)
        assertEquals("u2", navigator.previous(listOf("u1", "u2", "u3")).unitId)
    }

    @Test fun reconciliationPreservesSelectedIdentityAcrossListRefresh() {
        val navigator = EditorCueNavigator()
        navigator.reconcile(listOf("u1", "u2", "u3"))
        navigator.next(listOf("u1", "u2", "u3"))

        val selection = navigator.reconcile(listOf("u0", "u2", "u3", "u4"))

        assertEquals("u2", selection.unitId)
        assertEquals(1, selection.index)
    }

    @Test fun missingSelectedCueFallsBackToFirstAvailableCue() {
        val navigator = EditorCueNavigator()
        navigator.reconcile(listOf("u1", "u2"))
        navigator.next(listOf("u1", "u2"))

        val selection = navigator.reconcile(listOf("u7", "u8"))

        assertEquals("u7", selection.unitId)
        assertEquals(0, selection.index)
    }

    @Test fun emptyListProducesNoSelectionAndNeverUnderflows() {
        val navigator = EditorCueNavigator()

        val selection = navigator.reconcile(emptyList())

        assertEquals(null, selection.unitId)
        assertEquals(0, selection.index)
        assertEquals(0, selection.count)
        assertFalse(selection.canPrevious)
        assertFalse(selection.canNext)
    }
}
