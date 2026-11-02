package com.batmon.app.util

import org.junit.Assert.assertEquals
import org.junit.Test

class BlanksTest {

    @Test
    fun trimBlanks_stripsLeadingAndTrailingSpacesAndTabs() {
        assertEquals("hello", "  hello  ".trimBlanks())
        assertEquals("hello", "\t\thello\t".trimBlanks())
        assertEquals("hello world", " \t hello world \t ".trimBlanks())
    }

    @Test
    fun trimBlanks_preservesUnicodeWhitespace() {
        // Non-breaking space U+00A0
        val nbsp = "\u00A0"
        val withNbsp = "$nbsp password$nbsp "
        // Regular String.trim() would strip the NBSP
        assertEquals("password", withNbsp.trim())
        // trimBlanks() strips only ASCII space/tab, preserving the NBSP
        assertEquals("$nbsp password$nbsp", withNbsp.trimBlanks())
    }

    @Test
    fun trimBlanks_handlesEmptyAndBlankStrings() {
        assertEquals("", "".trimBlanks())
        assertEquals("", "   \t  ".trimBlanks())
        assertEquals("clean", "clean".trimBlanks())
    }
}
