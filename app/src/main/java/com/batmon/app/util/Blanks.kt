package com.batmon.app.util

/**
 * Strips ASCII spaces and tabs from the start and end of the string.
 * Unlike Kotlin's [String.trim], this preserves Unicode whitespace (such as non-breaking spaces),
 * mirroring Mosquitto's misc__trimblanks behavior.
 */
internal fun String.trimBlanks(): String {
    var start = 0
    var end = length - 1
    while (start <= end && (this[start] == ' ' || this[start] == '\t')) {
        start++
    }
    while (end >= start && (this[end] == ' ' || this[end] == '\t')) {
        end--
    }
    return if (start == 0 && end == length - 1) this else substring(start, end + 1)
}
