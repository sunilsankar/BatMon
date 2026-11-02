package com.batmon.app.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.EOFException

class ErrorTextTest {

    @Test
    fun safeMessage_usesExceptionMessageWhenPresent() {
        assertEquals("boom", RuntimeException("boom").safeMessage())
    }

    @Test
    fun safeMessage_fallsBackToClassNameWhenMessageIsNull() {
        assertEquals("EOFException", EOFException().safeMessage())
    }

    @Test
    fun safeMessage_fallsBackToClassNameWhenMessageIsBlank() {
        assertEquals("RuntimeException", RuntimeException("   ").safeMessage())
    }
}
