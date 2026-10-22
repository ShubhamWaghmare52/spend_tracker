package com.shubham.spendtracker.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class MonogramTest {
    @Test fun initials() {
        assertEquals("SW", monogram("SWIGGY"))
        assertEquals("RK", monogram("RAHUL K SHARMA"))
        assertEquals("RK", monogram("rahul.k@okaxis"))
        assertEquals("CH", monogram("CHAITANYA"))
        assertEquals("SP", monogram("SPOTIFY* PREMIUM")) // punctuation is ignored
        assertEquals("Q", monogram("q789639357@ybl"))
        assertEquals("9", monogram("9876543210@ybl"))
    }

    @Test fun samePayeeSameColourWhateverTheFormatting() {
        assertEquals(monogramSlot("Swiggy", 12), monogramSlot("SWIGGY", 12))
        assertEquals(monogramSlot("Big Basket", 12), monogramSlot("bigbasket", 12))
    }

    @Test fun shortAmountsRoundToWholeRupees() {
        assertEquals("₹2,758", formatRupeesShort(275807))
        assertEquals("₹1,440", formatRupeesShort(143950))
        assertEquals("₹0", formatRupeesShort(0))
    }
}
