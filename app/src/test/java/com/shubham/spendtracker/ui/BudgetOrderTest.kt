package com.shubham.spendtracker.ui

import com.shubham.spendtracker.parser.Categorizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class BudgetOrderTest {
    // Regression: categories with and without spend in one list used to crash the Budgets screen.
    @Test fun mixOfSpentAndUnspentCategoriesSorts() {
        val order = budgetCategoryOrder(
            budgets = mapOf("Food" to 500_000L),
            spentByCat = mapOf("Shopping" to 1_193_100L, "Other" to 4_063_100L, "Food" to 28_000L),
        )
        assertEquals(listOf("Food", "Other", "Shopping"), order.take(3))
        assertFalse("Income" in order)
        assertFalse("Self transfer" in order)
    }

    @Test fun nothingSpentNothingBudgeted() {
        assertEquals(Categorizer.all.size - 2, budgetCategoryOrder(emptyMap(), emptyMap()).size)
    }
}
