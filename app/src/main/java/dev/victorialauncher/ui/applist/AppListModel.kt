// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.ui.applist

import androidx.compose.runtime.Immutable
import dev.victorialauncher.data.AppInfo
import android.icu.text.Transliterator
import android.os.Build
import androidx.annotation.RequiresApi
import java.text.Normalizer

@Immutable
sealed interface AppListRow {
    data class Header(val text: String) : AppListRow
    data class Entry(val app: AppInfo) : AppListRow
}

/**
 * Marked immutable so Compose treats it as a stable parameter. It is only ever replaced
 * wholesale, never mutated, but the `List` fields on their own have it inferred as unstable,
 * which costs the whole app list a recomposition every time anything above it changes.
 */
@Immutable
data class AppListModel(
    val rows: List<AppListRow>,
    /** First row index for each A-Z letter, in scrubber order. */
    val letterIndex: List<Pair<Char, Int>>,
) {
    /** The scrubber's alphabet, derived once here rather than at each place that draws it. */
    val letters: List<Char> = letterIndex.map { it.first }
}

/**
 * The letter a name is filed under, or '#'.
 *
 * Only A-Z get one of their own. Char.isLetter is true of every kanji and every hangul
 * syllable, so filing by it gave a strip of thousands of entries on a device with CJK app
 * names — one per character, which is no index at all.
 *
 * An alphabet that can be carried over letter for letter is, so Калькулятор files under K
 * rather than joining everything else in '#'. That keeps one strip for a phone whose app
 * names are half Latin, which is the usual case.
 *
 * Accents and ligatures are folded first, so Ärger files under A and Œuvre under O, rather to '#'
 * than falling to '#' with the scripts that have no place on an A-Z strip. '#' sorts above A,
 * being the lower codepoint.
 */
internal fun indexLetter(name: String): Char {
    val first = name.firstOrNull() ?: return '#'
    val folded = Normalizer.normalize(first.toString(), Normalizer.Form.NFKD)
        .firstOrNull()
        ?.uppercaseChar()
        ?: return '#'
    if (folded in 'A'..'Z') return folded
    LATIN_STANDALONE[folded]?.let { return it }
    return romanize(folded) ?: '#'
}

/**
 * Scripts an alphabet can be carried over to A-Z without inventing anything.
 *
 * Each of these has a letter-for-letter romanization, so К lands on K the way a reader of the
 * script would expect. Han and kana do not: romanizing those needs to know the word, and the
 * same character reads differently in Japanese and Chinese — so they keep '#'.
 */
private val ROMANIZABLE = setOf(
    Character.UnicodeScript.CYRILLIC,
    Character.UnicodeScript.GREEK,
    Character.UnicodeScript.ARMENIAN,
    Character.UnicodeScript.GEORGIAN,
)

/**
 * ICU does this properly and ships with the platform, so the table below is only what stands
 * in for it on Android 9 and older, where the transliterator is not public API. The two agree
 * on Cyrillic and Greek; Armenian and Georgian fall back to '#' there rather than carry a
 * third alphabet by hand.
 */
private val romanizer: Transliterator? by lazy {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
        null
    } else {
        runCatching { Transliterator.getInstance("Any-Latin; Latin-ASCII") }.getOrNull()
    }
}

private fun romanize(upper: Char): Char? {
    val script = runCatching { Character.UnicodeScript.of(upper.code) }.getOrNull()
    if (script !in ROMANIZABLE) return null
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        icuRomanize(upper)?.let { return it }
    }
    return FALLBACK_ROMAN[upper]
}

@RequiresApi(Build.VERSION_CODES.Q)
private fun icuRomanize(upper: Char): Char? =
    romanizer?.transliterate(upper.toString())
        ?.firstOrNull()
        ?.uppercaseChar()
        ?.takeIf { it in 'A'..'Z' }

/** Matched to what ICU's Any-Latin produces, so both paths file a name the same way. */
private val FALLBACK_ROMAN = mapOf(
    // Cyrillic
    'А' to 'A', 'Б' to 'B', 'В' to 'V', 'Г' to 'G', 'Д' to 'D', 'Е' to 'E', 'Ё' to 'E',
    'Ж' to 'Z', 'З' to 'Z', 'И' to 'I', 'Й' to 'J', 'К' to 'K', 'Л' to 'L', 'М' to 'M',
    'Н' to 'N', 'О' to 'O', 'П' to 'P', 'Р' to 'R', 'С' to 'S', 'Т' to 'T', 'У' to 'U',
    'Ф' to 'F', 'Х' to 'H', 'Ц' to 'C', 'Ч' to 'C', 'Ш' to 'S', 'Щ' to 'S', 'Ы' to 'Y',
    'Э' to 'E', 'Ю' to 'U', 'Я' to 'A',
    // Ukrainian, Belarusian, Serbian and Macedonian letters Russian does not use
    'Ґ' to 'G', 'Є' to 'E', 'І' to 'I', 'Ї' to 'I', 'Ў' to 'U',
    'Ђ' to 'D', 'Ј' to 'J', 'Љ' to 'L', 'Њ' to 'N', 'Ћ' to 'C', 'Џ' to 'D',
    // Greek
    'Α' to 'A', 'Β' to 'B', 'Γ' to 'G', 'Δ' to 'D', 'Ε' to 'E', 'Ζ' to 'Z', 'Η' to 'E',
    'Θ' to 'T', 'Ι' to 'I', 'Κ' to 'K', 'Λ' to 'L', 'Μ' to 'M', 'Ν' to 'N', 'Ξ' to 'X',
    'Ο' to 'O', 'Π' to 'P', 'Ρ' to 'R', 'Σ' to 'S', 'Τ' to 'T', 'Υ' to 'Y', 'Φ' to 'P',
    'Χ' to 'C', 'Ψ' to 'P', 'Ω' to 'O',
)

/**
 * Latin letters Unicode holds as characters in their own right rather than as an accented A-Z
 * one, so no amount of normalizing reaches the letter underneath. Without these, names in
 * Danish, Norwegian, Polish or Icelandic fall to '#' the same as a script that genuinely has
 * no A-Z letter to file under.
 */
private val LATIN_STANDALONE = mapOf(
    'Æ' to 'A', 'Ø' to 'O', 'Œ' to 'O', 'Ł' to 'L', 'Đ' to 'D', 'Ð' to 'D',
    'Þ' to 'T', 'Ħ' to 'H', 'Ŧ' to 'T', 'Ŋ' to 'N', 'Ə' to 'E', 'ẞ' to 'S', 'ß' to 'S',
)

/**
 * [launchCounts] empty keeps every section alphabetical; otherwise the apps inside each letter
 * are ordered by how often they were opened from here. The letters themselves never move —
 * an app is still filed under its own name, or the scrubber would be pointing at nothing.
 */
fun buildAppListModel(
    apps: List<AppInfo>,
    hidden: Set<String>,
    displayName: (AppInfo) -> String,
    launchCounts: Map<String, Int> = emptyMap(),
    /**
     * The app's name in English, asked for only when its own name has no letter to file under.
     *
     * A Japanese phone calls an app ブルー, which lands in '#' along with everything else that
     * is not A-Z — so scrubbing to B, where its English name Blue would put it, finds nothing.
     */
    englishName: (AppInfo) -> String? = { null },
    /**
     * Whether each letter gets a heading row of its own. Without them the letters are still
     * indexed — at the first app of each instead of at its heading — so the strip still knows
     * where to send the list.
     */
    showHeaders: Boolean = true,
): AppListModel {
    val visible = apps.filter { it.key !in hidden }
    val rows = mutableListOf<AppListRow>()

    val byLetter = visible.groupBy { app ->
        val own = indexLetter(displayName(app))
        if (own != '#') own else englishName(app)?.let { indexLetter(it) } ?: '#'
    }

    val letterIndex = mutableListOf<Pair<Char, Int>>()
    byLetter.toSortedMap().forEach { (letter, list) ->
        letterIndex += letter to rows.size
        if (showHeaders) rows += AppListRow.Header(letter.toString())
        list.sortedWith(
            compareByDescending<AppInfo> { launchCounts[it.key] ?: 0 }
                .thenBy { displayName(it).lowercase() }
        ).forEach { rows += AppListRow.Entry(it) }
    }

    return AppListModel(rows, letterIndex)
}

/**
 * The same list with only the entries [match] accepts, and only the headers still holding
 * something. Rebuilt rather than filtered in place: letterIndex stores row indices, so
 * removing any row invalidates every index after it.
 */
fun AppListModel.filtered(match: (AppInfo) -> Boolean): AppListModel {
    val kept = mutableListOf<AppListRow>()
    val newIndex = mutableListOf<Pair<Char, Int>>()
    var pendingHeader: AppListRow.Header? = null

    // Which letter the row being looked at belongs to. Read from the index this model already
    // carries rather than from the heading above it, because a list drawn without headings has
    // no heading to read — and the letters still have to come out of it.
    var cursor = 0
    var letter: Char? = null
    var letterKept = false

    rows.forEachIndexed { index, row ->
        while (cursor < letterIndex.size && letterIndex[cursor].second <= index) {
            letter = letterIndex[cursor].first
            letterKept = false
            cursor++
        }
        when (row) {
            is AppListRow.Header -> pendingHeader = row
            is AppListRow.Entry -> if (match(row.app)) {
                // Recorded before the heading is added, so the letter points at the heading
                // where there is one and at its first app where there is not.
                if (!letterKept) {
                    letter?.let { newIndex += it to kept.size }
                    letterKept = true
                }
                pendingHeader?.let { kept += it; pendingHeader = null }
                kept += row
            }
        }
    }
    return AppListModel(kept, newIndex)
}
