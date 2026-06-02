package works.mees.dinghy.state

import works.mees.dinghy.ui.console.ConsoleLine

/**
 * A bounded, object-typed rolling window of [ConsoleLine] — the console scrollback backing store
 * (CONS-02 / D-02). It is the OBJECT-TYPED analog of `render/RingBuffer.kt`: same `@Synchronized`
 * O(1)-eviction + defensive-snapshot idiom, but it holds `ConsoleLine` objects, NOT `Float` samples.
 *
 * RESEARCH Pitfall 1 (LOAD-BEARING): `render/RingBuffer.kt` is `FloatArray`-backed and physically
 * cannot hold a `ConsoleLine`; do NOT shoehorn console lines into it. This is a fresh, build-from-
 * scratch ring (the simplest correct form per RESEARCH — a capped [ArrayDeque] with `removeFirst()`
 * over cap). It is HEADLESS: a plain Kotlin class with NO Compose annotations and NO I/O, so it
 * survives View recreation / rotation / theme swap because it is plain state, not a View.
 *
 * The default [capacity] of 1000 aligns 1:1 with Moonraker's `gcode_store_size` default (RESEARCH
 * §1), so a full `server.gcode_store` backfill maps cleanly onto the ring without truncation surprise.
 *
 * Thread-safety: a live-line producer coroutine feeds [push] / [replaceAll] while the View reads
 * [snapshot] on the main thread, so every mutator and reader is `@Synchronized` on this instance —
 * readers always see a consistent, non-torn list bounded by [capacity].
 *
 * @param capacity maximum retained lines (must be > 0); defaults to 1000 (Moonraker `gcode_store_size`).
 */
class ConsoleScrollback(val capacity: Int = DEFAULT_CAPACITY) {

    init {
        require(capacity > 0) { "ConsoleScrollback capacity must be > 0, was $capacity" }
    }

    /** Backing store; the head (index 0) is the oldest line, the tail is the newest. */
    private val data = ArrayDeque<ConsoleLine>(minOf(capacity, INITIAL_ALLOC))

    /** Number of lines currently retained (0..[capacity]). */
    val size: Int
        @Synchronized get() = data.size

    /**
     * Append [line] as the newest entry, evicting the oldest once the window is full. O(1) amortized.
     */
    @Synchronized
    fun push(line: ConsoleLine) {
        data.addLast(line)
        if (data.size > capacity) {
            data.removeFirst()
        }
    }

    /**
     * Atomically clear and refill from [lines], keeping only the NEWEST [capacity] entries when
     * [lines] is larger than the cap. Used for the (re)connect `server.gcode_store` backfill REPLACE
     * (D-02 / Mainsail-parity Option A) — the snapshot is authoritative and supersedes prior content.
     */
    @Synchronized
    fun replaceAll(lines: List<ConsoleLine>) {
        data.clear()
        val start = if (lines.size > capacity) lines.size - capacity else 0
        for (i in start until lines.size) {
            data.addLast(lines[i])
        }
    }

    /**
     * A stable, defensive copy of the retained lines in oldest→newest order. Mutating the returned
     * list (or the ring after this call) does not alter the other — the ring never hands out its
     * backing store.
     */
    @Synchronized
    fun snapshot(): List<ConsoleLine> = ArrayList(data)

    /** Drop all retained lines (window resets to empty). */
    @Synchronized
    fun clear() {
        data.clear()
    }

    companion object {
        /** Aligns 1:1 with Moonraker's `gcode_store_size` default (RESEARCH §1). */
        const val DEFAULT_CAPACITY = 1000

        /** Cap the initial allocation so a huge [capacity] doesn't pre-allocate aggressively. */
        private const val INITIAL_ALLOC = 256
    }
}
