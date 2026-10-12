package com.example

import com.example.data.*
import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class DailyPlannerTest {
    private val now = 1_800_000_000_000L
    private val window = DayWindow(now - 10_000, now + 10_000)
    private fun word(id: String, last: Long = 0, next: Long = 0) = WordFormula(id, id, null, null, null,
        null, null, null, "", "释义", "", "", lastReviewedAt = last, nextReviewAt = next)
    private fun event(id: String, first: Boolean?, correct: Boolean = true, n: Long = 1) = ReviewEvent(
        "e$n", "s", "built-in", id, id, "WORD", "SPELLING", "释义", id, correct, "", n.toInt(),
        now, 1000, 0, if (correct) 1 else 0, true, sequence = n, firstLearning = first, dueReview = first == false)

    @Test fun `new quota is bounded and due words precede new words`() {
        val words = listOf(word("c"), word("a"), word("b"), word("due", now - 100, now), word("later", now - 100, now + 1))
        val plan = DailyPlanner.calculate(DailyInputs(target = 2, words = words), now, window)
        assertEquals(listOf("due", "a", "b"), plan.queue.map { it.id })
        assertEquals(2, plan.todayGoal)
        assertEquals(1, plan.dueCount)
    }

    @Test fun `wrong new answer reserves a slot until corrected and retries count once`() {
        val words = listOf(word("a", now, now + 300000), word("b"), word("c"))
        val wrong = event("a", true, false)
        var input = DailyInputs(target = 2, words = words, events = listOf(wrong))
        var plan = DailyPlanner.calculate(input, now, window)
        assertEquals(0, plan.newDone)
        assertEquals(1, plan.newStarted)
        assertEquals(listOf("a", "b"), plan.queue.map { it.id })
        input = input.copy(events = listOf(wrong, event("a", false, n = 2).copy(scheduleChanged = false, retryOf = wrong.id), event("a", false, n = 3)))
        plan = DailyPlanner.calculate(input, now, window)
        assertEquals(1, plan.newDone)
        assertEquals(0, plan.reviewed)
        assertEquals(listOf("b"), plan.queue.map { it.id })
    }

    @Test fun `lowering target preserves started unfinished words and raising adds capacity`() {
        val input = DailyInputs(target = 0, words = listOf(word("a", now, now + 1), word("b")), events = listOf(event("a", true, false)))
        assertEquals(listOf("a"), DailyPlanner.calculate(input, now, window).queue.map { it.id })
        assertEquals(1, DailyPlanner.calculate(input, now, window).todayGoal)
        assertEquals(2, DailyPlanner.calculate(input.copy(target = 20), now, window).todayGoal)
    }

    @Test fun `zero means review only and insufficient words shrink today goal`() {
        val words = listOf(word("a"), word("due", now - 1, now))
        assertEquals(listOf("due"), DailyPlanner.calculate(DailyInputs(target = 0, words = words), now, window).queue.map { it.id })
        assertEquals(1, DailyPlanner.calculate(DailyInputs(target = 20, words = words), now, window).todayGoal)
    }

    @Test fun `other books cards future events and old unknown events do not add new progress`() {
        val events = listOf(event("a", true).copy(bookId = "other"), event("a", null).copy(targetKind = "COMPONENT"),
            event("a", true).copy(answeredAt = now + 1), event("a", null), event("a", true).copy(answeredAt = window.start - 1))
        val plan = DailyPlanner.calculate(DailyInputs(words = listOf(word("a")), events = events), now, window)
        assertEquals(0, plan.newDone)
        assertEquals(0, plan.reviewed)
    }

    @Test fun `midnight clears daily progress without clearing word progress`() {
        val input = DailyInputs(target = 1, words = listOf(word("a", now, now + 300000), word("b")), events = listOf(event("a", true)))
        assertEquals(1, DailyPlanner.calculate(input, now, window).newDone)
        val tomorrow = DailyPlanner.calculate(input, window.end, DayWindow(window.end, window.end + 86400000))
        assertEquals(0, tomorrow.newDone)
        assertEquals(listOf("b"), tomorrow.queue.map { it.id })
    }

    @Test fun `review stats count distinct due successes not early practice and due repeats remain actionable`() {
        val events = listOf(event("a", false), event("a", false, n = 2), event("b", false).copy(scheduleChanged = false, dueReview = false))
        val input = DailyInputs(target = 0, words = listOf(word("a", now - 1, now), word("b", now, now + 1)), events = events)
        val plan = DailyPlanner.calculate(input, now, window)
        assertEquals(1, plan.reviewed)
        assertEquals(listOf("a"), plan.queue.map { it.id })
    }

    @Test fun `local calendar respects daylight saving and timezone changes`() {
        val zone = TimeZone.getTimeZone("America/New_York")
        val c = Calendar.getInstance(zone).apply { clear(); set(2026, Calendar.MARCH, 8, 12, 0) }
        val day = DailyPlanner.day(c.timeInMillis, zone)
        assertEquals(23 * 3600000L, day.end - day.start)
        assertNotEquals(day, DailyPlanner.day(c.timeInMillis, TimeZone.getTimeZone("Asia/Shanghai")))
        assertEquals(day.end, DailyPlanner.day(day.end, zone).start)
    }

    @Test fun `early wrong correction does not count as due review and earlier success cannot complete a later new attempt`() {
        val wrong = event("a", false, false).copy(dueReview = false)
        val correction = event("a", false, n = 2).copy(dueReview = false, scheduleChanged = false, retryOf = wrong.id)
        val input = DailyInputs(words = listOf(word("a", now, now + 1)), events = listOf(wrong, correction))
        assertEquals(0, DailyPlanner.calculate(input, now, window).reviewed)
        val dueWrong = wrong.copy(dueReview = true)
        assertEquals(1, DailyPlanner.calculate(input.copy(events = listOf(dueWrong, correction)), now, window).reviewed)
        val reset = input.copy(events = listOf(correction, event("a", true, false, n = 3)))
        assertEquals(0, DailyPlanner.calculate(reset, now, window).newDone)
    }
}
