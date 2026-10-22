package com.shubham.spendtracker.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.shubham.spendtracker.data.Transaction
import com.shubham.spendtracker.parser.Categorizer
import com.shubham.spendtracker.parser.TxnType

private data class MerchantGroup(val merchant: String?, val txns: List<Transaction>) {
    val total = txns.sumOf { it.amountPaise }
    val key get() = merchant ?: ""
}

private fun uncategorised(txns: List<Transaction>) =
    txns.filter { it.type == TxnType.DEBIT && it.category == Categorizer.OTHER }
        .groupBy { it.merchant }
        .map { (m, list) -> MerchantGroup(m, list) }
        .sortedByDescending { it.total }

/** Spending still in "Other", for the Overview's "Sort" shortcut. */
fun uncategorisedCount(txns: List<Transaction>) = txns.count { it.type == TxnType.DEBIT && it.category == Categorizer.OTHER }

/**
 * Walks through payments still in "Other", biggest merchants first. One tap categorises
 * every payment to that merchant, now and in future.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SortScreen(txns: List<Transaction>, onSetCategory: (Transaction, String) -> Unit, modifier: Modifier = Modifier) {
    val groups = remember(txns) { uncategorised(txns) }
    // Progress is measured against what was unsorted when the screen opened.
    val startTotal = remember { groups.sumOf { it.total }.coerceAtLeast(1) }
    val remaining = groups.sumOf { it.total }
    val choices = remember { Categorizer.all - listOf(Categorizer.OTHER, Categorizer.INCOME) }

    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp)) {
        item(key = "header") {
            Column(Modifier.padding(vertical = 8.dp)) {
                Text(
                    "Pick a category once. Every payment to that merchant follows, now and in future.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        "${formatRupeesShort((startTotal - remaining).coerceAtLeast(0))} sorted",
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "${formatRupeesShort(remaining)} left · ${groups.size} merchants",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { 1f - remaining.toFloat() / startTotal },
                    modifier = Modifier.fillMaxWidth().height(8.dp),
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    strokeCap = StrokeCap.Round,
                    drawStopIndicator = {},
                )
                Spacer(Modifier.height(8.dp))
            }
        }

        if (groups.isEmpty()) {
            item(key = "done") {
                Column(Modifier.fillMaxWidth().padding(vertical = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🎉", style = MaterialTheme.typography.displayMedium)
                    Spacer(Modifier.height(8.dp))
                    Text("All sorted", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Every payment has a category. New ones from known merchants are sorted automatically.",
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        items(groups, key = { it.key }) { g ->
            val person = Categorizer.looksLikePerson(g.merchant)
            // People are usually transfers; put that first so it's one obvious tap.
            val ordered = if (person) listOf(Categorizer.TRANSFERS) + (choices - Categorizer.TRANSFERS) else choices
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.animateItem().padding(vertical = 6.dp),
            ) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CategoryAvatar(if (person) Categorizer.TRANSFERS else Categorizer.OTHER)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                g.merchant ?: "No merchant name",
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                "${g.txns.size} ${if (g.txns.size == 1) "payment" else "payments"} · last ${relativeDay(g.txns.maxOf { it.timestamp })}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(formatRupeesShort(g.total), fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(Modifier.height(12.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        ordered.forEachIndexed { i, cat ->
                            val suggested = person && i == 0
                            FilterChip(
                                selected = suggested,
                                onClick = {
                                    // A merchant group updates all its payments in one go; unnamed ones go one by one.
                                    if (g.merchant != null) onSetCategory(g.txns.first(), cat)
                                    else g.txns.forEach { onSetCategory(it, cat) }
                                },
                                label = { Text("${categoryEmoji(cat)} $cat") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                ),
                            )
                        }
                    }
                }
            }
        }
    }
}
