// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.ui

import java.util.concurrent.atomic.AtomicLong

/**
 * The order imposed on the several things that reload the app list, so that the pass which
 * read the device most recently is the one whose answer stays on screen.
 *
 * A reload is a full enumeration — every profile, every launchable activity, the icon cache
 * thrown away and rebuilt — and it is started independently by a package callback, a profile
 * broadcast, the launcher coming back into view and the padlock itself. Several run at once
 * and they finish in whatever order the system gets round to.
 *
 * Left unordered that is a disclosure rather than a flicker. A reload that read an open space
 * and is still enumerating when the space locks finishes afterwards and publishes what it
 * built: every private app back on screen, beside an open padlock, with the space shut. And
 * nothing re-reads on its own — the launcher has to be left and come back before anything
 * corrects it, which can be the rest of the day.
 *
 * So a pass takes a number before it reads anything, and may only publish while that number is
 * still the newest. Taking a number is also what invalidates the passes already running, which
 * is why it is taken at the top rather than at the point of publishing: a pass that has just
 * decided to go and look is more current than one that looked earlier, whatever order they
 * finish in.
 *
 * Nothing is left stale by this. A number is only taken by a pass that goes on to publish, so
 * whichever pass holds the newest one always finishes and publishes; a pass that is dropped
 * was superseded by one putting its own answer on screen.
 *
 * Atomic because a pass begins on the main thread and does its reading on another, and the
 * comparison at the end has to see what a newer pass wrote.
 */
class ReloadOrder {

    private val latest = AtomicLong(0L)

    /** Starts a pass, superseding every pass already running. */
    fun begin(): Long = latest.incrementAndGet()

    /** Whether the pass that took [generation] is still the newest one to have started. */
    fun mayPublish(generation: Long): Boolean = latest.get() == generation
}
