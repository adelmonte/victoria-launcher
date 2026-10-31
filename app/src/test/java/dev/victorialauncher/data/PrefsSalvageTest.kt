// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.data

import androidx.datastore.preferences.core.PreferencesSerializer
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.runBlocking
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PrefsSalvageTest {

    private val first = stringPreferencesKey("favorites")
    private val second = intPreferencesKey("icon_size_dp")
    private val third = stringPreferencesKey("icon_shape")

    private fun encoded(): ByteArray = runBlocking {
        val buffer = Buffer()
        PreferencesSerializer.writeTo(preferencesOf(first to "a,b,c", second to 42, third to "CIRCLE"), buffer)
        buffer.readByteArray()
    }

    private fun write(bytes: ByteArray): File =
        File.createTempFile("victoria_prefs", ".preferences_pb").apply { deleteOnExit(); writeBytes(bytes) }

    @Test
    fun `an intact file comes back whole`() {
        val prefs = salvagePreferences(write(encoded()))
        assertEquals("a,b,c", prefs[first])
        assertEquals(42, prefs[second])
        assertEquals("CIRCLE", prefs[third])
    }

    @Test
    fun `a torn write keeps the entries before the tear`() {
        val bytes = encoded()
        // Zeros from partway into the last entry to the end, as the reported file had.
        val torn = bytes.copyOf()
        for (i in bytes.size - 4 until bytes.size) torn[i] = 0
        val prefs = salvagePreferences(write(torn + ByteArray(64)))
        assertEquals("a,b,c", prefs[first])
        assertEquals(42, prefs[second])
        assertNull(prefs[third])
    }

    @Test
    fun `nothing readable is an empty set rather than a crash`() {
        assertTrue(salvagePreferences(write(ByteArray(128))).asMap().isEmpty())
        assertTrue(salvagePreferences(File("/nonexistent/victoria_prefs")).asMap().isEmpty())
    }
}
