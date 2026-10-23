package com.shubham.spendtracker.ui

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.shubham.spendtracker.data.Split
import com.shubham.spendtracker.data.Transaction
import com.shubham.spendtracker.parser.Categorizer

/** Money friends owe the user, grouped by person, with recently settled shares below. */
@Composable
fun SplitsScreen(txns: List<Transaction>, splits: List<Split>, actions: TxnActions, modifier: Modifier = Modifier) {
    val byId = txns.associateBy { it.id }
    val owed = splits.filter { it.settledAt == null }
        .groupBy { it.person.lowercase() }.values
        .sortedByDescending { group -> group.sumOf { it.sharePaise } }
    val settled = splits.filter { it.settledAt != null }.sortedByDescending { it.settledAt }.take(10)
    val total = owed.sumOf { group -> group.sumOf { it.sharePaise } }

    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp)) {
        item(key = "header") {
            Column(Modifier.padding(vertical = 8.dp)) {
                Text("Friends owe you", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(formatRupeesShort(total), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.SemiBold)
                Text(
                    "When a friend pays you back by UPI, it's matched by name and amount and ticked off automatically.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
            }
        }
        if (owed.isEmpty() && settled.isEmpty()) {
            item(key = "empty") {
                Column(Modifier.fillMaxWidth().padding(vertical = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🤝", style = MaterialTheme.typography.displayMedium)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "No splits yet. Open any payment and tap \"Split this payment\".",
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        items(owed, key = { "p" + it.first().person.lowercase() }) { shares ->
            PersonCard(shares, byId, actions)
        }
        if (settled.isNotEmpty()) {
            item(key = "settledTitle") {
                Text(
                    "Recently paid back",
                    Modifier.padding(start = 8.dp, top = 20.dp, bottom = 8.dp),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            items(settled, key = { "s${it.id}" }) { sp ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    MerchantAvatar(sp.person, Categorizer.TRANSFERS, 36.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("${sp.person} · ${formatRupees(sp.sharePaise)}", fontWeight = FontWeight.Medium)
                        Text(
                            "Paid back ${relativeDay(sp.settledAt!!)} · ${byId[sp.txnId]?.merchant ?: "payment"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = CreditGreen,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    TextButton(onClick = { actions.setSettled(sp, false) }) { Text("Undo") }
                }
            }
        }
    }
}

@Composable
private fun PersonCard(shares: List<Split>, byId: Map<Long, Transaction>, actions: TxnActions) {
    val person = shares.first().person
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.padding(vertical = 6.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                MerchantAvatar(person, Categorizer.TRANSFERS)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(person, fontWeight = FontWeight.SemiBold)
                    Text(
                        "${shares.size} ${if (shares.size == 1) "payment" else "payments"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(formatRupees(shares.sumOf { it.sharePaise }), fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
            }
            shares.forEach { sp ->
                HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                val t = byId[sp.txnId]
                Row(Modifier.padding(start = 16.dp, end = 4.dp, top = 2.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(t?.merchant ?: "Payment", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${t?.let { relativeDay(it.timestamp) } ?: ""} · share ${formatRupees(sp.sharePaise)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = { actions.setSettled(sp, true) }) { Text("Mark paid") }
                }
            }
        }
    }
}
