package com.batmon.app.util

internal fun Throwable.safeMessage(): String =
    message?.takeIf { it.isNotBlank() } ?: javaClass.simpleName
