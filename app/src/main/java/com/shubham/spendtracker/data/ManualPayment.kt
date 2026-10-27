package com.shubham.spendtracker.data

import com.shubham.spendtracker.parser.TxnType

/** A payment the user typed in because no SMS or notification announced it. */
data class ManualPayment(
    val type: TxnType,
    val amountPaise: Long,
    val name: String?,
    val timestamp: Long,
    /** The bank ("Bank of Baroda") and account the money moved through, when the user picked one. */
    val bank: String? = null,
    val last4: String? = null,
) {
    companion object {
        /** Source label for manual payments not tied to an account. */
        const val SOURCE = "Added by you"
        const val RAW_TEXT = "Added by you"
    }
}
