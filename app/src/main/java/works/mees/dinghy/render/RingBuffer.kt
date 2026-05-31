package works.mees.dinghy.render

/**
 * A bounded, rolling window of [Float] samples — the toolkit-agnostic data substrate both render
 * primitives (live temp graph, console sparkline) draw from (D-12). It is HEADLESS: a plain Kotlin
 * class with NO Compose annotations (the [works.mees.dinghy.state.PrinterState] discipline), so it
 * survives View recreation / rotation / theme swap because it is plain state, not a View. It is fed
 * by the throttled `StateFlow` in a later plan; here it is just the formalized version of the
 * `ArrayDeque` + `GRAPH_MAX = 120` bounded-window idiom already used in the bench harness.
 *
 * Semantics:
 * - [push] appends the newest value and evicts the oldest once [size] would exceed [capacity].
 * - [snapshot] returns a STABLE defensive `FloatArray` copy in oldest→newest order. The draw side
 *   reuses ITS own arrays per frame (Pitfall 4); the buffer never hands out its backing store, so a
 *   later [push] can never mutate an already-returned snapshot.
 *
 * Thread-safety: the throttled flow feeds [push] from one coroutine while the View reads [snapshot]
 * on the main thread, so both are `@Synchronized` on this instance. Readers therefore always see a
 * consistent, non-torn array bounded by [capacity].
 *
 * @param capacity maximum retained samples; defaults to the bench's GRAPH_MAX = 120 precedent.
 */
class RingBuffer(val capacity: Int = DEFAULT_CAPACITY) {

    init {
        require(capacity > 0) { "RingBuffer capacity must be > 0, was $capacity" }
    }

    /** Backing ring storage; [head] is the index of the oldest element when [count] == [capacity]. */
    private val data = FloatArray(capacity)
    private var head = 0
    private var count = 0

    /** Number of samples currently retained (0..[capacity]). */
    val size: Int
        @Synchronized get() = count

    /**
     * Append [value] as the newest sample, evicting the oldest once the window is full.
     */
    @Synchronized
    fun push(value: Float) {
        if (count < capacity) {
            data[(head + count) % capacity] = value
            count++
        } else {
            // Full: overwrite the oldest slot and advance head — O(1), no allocation.
            data[head] = value
            head = (head + 1) % capacity
        }
    }

    /**
     * A stable, defensive copy of the retained samples in oldest→newest order. Mutating the buffer
     * after this call does not alter the returned array.
     */
    @Synchronized
    fun snapshot(): FloatArray {
        val out = FloatArray(count)
        for (i in 0 until count) {
            out[i] = data[(head + i) % capacity]
        }
        return out
    }

    /** Drop all retained samples (window resets to empty). */
    @Synchronized
    fun clear() {
        head = 0
        count = 0
    }

    companion object {
        /** Bench `GRAPH_MAX` precedent — samples kept on screen for the rolling temp graph. */
        const val DEFAULT_CAPACITY = 120
    }
}
