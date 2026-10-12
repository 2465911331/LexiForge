package com.example.data

import java.util.Calendar
import java.util.TimeZone

data class DayWindow(val start: Long, val end: Long)
data class DailyInputs(val bookId: String = "built-in", val target: Int = 20,
    val words: List<WordFormula> = emptyList(), val events: List<ReviewEvent> = emptyList())
data class DailyPlan(val bookId: String = "built-in", val target: Int = 20,
    val todayGoal: Int = 0, val newDone: Int = 0, val newStarted: Int = 0,
    val reviewed: Int = 0, val dueCount: Int = 0, val queue: List<WordFormula> = emptyList())

object DailyPlanner {
    // Calendar supports API 24 and local days that are not exactly 24 hours (DST).
    fun day(now: Long, zone: TimeZone = TimeZone.getDefault()): DayWindow {
        val c = Calendar.getInstance(zone).apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val start = c.timeInMillis
        c.add(Calendar.DAY_OF_MONTH, 1)
        return DayWindow(start, c.timeInMillis)
    }

    fun calculate(input: DailyInputs, now: Long, window: DayWindow = day(now)): DailyPlan {
        val words = input.words.filter { it.bookId == input.bookId }
        val ids = words.map { it.id }.toSet()
        val events = input.events.filter { it.bookId == input.bookId && it.targetKind == "WORD" &&
            it.targetId in ids && it.answeredAt >= window.start && it.answeredAt < window.end && it.answeredAt <= now }
        val firstStarts = events.filter { it.firstLearning == true }.groupBy { it.targetId }.mapValues { (_, e) -> e.minOf { it.sequence } }
        val started = firstStarts.keys
        val done = events.filter { it.correct && it.sequence >= (firstStarts[it.targetId] ?: Long.MAX_VALUE) }.map { it.targetId }.toSet()
        val unseen = words.filter { it.lastReviewedAt == 0L && it.id !in started }.sortedBy { it.id }
        val target = input.target.coerceIn(0, 200)
        val newSlots = (target - started.size).coerceAtLeast(0)
        val pending = words.filter { it.id in started && it.id !in done }.sortedBy { it.id }
        val due = ReviewScheduler.words(words, now, true)
        val dueStarts = events.filter { it.dueReview == true }.groupBy { it.targetId }.mapValues { (_, e) -> e.minOf { it.sequence } }
        val reviewed = events.filter { it.correct && it.targetId !in started &&
            it.sequence >= (dueStarts[it.targetId] ?: Long.MAX_VALUE) }.map { it.targetId }.toSet().size
        return DailyPlan(input.bookId, target, started.size + minOf(newSlots, unseen.size), done.size,
            started.size, reviewed, due.size, (due + pending + unseen.take(newSlots)).distinctBy { it.id })
    }
}
