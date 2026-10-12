package com.example

import com.example.data.BuiltInWordbook
import com.example.data.ReviewScheduler
import com.example.data.reviewClock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReviewSchedulerTest {
    private val now = 1_800_000_000_000L

    @Test fun `first success is due in five minutes then thirty minutes`() {
        val first = ReviewScheduler.nextStage(0, true)
        assertEquals(now + 5 * 60_000L, ReviewScheduler.nextReviewAt(first, now))
        val second = ReviewScheduler.nextStage(first, true)
        assertEquals(now + 30 * 60_000L, ReviewScheduler.nextReviewAt(second, now))
    }

    @Test fun `all later intervals and maximum stage remain bounded`() {
        val minutes = listOf(5, 30, 720, 1440, 2880, 5760, 10080, 21600)
        minutes.forEachIndexed { index, expected ->
            assertEquals(now + expected * 60_000L, ReviewScheduler.nextReviewAt(index + 1, now))
        }
        assertEquals(8, ReviewScheduler.nextStage(8, true))
    }

    @Test fun `forgotten word at stage zero returns in five minutes on the same day`() {
        val stage = ReviewScheduler.nextStage(6, false)
        val due = ReviewScheduler.nextReviewAt(stage, now)
        val word = BuiltInWordbook.words.first().copy(lastReviewedAt = now, nextReviewAt = due, ebbinghausStage = stage)
        assertTrue(ReviewScheduler.words(listOf(word), due - 1, true).isEmpty())
        assertEquals(listOf(word), ReviewScheduler.words(listOf(word), due, true))
    }

    @Test fun `no due words means an empty review queue not all learned words`() {
        val fresh = BuiltInWordbook.words.first()
        val future = fresh.copy(id = "future", lastReviewedAt = now, ebbinghausStage = 1, nextReviewAt = now + 300_000)
        assertTrue(ReviewScheduler.words(listOf(fresh, future), now, true).isEmpty())
        assertEquals(listOf(fresh), ReviewScheduler.words(listOf(fresh, future), now, false))
    }

    @Test fun `legacy zero deadlines and overdue words precede new words`() {
        val fresh = BuiltInWordbook.words.first()
        val due = fresh.copy(id = "due", lastReviewedAt = now - 900_000, nextReviewAt = now - 600_000)
        val unscheduled = fresh.copy(id = "unscheduled", lastReviewedAt = now, nextReviewAt = 0)
        assertEquals(listOf(unscheduled, due, fresh), ReviewScheduler.words(listOf(fresh, unscheduled, due), now, false))
    }

    @Test fun `components use their own deadlines even when anchor word was studied`() {
        val component = BuiltInWordbook.components.first().copy(lastReviewedAt = now, nextReviewAt = now + 300_000, ebbinghausStage = 0)
        assertTrue(ReviewScheduler.components(listOf(component), now, true).isEmpty())
        assertEquals(listOf(component), ReviewScheduler.components(listOf(component), now + 300_000, true))
    }

    @Test fun `free practice before the deadline cannot inflate the review stage`() {
        assertTrue(ReviewScheduler.canAdvance(0, 0, now))
        assertFalse(ReviewScheduler.canAdvance(now, now + 300_000, now + 1))
        assertTrue(ReviewScheduler.canAdvance(now, now + 300_000, now + 300_000))
    }

    @Test fun `clock makes due items appear without any database emission`() = runTest {
        val word = BuiltInWordbook.words.first().copy(lastReviewedAt = now - 300_000, nextReviewAt = now + 1_000)
        val emissions = combine(flowOf(listOf(word)), reviewClock { now + testScheduler.currentTime }) { words, time ->
            ReviewScheduler.words(words, time, true)
        }.take(2).toList()
        assertTrue(emissions[0].isEmpty())
        assertEquals(listOf(word), emissions[1])
    }
}
