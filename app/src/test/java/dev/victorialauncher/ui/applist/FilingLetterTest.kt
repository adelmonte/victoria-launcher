// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.ui.applist

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Which letter a name files under when it starts with none.
 *
 * The English fallback is there so a phone whose apps name themselves in another script still
 * sorts somewhere useful. These are the cases where it must not apply.
 */
class FilingLetterTest {

    @Test
    fun `a chosen name starting with a digit files under hash, not the name it replaced`() {
        assertEquals('#', filingLetter("4PDA", renamed = true) { "ForPDA" })
    }

    @Test
    fun `an untouched name with no letter falls back to its english one`() {
        assertEquals('B', filingLetter("ブルー", renamed = false) { "Blue" })
    }

    @Test
    fun `a name with no letter and no english name files under hash`() {
        assertEquals('#', filingLetter("ブルー", renamed = false) { null })
    }

    @Test
    fun `a chosen name with a letter files under that letter`() {
        assertEquals('Q', filingLetter("Quad PDA", renamed = true) { "ForPDA" })
    }

    @Test
    fun `the english name is never asked for when the shown name has a letter`() {
        var asked = false
        val letter = filingLetter("Firefox", renamed = false) { asked = true; "Firefox" }
        assertEquals('F', letter)
        assertEquals(false, asked)
    }

    @Test
    fun `japanese kanji files under the kanji heading, not its english name`() {
        assertEquals(KANJI_HEADING, filingLetter("天気", renamed = false, japanese = true) { "Weather" })
    }

    @Test
    fun `japanese kana file under their row`() {
        assertEquals('は', filingLetter("ブルー", renamed = false, japanese = true) { "Blue" })
        assertEquals('か', japaneseHeading("ガイド"))
        assertEquals('た', japaneseHeading("ﾂｲｯﾀｰ"))
        assertEquals('や', japaneseHeading("ゃ"))
        assertEquals('わ', japaneseHeading("ん"))
        assertEquals('あ', japaneseHeading("あ"))
    }

    @Test
    fun `latin names keep their letter on a japanese phone`() {
        assertEquals('L', filingLetter("LINE", renamed = false, japanese = true) { null })
    }
}
