package com.shubham.spendtracker.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<Transaction>>

    @Query("SELECT * FROM transactions WHERE timestamp BETWEEN :from AND :to OR (upiRef IS NOT NULL AND upiRef = :upiRef)")
    suspend fun candidates(from: Long, to: Long, upiRef: String?): List<Transaction>

    @Query("SELECT * FROM transactions WHERE upiRef = :upiRef")
    suspend fun byUpiRef(upiRef: String): List<Transaction>

    @Insert
    suspend fun insert(txn: Transaction): Long

    @Update
    suspend fun update(txn: Transaction)

    @Delete
    suspend fun delete(txn: Transaction)

    @Query("SELECT COALESCE(SUM(amountPaise), 0) FROM transactions WHERE type = 'DEBIT' AND category != :excluded AND timestamp >= :from")
    suspend fun spentSince(from: Long, excluded: String): Long

    @Query("DELETE FROM transactions WHERE source = 'SMS'")
    suspend fun deleteSmsImports()

    @Query("UPDATE transactions SET category = :category WHERE merchant = :merchant")
    suspend fun setCategoryForMerchant(merchant: String, category: String)

    @Query("SELECT category FROM merchant_categories WHERE merchantKey = :key")
    suspend fun merchantCategory(key: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveMerchantCategory(mc: MerchantCategory)
}

@Database(entities = [Transaction::class, MerchantCategory::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun transactions(): TransactionDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "spend.db").build()
    }
}
