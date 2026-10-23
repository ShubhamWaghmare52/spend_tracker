package com.shubham.spendtracker.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Color
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
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedButton
import com.shubham.spendtracker.data.Split
import com.shubham.spendtracker.data.Tags
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

// Mid-tone colours that carry white initials in both light and dark themes.
private val monogramColors = listOf(
    Color(0xFF5C6BC0), Color(0xFF26A69A), Color(0xFFEF6C00), Color(0xFF8E24AA),
    Color(0xFF00897B), Color(0xFFD81B60), Color(0xFF3949AB), Color(0xFF6D4C41),
    Color(0xFF43A047), Color(0xFFF4511E), Color(0xFF1E88E5), Color(0xFF7CB342),
)

/**
 * A payee's initials on its own colour, with the category emoji as a small badge.
 * Falls back to the plain category tile when there's no payee name.
 */
@Composable
fun MerchantAvatar(merchant: String?, category: String, size: Dp = 42.dp) {
    if (merchant.isNullOrBlank()) {
        CategoryAvatar(category, size)
        return
    }
    val badge = size * 0.46f
    Box(Modifier.size(size + badge * 0.25f)) {
        Box(
            Modifier
                .size(size)
                .background(monogramColors[monogramSlot(merchant, monogramColors.size)], CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                monogram(merchant),
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = (size.value * 0.36f).sp,
            )
        }
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .size(badge)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape)
                .border(1.5.dp, MaterialTheme.colorScheme.surfaceContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(categoryEmoji(category), fontSize = (badge.value * 0.55f).sp)
        }
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
        MerchantAvatar(t.merchant, t.category)
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
                    t.tagList.firstOrNull()?.let { append(" · #").append(it) }
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

/** Asks for a rupee amount. Saving 0 (via [removeLabel]) clears it. */
@Composable
fun AmountDialog(title: String, current: Long, removeLabel: String, onDismiss: () -> Unit, onSave: (Long) -> Unit) {
    var text by remember { mutableStateOf(if (current > 0) (current / 100).toString() else "") }
    val rupees = text.toLongOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { v -> text = v.filter(Char::isDigit).take(7) },
                prefix = { Text("₹") },
                placeholder = { Text("1000") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave((rupees ?: 0) * 100) }, enabled = rupees != null && rupees > 0) { Text("Save") }
        },
        dismissButton = {
            if (current > 0) TextButton(onClick = { onSave(0) }) { Text(removeLabel) }
            else TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
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
                    append("${formatRupeesShort(spent)} spent")
                    if (received > 0) append(" · ${formatRupeesShort(received)} received")
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
    splits: List<Split>,
    actions: TxnActions,
    onDismiss: () -> Unit,
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
                .imePadding() // keep the note/tags fields above the keyboard
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            MerchantAvatar(txn.merchant, txn.category, 64.dp)
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
                            onClick = { if (cat != txn.category) actions.setCategory(txn, cat) },
                            label = { Text("${categoryEmoji(cat)} $cat") },
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            NoteAndTags(txn, actions)

            if (txn.type == TxnType.DEBIT) {
                Spacer(Modifier.height(24.dp))
                SplitSection(txn, splits.filter { it.txnId == txn.id }, actions)
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
                onClick = { actions.delete(txn); onDismiss() },
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) { Text("Not a payment — remove") }
        }
    }
}

/** Everything the payment detail sheet can change. */
class TxnActions(
    val setCategory: (Transaction, String) -> Unit,
    val delete: (Transaction) -> Unit,
    val saveNote: (Transaction, String?, String?) -> Unit,
    val split: (Transaction, List<String>) -> Unit,
    val removeSplits: (Transaction) -> Unit,
    val setSettled: (Split, Boolean) -> Unit,
)

@Composable
private fun SectionTitle(title: String, subtitle: String? = null) {
    Column(Modifier.fillMaxWidth()) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Spacer(Modifier.height(10.dp))
    }
}

@Composable
private fun NoteAndTags(txn: Transaction, actions: TxnActions) {
    var note by remember(txn.id) { mutableStateOf(txn.note.orEmpty()) }
    var tags by remember(txn.id) { mutableStateOf(txn.tags.orEmpty().replace(",", ", ")) }
    val changed = note.trim() != txn.note.orEmpty() || Tags.join(Tags.parse(tags)) != txn.tags
    Column(Modifier.fillMaxWidth()) {
        SectionTitle("Note and tags", "Tags group payments across categories, e.g. a trip or an event")
        OutlinedTextField(
            value = note,
            onValueChange = { note = it.take(200) },
            label = { Text("Note") },
            modifier = Modifier.fillMaxWidth(),
            maxLines = 3,
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = tags,
            onValueChange = { tags = it.take(120) },
            label = { Text("Tags") },
            placeholder = { Text("Goa trip, Work") },
            supportingText = { Text("Separate with commas") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        if (changed) {
            Button(onClick = { actions.saveNote(txn, note, tags) }, modifier = Modifier.align(Alignment.End)) { Text("Save") }
        }
    }
}

@Composable
private fun SplitSection(txn: Transaction, splits: List<Split>, actions: TxnActions) {
    var asking by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        if (splits.isEmpty()) {
            SectionTitle("Split", "Paid for friends? Track who owes you. Their UPI payback is matched automatically.")
            OutlinedButton(onClick = { asking = true }) { Text("Split this payment") }
        } else {
            val owed = splits.filter { it.settledAt == null }.sumOf { it.sharePaise }
            SectionTitle(
                "Split",
                if (owed > 0) "${formatRupees(owed)} still to come back" else "Everyone has paid back 🎉",
            )
            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
                Column {
                    splits.forEachIndexed { i, sp ->
                        Row(
                            Modifier.fillMaxWidth().padding(start = 12.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            MerchantAvatar(sp.person, Categorizer.TRANSFERS, 36.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(sp.person, fontWeight = FontWeight.Medium)
                                Text(
                                    if (sp.settledAt != null) "Paid back ${relativeDay(sp.settledAt)}" else "Owes ${formatRupees(sp.sharePaise)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (sp.settledAt != null) CreditGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Checkbox(checked = sp.settledAt != null, onCheckedChange = { actions.setSettled(sp, it) })
                        }
                        if (i < splits.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
            TextButton(onClick = { asking = true }) { Text("Change split") }
        }
    }
    if (asking) {
        SplitDialog(
            txn, splits.map { it.person },
            onDismiss = { asking = false },
            onSave = { people -> if (people.isEmpty()) actions.removeSplits(txn) else actions.split(txn, people); asking = false },
        )
    }
}

@Composable
private fun SplitDialog(txn: Transaction, current: List<String>, onDismiss: () -> Unit, onSave: (List<String>) -> Unit) {
    var names by remember { mutableStateOf(current.joinToString(", ")) }
    val people = names.split(',').map { it.trim() }.filter { it.isNotEmpty() }.distinctBy { it.lowercase() }
    val share = if (people.isEmpty()) 0 else txn.amountPaise / (people.size + 1)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Split ${formatRupees(txn.amountPaise)}") },
        text = {
            Column {
                Text("Split equally between you and:", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = names,
                    onValueChange = { names = it },
                    placeholder = { Text("Rahul, Amit") },
                    supportingText = { Text("Names, separated by commas") },
                    singleLine = true,
                )
                if (people.isNotEmpty()) {
                    Text(
                        "${formatRupees(share)} each from ${people.size} ${if (people.size == 1) "person" else "people"} · your share ${formatRupees(txn.amountPaise - share * people.size)}",
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(people) }, enabled = people.isNotEmpty() || current.isNotEmpty()) { Text(if (people.isEmpty()) "Remove split" else "Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
