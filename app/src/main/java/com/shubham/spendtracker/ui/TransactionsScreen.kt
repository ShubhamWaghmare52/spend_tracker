package com.shubham.spendtracker.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.shubham.spendtracker.data.Split
import com.shubham.spendtracker.data.Transaction
import com.shubham.spendtracker.parser.Categorizer
import com.shubham.spendtracker.parser.TxnType

private const val ALL = "All"
private const val SPENT = "Spent"
private const val RECEIVED = "Received"

@Composable
fun TransactionsScreen(
    txns: List<Transaction>,
    splits: List<Split>,
    actions: TxnActions,
    modifier: Modifier = Modifier,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(ALL) }
    var detailId by remember { mutableStateOf<Long?>(null) }

    val categories = remember(txns) {
        txns.groupingBy { it.category }.eachCount().entries.sortedByDescending { it.value }.map { it.key }
    }
    // Tag filters are stored as "#tag" so they can't clash with a category name.
    val tagFilters = remember(txns) { txns.flatMap { it.tagList }.distinctBy { it.lowercase() }.sorted().map { "#$it" } }
    val filtered = remember(txns, query, filter) {
        val q = query.trim()
        txns.filter { t ->
            val passesFilter = when (filter) {
                ALL -> true
                SPENT -> t.type == TxnType.DEBIT
                RECEIVED -> t.type == TxnType.CREDIT
                else -> if (filter.startsWith("#")) t.tagList.any { it.equals(filter.drop(1), ignoreCase = true) } else t.category == filter
            }
            passesFilter && (q.isEmpty() || listOfNotNull(t.merchant, t.category, sourceLabel(t.sourceApp), t.upiRef, t.note, t.tags)
                .any { it.contains(q, ignoreCase = true) })
        }
    }
    // Grouped by the actual date (not its label), so the same day in different years stays apart.
    val grouped = remember(filtered) {
        filtered.groupBy { java.time.Instant.ofEpochMilli(it.timestamp).atZone(java.time.ZoneId.systemDefault()).toLocalDate() }
    }
    val spent = filtered.filter { it.type == TxnType.DEBIT && it.category != Categorizer.SELF }
    val received = filtered.filter { it.type == TxnType.CREDIT && it.category != Categorizer.SELF }

    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item(key = "search") {
            TextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search merchant, note, tag, bank or ref") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Close, contentDescription = "Clear") }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
        item(key = "filters") {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(vertical = 8.dp),
            ) {
                items(listOf(ALL, SPENT, RECEIVED) + tagFilters + categories) { f ->
                    FilterChip(
                        selected = f == filter,
                        onClick = { filter = if (f == filter) ALL else f },
                        label = {
                            Text(
                                when {
                                    f in listOf(ALL, SPENT, RECEIVED) || f.startsWith("#") -> f
                                    else -> "${categoryEmoji(f)} $f"
                                },
                            )
                        },
                        shape = RoundedCornerShape(50),
                    )
                }
            }
        }
        item(key = "summary") {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SummaryTile("Spent", spent.sumOf { it.amountPaise }, spent.size, MaterialTheme.colorScheme.onSurface, Modifier.weight(1f))
                SummaryTile("Received", received.sumOf { it.amountPaise }, received.size, CreditGreen, Modifier.weight(1f))
            }
        }

        if (filtered.isEmpty()) {
            item(key = "empty") {
                Column(
                    Modifier.fillMaxWidth().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("🔍", style = MaterialTheme.typography.displaySmall)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (txns.isEmpty()) "No payments tracked yet. They'll appear here as soon as your bank sends an alert."
                        else "Nothing matches your search.",
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        grouped.forEach { (date, list) ->
            val day = relativeDay(list.first().timestamp)
            item(key = "h$date") {
                Row(
                    Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(day, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    val daySpent = list.filter { it.type == TxnType.DEBIT && it.category != Categorizer.SELF }.sumOf { it.amountPaise }
                    if (daySpent > 0) {
                        Text(
                            formatRupeesShort(daySpent),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            item(key = "g$date") {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.padding(horizontal = 16.dp),
                ) {
                    Column(Modifier.padding(vertical = 4.dp)) {
                        list.forEachIndexed { i, t ->
                            TxnRow(t, onClick = { detailId = t.id })
                            if (i < list.lastIndex) {
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
    }

    txns.find { it.id == detailId }?.let { t ->
        TxnDetailSheet(t, splits, actions, onDismiss = { detailId = null })
    }
}

@Composable
private fun SummaryTile(label: String, paise: Long, count: Int, color: Color, modifier: Modifier = Modifier) {
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = modifier) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(formatRupeesShort(paise), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, color = color)
            Text(
                "$count ${if (count == 1) "payment" else "payments"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
