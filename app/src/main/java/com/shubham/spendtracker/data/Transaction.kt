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
)

/** Category the user picked for a merchant; applied to that merchant's future payments. */
@Entity(tableName = "merchant_categories")
data class MerchantCategory(
    @PrimaryKey val merchantKey: String,
    val category: String,
)
