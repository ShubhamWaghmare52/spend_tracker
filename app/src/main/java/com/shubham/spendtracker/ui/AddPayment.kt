package com.shubham.spendtracker.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.shubham.spendtracker.data.ManualPayment
import com.shubham.spendtracker.insights.AccountBalance
import com.shubham.spendtracker.insights.BalanceGap
import com.shubham.spendtracker.parser.TxnType
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.math.abs

/** Keeps a typed amount to digits and at most one decimal point with two places ("240", "33.33"). */
internal fun cleanAmount(input: String): String {
    val kept = StringBuilder()
    for (c in input) {
        if (c.isDigit()) {
            val dot = kept.indexOf(".")
            if (dot >= 0 && kept.length - dot > 2) continue
            kept.append(c)
        } else if (c == '.' && '.' !in kept) {
            kept.append(if (kept.isEmpty()) "0." else ".")
        }
    }
    return kept.take(10).toString()
}

/** What the form starts with when filling in a [gap]: the missing amount, just before the bank's SMS. */
internal fun paymentFor(gap: BalanceGap): ManualPayment {
    val at = if (gap.to - gap.from > 2 * 60_000) gap.to - 60_000 else (gap.from + gap.to) / 2
    return ManualPayment(
        type = if (gap.amountPaise < 0) TxnType.DEBIT else TxnType.CREDIT,
        amountPaise = abs(gap.amountPaise),
        name = null,
        timestamp = at,
        bank = gap.bank,
        last4 = gap.last4,
    )
}

private fun accountLabel(bank: String, last4: String?) = bank + (last4?.let { " ••$it" } ?: "")

/** Form for a payment no SMS or notification announced. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddPaymentDialog(
    initial: ManualPayment,
    accounts: List<AccountBalance>,
    onDismiss: () -> Unit,
    onSave: (ManualPayment) -> Unit,
) {
    var type by remember { mutableStateOf(initial.type) }
    var amount by remember {
        mutableStateOf(
            if (initial.amountPaise > 0) initial.amountPaise.toBigDecimal().movePointLeft(2).stripTrailingZeros().toPlainString() else "",
        )
    }
    var name by remember { mutableStateOf(initial.name.orEmpty()) }
    var timestamp by remember { mutableStateOf(initial.timestamp) }
    var account by remember { mutableStateOf(initial.bank?.let { it to initial.last4 }) }
    var pickingDate by remember { mutableStateOf(false) }
    val paise = amount.toBigDecimalOrNull()?.movePointRight(2)?.toLong()?.takeIf { it > 0 }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add payment") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    listOf(TxnType.DEBIT to "Paid", TxnType.CREDIT to "Received").forEachIndexed { i, (t, label) ->
                        SegmentedButton(
                            selected = type == t,
                            onClick = { type = t },
                            shape = SegmentedButtonDefaults.itemShape(i, 2),
                            icon = {},
                        ) { Text(label) }
                    }
                }
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = cleanAmount(it) },
                    label = { Text("Amount") },
                    prefix = { Text("₹") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(40) },
                    label = { Text(if (type == TxnType.DEBIT) "Paid to" else "Received from") },
                    singleLine = true,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Date", Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = { pickingDate = true }) { Text(formatDay(timestamp)) }
                }
                if (accounts.isNotEmpty()) {
                    Text("Account", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(account == null, { account = null }, label = { Text("Not sure") })
                        accounts.forEach { a ->
                            val key = a.bank to a.last4
                            FilterChip(account == key, { account = key }, label = { Text(accountLabel(a.bank, a.last4)) })
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { paise?.let { onSave(ManualPayment(type, it, name, timestamp, account?.first, account?.second)) } },
                enabled = paise != null,
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )

    if (pickingDate) {
        val zone = ZoneId.systemDefault()
        val current = Instant.ofEpochMilli(timestamp).atZone(zone)
        // The picker works in UTC midnights; the payment keeps its time of day on the new date.
        val state = rememberDatePickerState(
            initialSelectedDateMillis = current.toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= System.currentTimeMillis()
            },
        )
        DatePickerDialog(
            onDismissRequest = { pickingDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { utc ->
                        val day = Instant.ofEpochMilli(utc).atZone(ZoneOffset.UTC).toLocalDate()
                        timestamp = day.atTime(current.toLocalTime()).atZone(zone).toInstant().toEpochMilli()
                    }
                    pickingDate = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { pickingDate = false }) { Text("Cancel") } },
        ) { DatePicker(state) }
    }
}

/** A balance that doesn't add up, offered as a payment to fill in. */
@Composable
fun MissingPaymentCard(gap: BalanceGap, onAdd: () -> Unit, onDismiss: () -> Unit) {
    val less = gap.amountPaise < 0
    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Possible missing payment", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "${accountLabel(gap.bank, gap.last4)} has ${formatRupees(abs(gap.amountPaise))} " +
                    "${if (less) "less" else "more"} than your recorded payments explain, between " +
                    "${formatDay(gap.from)}, ${formatTime(gap.from)} and ${formatDay(gap.to)}, ${formatTime(gap.to)}.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                if (less) "Probably a payment the bank sent no SMS for." else "Probably money you received without an SMS.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text("Dismiss") }
                Button(onClick = onAdd) { Text("Add payment") }
            }
        }
    }
}
