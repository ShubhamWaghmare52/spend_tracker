package com.shubham.spendtracker.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.shubham.spendtracker.parser.TxnSource
import com.shubham.spendtracker.parser.TxnType

@Entity(tableName = "transactions", indices = [Index("timestamp"), Index("upiRef")])
data class Transaction(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amountPaise: Long,
    val type: TxnType,
    val merchant: String?,
    val accountLast4: String?,
    val source: TxnSource,
    val sourceApp: String,
    val timestamp: Long,
    val upiRef: String?,
    val category: String,
    val rawText: String,
    /** The user's own note, e.g. "dinner with college friends". */
    val note: String? = null,
    /** Comma-separated tags, e.g. "Goa trip,Work". */
    val tags: String? = null,
) {
    val tagList: List<String> get() = Tags.parse(tags)
}

/** Category the user picked for a merchant; applied to that merchant's future payments. */
@Entity(tableName = "merchant_categories")
data class MerchantCategory(
    @PrimaryKey val merchantKey: String,
    val category: String,
)

/**
 * One friend's share of a payment the user made for a group. Settled when the friend pays
 * back (matched automatically from an incoming UPI credit, or marked by hand).
 */
@Entity(tableName = "splits", indices = [Index("txnId")])
data class Split(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val txnId: Long,
    val person: String,
    val sharePaise: Long,
    /** When it was paid back; null while outstanding. Stored as a time, not a row id, so re-imports can't break it. */
    val settledAt: Long? = null,
)

object Tags {
    fun parse(raw: String?): List<String> =
        raw.orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() }
            .distinctBy { it.lowercase() }

    fun join(tags: List<String>): String? = parse(tags.joinToString(",")).joinToString(",").ifEmpty { null }
}
