package com.shubham.spendtracker.insights

import com.shubham.spendtracker.data.Transaction
import com.shubham.spendtracker.parser.Categorizer
import com.shubham.spendtracker.parser.TxnType
import java.time.Instant
import java.time.ZoneId

/** Why a payment looks out of character, judged against the user's own history. */
sealed interface Unusual {
    /** Several times bigger than what the user normally pays this merchant. */
    data class BigForMerchant(val typicalPaise: Long, val times: Int) : Unusual

    /** A large first-ever payment to this payee. */
    data object LargeToNewPayee : Unusual

    /** A sizeable payment in the small hours. */
    data class LateNight(val hour: Int) : Unusual
}

object UnusualPayments {
    private const val MIN_BIG_PAISE = 1_000_00L
    private const val NEW_PAYEE_PAISE = 5_000_00L
    private const val LATE_NIGHT_PAISE = 2_000_00L
    private val LATE_HOURS = 0..4

    fun check(new: Transaction, history: List<Transaction>, zone: ZoneId = ZoneId.systemDefault()): Unusual? {
        if (new.type != TxnType.DEBIT || new.category == Categorizer.SELF) return null
        val key = Categorizer.merchantKey(new.merchant)
        val before = history.filter {
            it.id != new.id && it.timestamp < new.timestamp && it.type == TxnType.DEBIT && it.category != Categorizer.SELF
        }

        if (key != null) {
            val sameMerchant = before.filter { Categorizer.merchantKey(it.merchant) == key }.map { it.amountPaise }.sorted()
            if (sameMerchant.size >= 3) {
                val typical = sameMerchant[sameMerchant.size / 2]
                if (new.amountPaise >= MIN_BIG_PAISE && new.amountPaise >= typical * 3 && new.amountPaise - typical >= MIN_BIG_PAISE) {
                    return Unusual.BigForMerchant(typical, (new.amountPaise / typical.coerceAtLeast(1)).toInt())
                }
            } else if (sameMerchant.isEmpty() && new.amountPaise >= NEW_PAYEE_PAISE) {
                return Unusual.LargeToNewPayee
            }
        }

        val hour = Instant.ofEpochMilli(new.timestamp).atZone(zone).hour
        if (hour in LATE_HOURS && new.amountPaise >= LATE_NIGHT_PAISE) return Unusual.LateNight(hour)
        return null
    }
}
