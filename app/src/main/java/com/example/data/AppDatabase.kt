package com.example.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Dao
interface LexiDao {
    @Query("SELECT * FROM affix_components")
    fun getAllComponents(): Flow<List<AffixComponent>>

    @Query("SELECT * FROM affix_components WHERE type = :type")
    fun getComponentsByType(type: String): Flow<List<AffixComponent>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertComponents(components: List<AffixComponent>)

    @Query("UPDATE affix_components SET isMastered = :isMastered, reviewCount = reviewCount + 1, lastReviewedAt = :lastReviewedAt, ebbinghausStage = :ebbinghausStage, nextReviewAt = :nextReviewAt WHERE id = :id")
    suspend fun updateComponentEbbinghaus(id: String, isMastered: Boolean, lastReviewedAt: Long, ebbinghausStage: Int, nextReviewAt: Long)

    @Query("UPDATE affix_components SET isMastered = :isMastered, reviewCount = reviewCount + 1 WHERE id = :id")
    suspend fun updateComponentMastery(id: String, isMastered: Boolean)

    @Query("SELECT * FROM word_formulas")
    fun getAllWords(): Flow<List<WordFormula>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWords(words: List<WordFormula>)

    @Query("UPDATE word_formulas SET isAssembled = :assembled, lastReviewedAt = :lastReviewedAt, ebbinghausStage = :ebbinghausStage, nextReviewAt = :nextReviewAt WHERE id = :id")
    suspend fun updateWordEbbinghaus(id: String, assembled: Boolean, lastReviewedAt: Long, ebbinghausStage: Int, nextReviewAt: Long)

    @Query("UPDATE word_formulas SET isAssembled = :assembled WHERE id = :id")
    suspend fun updateWordAssembly(id: String, assembled: Boolean)

    @Query("SELECT COUNT(*) FROM affix_components")
    suspend fun getAffixCount(): Int

    @Query("UPDATE affix_components SET isMastered = 0, reviewCount = 0, lastReviewedAt = 0, ebbinghausStage = 0, nextReviewAt = 0")
    suspend fun resetAllComponents()

    @Query("UPDATE word_formulas SET isAssembled = 0, lastReviewedAt = 0, ebbinghausStage = 0, nextReviewAt = 0")
    suspend fun resetAllWords()

    @Query("DELETE FROM word_formulas")
    suspend fun deleteAllWordFormulas()

    @Query("DELETE FROM affix_components")
    suspend fun deleteAllComponents()
}

@Database(entities = [AffixComponent::class, WordFormula::class], version = 3, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun lexiDao(): LexiDao
}
