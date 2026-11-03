package com.shubham.spendtracker.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BankLabelTest {
    @Test fun namesBanksFromTheirSenderIds() {
        assertEquals("India Post Payments Bank", bankName("AX-IPPBMB-S"))
        assertEquals("HDFC Bank", bankName("AD-HDFCBK-S"))
        assertEquals("Bank of Baroda", bankName("JK-BOBSMS-S + Google Pay"))
    }

    @Test fun labelsJoinBankAndApp() {
        assertEquals("India Post Payments Bank · Google Pay", sourceLabel("AX-IPPBMB-S + Google Pay"))
    }

    @Test fun appOnlySourcesHaveNoBank() {
        assertNull(bankName("Google Pay"))
    }
}
