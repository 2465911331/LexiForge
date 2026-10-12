package com.example.data

/** Stage 0 is new/forgotten; stage 1 is the first successful recall. */
object ReviewScheduler {
    private val intervalsMinutes = longArrayOf(5, 30, 720, 1440, 2880, 5760, 10080, 21600)

    fun nextStage(current: Int, correct: Boolean): Int =
        if (correct) (current + 1).coerceIn(1, intervalsMinutes.size) else 0

    fun nextReviewAt(stage: Int, now: Long): Long =
        now + intervalsMinutes[(stage - 1).coerceIn(0, intervalsMinutes.lastIndex)] * 60_000L

    fun isDue(lastReviewedAt: Long, nextReviewAt: Long, now: Long): Boolean =
        // Older versions sometimes reset the deadline to zero without clearing lastReviewedAt.
        // Treat those learned records as due rather than stranding them outside both queues.
        lastReviewedAt > 0 && nextReviewAt <= now

    fun canAdvance(lastReviewedAt: Long, nextReviewAt: Long, now: Long): Boolean =
        lastReviewedAt == 0L || isDue(lastReviewedAt, nextReviewAt, now)

    fun words(words: List<WordFormula>, now: Long, dueOnly: Boolean): List<WordFormula> =
        words.filter { isDue(it.lastReviewedAt, it.nextReviewAt, now) || (!dueOnly && it.lastReviewedAt == 0L) }
            .sortedBy { if (it.lastReviewedAt == 0L) Long.MAX_VALUE else it.nextReviewAt }

    fun components(components: List<AffixComponent>, now: Long, dueOnly: Boolean): List<AffixComponent> =
        components.filter { isDue(it.lastReviewedAt, it.nextReviewAt, now) || (!dueOnly && it.lastReviewedAt == 0L) }
            .sortedBy { if (it.lastReviewedAt == 0L) Long.MAX_VALUE else it.nextReviewAt }
}
