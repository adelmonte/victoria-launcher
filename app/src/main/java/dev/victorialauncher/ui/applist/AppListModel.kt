// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.ui.applist

import androidx.compose.runtime.Immutable
import dev.victorialauncher.data.AppInfo
import dev.victorialauncher.data.EntryKind
import dev.victorialauncher.data.Folder
import android.icu.text.Transliterator
import android.os.Build
import androidx.annotation.RequiresApi
import java.text.Normalizer

/** Section tags, which only have to be distinct from each other and from no tag at all. */
internal const val PRIVATE_SECTION = "private"
internal const val RECENT_SECTION = "recent"

/** The section an open folder's apps are listed in, one per folder. */
internal fun folderSection(id: String) = "folder:$id"

@Immutable
sealed interface AppListRow {
    data class Header(val text: String) : AppListRow

    /**
     * One app, and which part of the list it is in.
     *
     * [section] is null throughout the alphabet and names the section otherwise. An app shown
     * again under "Recently installed" is the same app in two places, and a list keyed by the
     * app alone then has the same key twice — which Compose refuses outright, taking the home
     * screen down with it.
     */
    data class Entry(val app: AppInfo, val section: String? = null) : AppListRow {
        val inFolder: Boolean get() = section?.startsWith("folder:") == true
    }

    /** A folder filed under its name, its apps listed right below it while [expanded]. */
    data class FolderRow(val folder: Folder, val members: List<AppInfo>, val expanded: Boolean) : AppListRow
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
/**
 * The letter an app files under, given the name being shown for it.
 *
 * Separate from [indexLetter] because the interesting part is not which letter a string starts
 * with but what to do when it starts with none. [english] is asked for only then, and only for
 * a name that came from the app: a name somebody typed is the answer even when it files under
 * '#'. Renaming an app to 4PDA and finding it still under F was this fallback handing back the
 * very name that had just been replaced.
 */
internal fun filingLetter(
    shown: String,
    renamed: Boolean,
    japanese: Boolean = false,
    english: () -> String?,
): Char {
    val own = indexLetter(shown)
    if (own != '#') return own
    // A Japanese reader looks for 天気 among the kana and kanji, not under W (#122).
    if (japanese) return japaneseHeading(shown) ?: '#'
    if (renamed) return '#'
    return english()?.let { indexLetter(it) } ?: '#'
}

/**
 * The kana row a Japanese name files under, in the order JIS X 4061 gives them after A-Z, or
 * [KANJI_HEADING] for a name that starts with a kanji.
 *
 * Kanji are not given a row: which one depends on how the word is read, and nothing on the
 * phone says that for an app's name.
 */
internal fun japaneseHeading(name: String): Char? {
    val first = name.firstOrNull() ?: return null
    if (runCatching { Character.UnicodeScript.of(first.code) }.getOrNull() == Character.UnicodeScript.HAN) {
        return KANJI_HEADING
    }
    // Half-width katakana widened and voicing marks split off, so が files with か.
    var kana = Normalizer.normalize(first.toString(), Normalizer.Form.NFKD).firstOrNull() ?: return null
    if (kana in '\u30A1'..'\u30F6') kana -= 0x60
    kana = SMALL_KANA[kana] ?: kana
    if (kana !in '\u3041'..'\u3096') return null
    return KANA_ROWS.last { it <= kana }
}

internal const val KANJI_HEADING = '漢'

private val KANA_ROWS = listOf('あ', 'か', 'さ', 'た', 'な', 'は', 'ま', 'や', 'ら', 'わ')

/** Small kana that sort below their row's first letter, mapped to their full-size form. */
private val SMALL_KANA = mapOf(
    'ぁ' to 'あ', 'ゃ' to 'や', 'ゅ' to 'ゆ', 'ょ' to 'よ', 'ゎ' to 'わ', 'ゕ' to 'か', 'ゖ' to 'け',
)

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
    /**
     * Whether this app's name was chosen rather than read from the app.
     *
     * Only consulted for a name that has no letter to file under, where it decides between
     * respecting the choice and falling back to [englishName].
     */
    renamed: (AppInfo) -> Boolean = { false },
    /** Heading for a section of the private space's own apps, or null to leave them in the A-Z. */
    privateSectionLabel: String? = null,
    /** Heading and how many, for a section of what was installed most recently. */
    recentSection: Pair<String, Int>? = null,
    /** Whether names that are not A-Z file under kana rows and kanji, as a Japanese phone sorts. */
    japanese: Boolean = false,
    /** Folders to file among the apps, or none to keep them on the home screen only. */
    folders: List<Folder> = emptyList(),
    expandedFolders: Set<String> = emptySet(),
): AppListModel {
    val all = apps.filter { it.key !in hidden }
    // The row that locks and unlocks the space goes at the very end rather than under P, where
    // nobody looking for the space thinks to look (#120).
    val padlock = all.filter { it.kind == EntryKind.PRIVATE_SPACE }
    // Lifted out of the alphabet entirely rather than listed twice. A private app and its main
    // profile twin have the same name, which is the whole reason for asking.
    val visible = all.filterNot {
        it.kind == EntryKind.PRIVATE_SPACE || (privateSectionLabel != null && it.privateProfile)
    }
    val rows = mutableListOf<AppListRow>()

    val byLetter = visible.groupBy { app ->
        filingLetter(displayName(app), renamed(app), japanese) { englishName(app) }
    }

    // Filed by the name they were given, the way a renamed app is. One whose apps are all
    // hidden or gone has nothing to open, so it is left out.
    val visibleByKey = visible.associateBy { it.key }
    val foldersByLetter = folders.mapNotNull { folder ->
        val members = folder.apps.mapNotNull { visibleByKey[it] }
        if (members.isEmpty()) null else AppListRow.FolderRow(folder, members, folder.id in expandedFolders)
    }.groupBy { filingLetter(it.folder.name, renamed = true, japanese) { null } }

    val letterIndex = mutableListOf<Pair<Char, Int>>()
    (byLetter.keys + foldersByLetter.keys).toSortedSet().forEach { letter ->
        letterIndex += letter to rows.size
        if (showHeaders) rows += AppListRow.Header(letter.toString())
        // Ahead of the letter's apps: a handful at most, and found where they always are.
        foldersByLetter[letter].orEmpty().sortedBy { it.folder.name.lowercase() }.forEach { row ->
            rows += row
            if (row.expanded) {
                row.members.sortedBy { displayName(it).lowercase() }
                    .forEach { rows += AppListRow.Entry(it, section = folderSection(row.folder.id)) }
            }
        }
        byLetter[letter].orEmpty().sortedWith(
            compareByDescending<AppInfo> { launchCounts[it.key] ?: 0 }
                .thenBy { displayName(it).lowercase() }
        ).forEach { rows += AppListRow.Entry(it) }
    }

    // Appended after the alphabet, each under its own heading. Not given a letter of their
    // own: the strip is the alphabet, and a section that is not a letter has no business
    // claiming a place in it.
    recentSection?.let { (label, limit) ->
        val recent = visible.filter { it.installedAt > 0L }
            .sortedByDescending { it.installedAt }
            .take(limit)
        if (recent.isNotEmpty()) {
            rows += AppListRow.Header(label)
            recent.forEach { rows += AppListRow.Entry(it, section = RECENT_SECTION) }
        }
    }

    // Last, so the padlock and the apps it lets out sit together at the bottom.
    val privateApps = if (privateSectionLabel == null) emptyList() else all.filter { it.privateProfile }
    if (privateApps.isNotEmpty()) rows += AppListRow.Header(privateSectionLabel!!)
    padlock.forEach { rows += AppListRow.Entry(it) }
    privateApps.sortedBy { displayName(it).lowercase() }
        .forEach { rows += AppListRow.Entry(it, section = PRIVATE_SECTION) }

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
            // A search finds apps, and each once: a folder's apps are already under their
            // own letters.
            is AppListRow.FolderRow -> Unit
            is AppListRow.Entry -> if (!row.inFolder && match(row.app)) {
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

/**
 * How well a name answers a query, lower being better.
 *
 * A plain substring match says only that the letters are in there somewhere, so typing "c"
 * matched every app with a c anywhere in it and Chess sat below Facebook and Microsoft Word.
 * The letters you have typed are almost always the start of the name you mean, then the start
 * of a word inside it, and only failing both is a match in the middle worth anything.
 */
fun searchRank(name: String, packageName: String, term: String): Int = when {
    name.startsWith(term, ignoreCase = true) -> 0
    name.split(' ', '-', '_', '.').any { it.startsWith(term, ignoreCase = true) } -> 1
    name.contains(term, ignoreCase = true) -> 2
    // Last, and deliberately so: a package name is not what anyone typed, it is only where
    // the English word survives on a phone that renamed everything.
    packageName.contains(term, ignoreCase = true) -> 3
    else -> 4
}

/**
 * The same list reordered by [rank], keeping the order it already had within each band and
 * rebuilding the letter index, which is row positions and cannot survive a reorder.
 */
fun AppListModel.rankedBy(rank: (AppInfo) -> Int): AppListModel {
    val entries = rows.filterIsInstance<AppListRow.Entry>().filterNot { it.inFolder }
    val sorted = entries.sortedBy { rank(it.app) }
    if (sorted == entries) return this
    // Headings would be wrong the moment the rows stop being in letter order, and a search
    // result is not a letter's worth of apps anyway.
    val letterIndex = sorted.mapIndexedNotNull { index, row ->
        indexLetter(row.app.label).takeIf { index == 0 || it != indexLetter(sorted[index - 1].app.label) }
            ?.let { it to index }
    }
    return AppListModel(sorted, letterIndex)
}
