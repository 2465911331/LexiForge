package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.BuiltInWordbook
import com.example.data.LexiRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BuiltInUpgradeTest {
    private lateinit var database: AppDatabase

    @Before fun setup() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java)
            .allowMainThreadQueries().build()
    }

    @After fun close() = database.close()

    @Test fun `upgrade removes generated words but preserves progress of corrected entries`() = runBlocking {
        val dao = database.lexiDao()
        val old = BuiltInWordbook.words.first { it.id == "structure" }.copy(
            suffixId = "suff_ion", isAssembled = true, ebbinghausStage = 3,
            lastReviewedAt = 12345L, nextReviewAt = 999999L)
        val component = BuiltInWordbook.components.first().copy(isMastered = true, reviewCount = 4, lastReviewedAt = 12345L)
        dao.insertComponents(listOf(component))
        dao.insertWords(listOf(old, old.copy(id = "commit_past", spell = "commited")))
        val repository = LexiRepository(dao)
        repository.checkAndPrepopulate(isBuiltIn = true)
        val upgraded = dao.wordSnapshot().first { it.id == old.id }
        assertEquals("suff_ure", upgraded.suffixId)
        assertEquals(old.lastReviewedAt, upgraded.lastReviewedAt)
        assertEquals(old.nextReviewAt, upgraded.nextReviewAt)
        assertEquals(old.ebbinghausStage, upgraded.ebbinghausStage)
        assertTrue(upgraded.isAssembled)
        assertEquals(18, dao.wordSnapshot().size)
        assertFalse(dao.wordSnapshot().any { it.id == "commit_past" })
        assertEquals(4, dao.componentSnapshot().first { it.id == component.id }.reviewCount)
        // Reopening the app must not reset progress a second time.
        repository.checkAndPrepopulate(isBuiltIn = true)
        assertEquals(upgraded, dao.wordSnapshot().first { it.id == old.id })
    }

    @Test fun `opening an imported book does not replace it with the built in book`() = runBlocking {
        val dao = database.lexiDao()
        val imported = BuiltInWordbook.words.first().copy(id = "custom", spell = "custom")
        dao.insertComponents(BuiltInWordbook.components)
        dao.insertWords(listOf(imported))
        LexiRepository(dao).checkAndPrepopulate(isBuiltIn = false)
        assertEquals(listOf(imported), dao.wordSnapshot())
    }
}
