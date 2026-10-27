// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FolderTest {

    @Test
    fun `a folder token round-trips`() {
        val token = folderToken("abc123")
        assertEquals("abc123", folderIdFromToken(token))
    }

    @Test
    fun `an app component key is not mistaken for a folder`() {
        assertNull(folderIdFromToken("com.example.app/com.example.app.MainActivity"))
    }

    @Test
    fun `folders survive a JSON round-trip`() {
        val folders = listOf(
            Folder(id = "1", name = "Media", apps = listOf("a/A", "b/B")),
            Folder(id = "2", name = "Work", apps = emptyList(), icon = "pack:com.pack:ic_work"),
        )
        val restored = foldersFromJson(foldersToJson(folders))
        assertEquals(folders, restored)
    }

    @Test
    fun `malformed JSON yields no folders rather than throwing`() {
        assertTrue(foldersFromJson("{not json").isEmpty())
        assertTrue(foldersFromJson("").isEmpty())
        assertTrue(foldersFromJson(null).isEmpty())
    }

    @Test
    fun `an entry with no id is dropped instead of producing a folder nothing can address`() {
        val restored = foldersFromJson("""[{"name":"Nameless","apps":[]},{"id":"2","name":"Real","apps":[]}]""")
        assertEquals(1, restored.size)
        assertEquals("2", restored.first().id)
    }

    @Test
    fun `a folder with no icon round-trips as null rather than an empty string`() {
        val restored = foldersFromJson(foldersToJson(listOf(Folder("1", "Plain", emptyList()))))
        assertNull(restored.single().icon)
    }

    @Test
    fun `a folder's widgets survive a JSON round-trip`() {
        val folders = listOf(
            Folder(id = "1", name = "Music", apps = listOf("a/A"), widgets = listOf(FolderWidget(42, 200), FolderWidget(7, 96))),
        )
        assertEquals(folders, foldersFromJson(foldersToJson(folders)))
    }

    @Test
    fun `a folder saved before widgets reads back with none`() {
        val restored = foldersFromJson("""[{"id":"1","name":"Old","apps":["a/A"]}]""")
        assertEquals(emptyList<FolderWidget>(), restored.single().widgets)
    }

    @Test
    fun `a folder keeps its Now Playing card`() {
        val folders = listOf(Folder(id = "1", name = "Audio", apps = listOf("a/A"), nowPlaying = true))
        assertEquals(folders, foldersFromJson(foldersToJson(folders)))
        assertEquals(false, foldersFromJson("""[{"id":"1","name":"Old","apps":["a/A"]}]""").single().nowPlaying)
    }
}
