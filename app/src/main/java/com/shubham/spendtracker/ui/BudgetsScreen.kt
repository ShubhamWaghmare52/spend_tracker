package com.shubham.spendtracker.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.shubham.spendtracker.data.Budgets
import com.shubham.spendtracker.data.DailyLimit
import com.shubham.spendtracker.data.Transaction
import com.shubham.spendtracker.parser.Categorizer
import com.shubham.spendtracker.parser.TxnType

/** Monthly budget per category, with this month's spend against each. */
@Composable
fun BudgetsScreen(
    txns: List<Transaction>,
    budgets: Map<String, Long>,
    onSetBudget: (String, Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var editing by remember { mutableStateOf<String?>(null) }
    val monthStart = Budgets.startOfMonth()
    val spentByCat = remember(txns) {
        txns.filter { it.timestamp >= monthStart && it.type == TxnType.DEBIT && it.category != Categorizer.SELF }
            .groupBy { it.category }.mapValues { (_, v) -> v.sumOf { it.amountPaise } }
    }
    // Budgeted categories first, then by what's actually being spent.
    val categories = remember(budgets, spentByCat) {
        (Categorizer.all - listOf(Categorizer.INCOME, Categorizer.SELF))
            .sortedWith(compareByDescending<String> { it in budgets }.thenByDescending { spentByCat[it] ?: 0 })
    }
    val budgetedSpent = budgets.keys.sumOf { spentByCat[it] ?: 0 }
    val budgetTotal = budgets.values.sum()

    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp)) {
        item(key = "header") {
            Column(Modifier.padding(vertical = 8.dp)) {
                Text(
                    "Set a monthly budget for any category. You'll get a notification at 80% and when you go over, " +
                        "and a summary on the 1st of each month.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (budgetTotal > 0) {
                    Spacer(Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            formatRupees(budgetedSpent),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            " of ${formatRupees(budgetTotal)} budgeted this month",
                            Modifier.padding(bottom = 3.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
        }
        item(key = "list") {
            Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
                Column(Modifier.padding(vertical = 4.dp)) {
                    categories.forEachIndexed { i, cat ->
                        BudgetRow(cat, spentByCat[cat] ?: 0, budgets[cat] ?: 0) { editing = cat }
                        if (i < categories.lastIndex) {
                            HorizontalDivider(
                                Modifier.padding(start = 70.dp, end = 16.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                            )
                        }
                    }
                }
            }
        }
    }

    editing?.let { cat ->
        AmountDialog(
            "${categoryEmoji(cat)} $cat · monthly budget",
            budgets[cat] ?: 0,
            "Remove budget",
            onDismiss = { editing = null },
            onSave = { onSetBudget(cat, it); editing = null },
        )
    }
}

@Composable
private fun BudgetRow(category: String, spent: Long, budget: Long, onClick: () -> Unit) {
    val level = DailyLimit.level(spent, budget)
    val color = when (level) {
        DailyLimit.Level.OK -> categoryColor(category)
        DailyLimit.Level.NEAR -> WarnAmber
        DailyLimit.Level.OVER -> MaterialTheme.colorScheme.error
    }
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CategoryAvatar(category, 40.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(category, Modifier.weight(1f), fontWeight = FontWeight.Medium)
                Text(
                    if (budget > 0) formatRupees(budget) else "Set budget",
                    color = if (budget > 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            if (budget > 0) {
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { (spent.toFloat() / budget).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(5.dp),
                    color = color,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    strokeCap = StrokeCap.Round,
                    drawStopIndicator = {},
                )
                Spacer(Modifier.height(4.dp))
            }
            Text(
                when {
                    budget <= 0 -> "${formatRupees(spent)} spent this month"
                    level == DailyLimit.Level.OVER -> "${formatRupees(spent)} spent · ${formatRupees(spent - budget)} over"
                    else -> "${formatRupees(spent)} spent · ${formatRupees(budget - spent)} left"
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (budget > 0 && level != DailyLimit.Level.OK) color else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
