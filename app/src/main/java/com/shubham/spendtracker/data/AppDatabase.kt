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
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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

    // Rows the user has added a note, tags or a split to are kept; the re-scan merges into them.
    @Query(
        "DELETE FROM transactions WHERE source = 'SMS' AND timestamp >= :since AND note IS NULL AND tags IS NULL " +
            "AND id NOT IN (SELECT txnId FROM splits)",
    )
    suspend fun deleteSmsImportsSince(since: Long)

    @Query("UPDATE transactions SET note = :note, tags = :tags WHERE id = :id")
    suspend fun updateNote(id: Long, note: String?, tags: String?)

    @Query("SELECT * FROM splits ORDER BY id")
    fun observeSplits(): Flow<List<Split>>

    @Query("SELECT * FROM splits WHERE settledAt IS NULL")
    suspend fun outstandingSplits(): List<Split>

    @Insert
    suspend fun insertSplits(splits: List<Split>)

    @Update
    suspend fun updateSplit(split: Split)

    @Query("DELETE FROM splits WHERE txnId = :txnId")
    suspend fun deleteSplitsFor(txnId: Long)

    @Query("SELECT * FROM transactions ORDER BY timestamp")
    suspend fun all(): List<Transaction>

    @Query("SELECT COUNT(*) FROM transactions WHERE timestamp = :timestamp AND amountPaise = :amountPaise AND type = :type")
    suspend fun countSame(timestamp: Long, amountPaise: Long, type: String): Int

    @Query("SELECT COALESCE(SUM(amountPaise), 0) FROM transactions WHERE type = 'DEBIT' AND category = :category AND timestamp >= :from")
    suspend fun spentInCategorySince(category: String, from: Long): Long

    @Query("UPDATE transactions SET category = :category WHERE merchant = :merchant")
    suspend fun setCategoryForMerchant(merchant: String, category: String)

    @Query("SELECT category FROM merchant_categories WHERE merchantKey = :key")
    suspend fun merchantCategory(key: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveMerchantCategory(mc: MerchantCategory)
}

@Database(entities = [Transaction::class, MerchantCategory::class, Split::class], version = 2, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun transactions(): TransactionDao

    companion object {
        /** v2: notes and tags on payments, and split expenses. SQL copied from Room's exported schema. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `transactions` ADD COLUMN `note` TEXT")
                db.execSQL("ALTER TABLE `transactions` ADD COLUMN `tags` TEXT")
                db.execSQL(SPLITS_TABLE)
                db.execSQL(SPLITS_INDEX)
            }
        }

        const val SPLITS_TABLE = "CREATE TABLE IF NOT EXISTS `splits` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
            "`txnId` INTEGER NOT NULL, `person` TEXT NOT NULL, `sharePaise` INTEGER NOT NULL, `settledAt` INTEGER)"
        const val SPLITS_INDEX = "CREATE INDEX IF NOT EXISTS `index_splits_txnId` ON `splits` (`txnId`)"

        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "spend.db")
                .addMigrations(MIGRATION_1_2)
                .build()
    }
}
