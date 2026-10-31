// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.data

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferencesSerializer
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.runBlocking
import okio.Buffer
import java.io.File

/** The tag of field 1, length-delimited: one entry of the preferences map. */
private const val ENTRY_TAG = 0x0A

/**
 * What can still be read out of a preferences file that no longer parses.
 *
 * The file is a run of entries, one per setting, and the damage seen in practice is a torn
 * write: whole entries up front, then zeros where the rest should be. Every entry that still
 * reads on its own is kept, so a launcher that would otherwise crash at every start comes back
 * with its favorites and folders rather than as a fresh install. Nothing readable at all is
 * an empty set of preferences, which is still better than never starting.
 */
fun salvagePreferences(file: File): Preferences {
    val bytes = runCatching { file.readBytes() }.getOrNull() ?: return emptyPreferences()
    return runCatching { parsePreferences(salvageEntries(bytes)) }.getOrDefault(emptyPreferences())
}

/**
 * The entries of [bytes] that each parse on their own, joined back together.
 *
 * One that runs into the zeros at the end is dropped even when it parses: zeros are valid in
 * the middle of a string, so the setting the tear cut through reads back as a value nobody
 * chose — half a list of favorites, or a name ending in nothing.
 */
fun salvageEntries(bytes: ByteArray): ByteArray {
    val intactEnd = bytes.indexOfLast { it.toInt() != 0 } + 1
    val kept = Buffer()
    var pos = 0
    while (pos < bytes.size && bytes[pos].toInt() == ENTRY_TAG) {
        val (length, start) = readVarint(bytes, pos + 1) ?: break
        val end = start + length
        if (length < 0 || end > intactEnd || end < start) break
        val entry = bytes.copyOfRange(pos, end)
        if (runCatching { parsePreferences(entry) }.isSuccess) kept.write(entry)
        pos = end
    }
    return kept.readByteArray()
}

private fun parsePreferences(bytes: ByteArray): Preferences =
    runBlocking { PreferencesSerializer.readFrom(Buffer().write(bytes)) }

/** A varint starting at [from], and where the bytes after it begin; null if it runs off the end. */
private fun readVarint(bytes: ByteArray, from: Int): Pair<Int, Int>? {
    var result = 0L
    var shift = 0
    var pos = from
    while (pos < bytes.size && shift < 35) {
        val b = bytes[pos++].toInt()
        result = result or ((b and 0x7F).toLong() shl shift)
        if (b and 0x80 == 0) return if (result > Int.MAX_VALUE) null else result.toInt() to pos
        shift += 7
    }
    return null
}
