package com.shubham.spendtracker.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shubham.spendtracker.data.Transaction
import com.shubham.spendtracker.parser.Categorizer
import com.shubham.spendtracker.parser.TxnType

@Composable
fun CategoryAvatar(category: String, size: Dp = 42.dp) {
    Box(
        Modifier
            .size(size)
            .background(categoryColor(category).copy(alpha = 0.18f), RoundedCornerShape(size * 0.32f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(categoryEmoji(category), fontSize = (size.value * 0.45f).sp)
    }
}

fun amountText(t: Transaction): String =
    (if (t.type == TxnType.CREDIT) "+" else "") + formatRupees(t.amountPaise)

@Composable
fun amountColor(t: Transaction) =
    if (t.type == TxnType.CREDIT) CreditGreen else MaterialTheme.colorScheme.onSurface

@Composable
fun TxnRow(t: Transaction, onClick: () -> Unit, showDay: Boolean = false) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CategoryAvatar(t.category)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                t.merchant ?: t.category,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                buildString {
                    append(t.category)
                    append(" · ")
                    if (showDay) append(relativeDay(t.timestamp)).append(", ")
                    append(formatTime(t.timestamp))
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(amountText(t), color = amountColor(t), fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyLarge)
            Text(
                sourceLabel(t.sourceApp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

/** Which payments a list sheet shows: a day, a category or a merchant within a time range. */
data class PaymentQuery(
    val title: String,
    val from: Long,
    val to: Long,
    val category: String? = null,
    val merchant: String? = null,
) {
    fun matches(t: Transaction) =
        t.timestamp in from until to && (category == null || t.category == category) && (merchant == null || t.merchant == merchant)

    val multiDay get() = to - from > 24 * 60 * 60 * 1000L
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentListSheet(query: PaymentQuery, txns: List<Transaction>, onDismiss: () -> Unit, onOpen: (Transaction) -> Unit) {
    val list = remember(txns, query) { txns.filter(query::matches) }
    val spent = list.filter { it.type == TxnType.DEBIT && it.category != Categorizer.SELF }.sumOf { it.amountPaise }
    val received = list.filter { it.type == TxnType.CREDIT && it.category != Categorizer.SELF }.sumOf { it.amountPaise }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.padding(horizontal = 20.dp)) {
            Text(query.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(
                buildString {
                    append("${formatRupees(spent)} spent")
                    if (received > 0) append(" · ${formatRupees(received)} received")
                    append(" · ${list.size} ${if (list.size == 1) "payment" else "payments"}")
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(12.dp))
        if (list.isEmpty()) {
            Text(
                "No payments here.",
                Modifier.padding(20.dp).padding(bottom = 24.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LazyColumn(contentPadding = PaddingValues(bottom = 32.dp)) {
                items(list, key = { it.id }) { TxnRow(it, onClick = { onOpen(it) }, showDay = query.multiDay) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun TxnDetailSheet(
    txn: Transaction,
    onDismiss: () -> Unit,
    onSetCategory: (String) -> Unit,
    onDelete: () -> Unit,
) {
    var showRaw by remember { mutableStateOf(false) }
    val credit = txn.type == TxnType.CREDIT
    val rawMessage = remember { BringIntoViewRequester() }
    LaunchedEffect(showRaw) { if (showRaw) rawMessage.bringIntoView() }

    // Open fully expanded: a half-open sheet snaps back down whenever its content grows
    // (e.g. revealing the original message), which loses the user's scroll position.
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CategoryAvatar(txn.category, 64.dp)
            Spacer(Modifier.height(12.dp))
            Text(
                txn.merchant ?: "Unknown payee",
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
            Text(
                amountText(txn),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.SemiBold,
                color = amountColor(txn),
            )
            Text(formatDateTime(txn.timestamp), color = MaterialTheme.colorScheme.onSurfaceVariant)

            Spacer(Modifier.height(20.dp))
            val rows = buildList {
                add("Category" to "${categoryEmoji(txn.category)}  ${txn.category}")
                add((if (credit) "Received in" else "Paid via") to sourceLabel(txn.sourceApp))
                txn.accountLast4?.let { add("Account" to "•• $it") }
                txn.upiRef?.let { add("Reference" to it) }
                add("Type" to if (credit) "Money in" else "Money out")
            }
            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
                Column {
                    rows.forEachIndexed { i, (label, value) ->
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) {
                            Text(label, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(value, fontWeight = FontWeight.Medium, textAlign = TextAlign.End)
                        }
                        if (i < rows.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            Column(Modifier.fillMaxWidth()) {
                Text("Category", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    "Also applies to other payments to this merchant",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Categorizer.all.forEach { cat ->
                        FilterChip(
                            selected = cat == txn.category,
                            onClick = { if (cat != txn.category) onSetCategory(cat) },
                            label = { Text("${categoryEmoji(cat)} $cat") },
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            TextButton(onClick = { showRaw = !showRaw }) {
                Text(if (showRaw) "Hide original message" else "Show original message")
            }
            if (showRaw) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.bringIntoViewRequester(rawMessage),
                ) {
                    Text(txn.rawText, Modifier.padding(14.dp), style = MaterialTheme.typography.bodySmall)
                }
            }
            TextButton(
                onClick = onDelete,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) { Text("Not a payment — remove") }
        }
    }
}
