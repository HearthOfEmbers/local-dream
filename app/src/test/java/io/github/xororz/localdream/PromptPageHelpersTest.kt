package io.github.xororz.localdream

import io.github.xororz.localdream.ui.screens.XY_MAX_CELLS
import io.github.xororz.localdream.ui.screens.XyAxisType
import io.github.xororz.localdream.ui.screens.appendXyValue
import io.github.xororz.localdream.ui.screens.fillXyRange
import io.github.xororz.localdream.ui.screens.insertBreakAt
import io.github.xororz.localdream.ui.screens.parseXyAxis
import io.github.xororz.localdream.ui.screens.toggleXyValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PromptPageHelpersTest {
    @Test
    fun fillRangeStepsWithoutFloatDrift() {
        assertEquals(listOf("1", "1.2", "1.4", "1.6", "1.8", "2"), fillXyRange(1f, 2f, 0.2f, integer = false))
    }

    @Test
    fun fillRangeCountsDown() {
        assertEquals(listOf("8", "6", "4"), fillXyRange(8f, 4f, 2f, integer = true))
    }

    @Test
    fun fillRangeRejectsBadStepAndHugeLists() {
        assertNull(fillXyRange(1f, 2f, 0f, integer = false))
        assertNull(fillXyRange(0f, XY_MAX_CELLS.toFloat() * 2, 1f, integer = true))
    }

    @Test
    fun filledRangeParsesAsAnAxis() {
        val values = fillXyRange(4f, 8f, 2f, integer = true)!!
        assertNotNull(parseXyAxis(XyAxisType.STEPS, values.joinToString(", ")))
    }

    @Test
    fun toggleAddsThenRemovesSampler() {
        val once = toggleXyValue("euler_a", "lcm")
        assertEquals("euler_a, lcm", once)
        assertEquals("euler_a", toggleXyValue(once, "lcm"))
    }

    @Test
    fun appendTidiesStrayCommas() {
        assertEquals("4, 6, 8", appendXyValue("4, ,6,", "8"))
    }

    @Test
    fun breakGoesBetweenTagsWithCommas() {
        val text = "1girl, smile"
        val (out, caret) = insertBreakAt(text, text.indexOf(" smile"))
        assertEquals("1girl, BREAK, smile", out)
        assertEquals("1girl, BREAK, ".length, caret)
    }

    @Test
    fun breakAtEndLeavesCaretReadyForTheNextTag() {
        val (out, caret) = insertBreakAt("1girl,", 6)
        assertEquals("1girl, BREAK, ", out)
        assertEquals(out.length, caret)
    }

    @Test
    fun breakInEmptyPrompt() {
        assertEquals("BREAK, " to 7, insertBreakAt("", 0))
    }
}
