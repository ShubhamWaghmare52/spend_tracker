package com.shubham.spendtracker.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.shubham.spendtracker.data.Split
import com.shubham.spendtracker.data.Transaction
import com.shubham.spendtracker.parser.Categorizer
import com.shubham.spendtracker.parser.TxnType
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset

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
    var dateCode by rememberSaveable { mutableStateOf(DateFilter.AllTime.encode()) }
    val dateFilter = DateFilter.decode(dateCode)
    var pickingRange by remember { mutableStateOf(false) }
    val firstMonth = remember(txns) {
        txns.minOfOrNull { it.timestamp }?.let { YearMonth.from(Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault())) }
    }

    val categories = remember(txns) {
        txns.groupingBy { it.category }.eachCount().entries.sortedByDescending { it.value }.map { it.key }
    }
    // Tag filters are stored as "#tag" so they can't clash with a category name.
    val tagFilters = remember(txns) { txns.flatMap { it.tagList }.distinctBy { it.lowercase() }.sorted().map { "#$it" } }
    val filtered = remember(txns, query, filter, dateCode) {
        val q = query.trim()
        val window = dateFilter.window()
        txns.filter { t ->
            if (window != null && t.timestamp !in window) return@filter false
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
        item(key = "dates") {
            DateBar(
                filter = dateFilter,
                firstMonth = firstMonth,
                onChange = { dateCode = it.encode() },
                onPickRange = { pickingRange = true },
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
                        else if (query.isBlank() && filter == ALL && dateFilter != DateFilter.AllTime) "No payments in ${dateFilter.label()}."
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
    if (pickingRange) {
        RangePickerDialog(
            initial = dateFilter as? DateFilter.Range,
            showMonth = when (dateFilter) {
                is DateFilter.Month -> dateFilter.month
                is DateFilter.Range -> YearMonth.from(dateFilter.start)
                DateFilter.AllTime -> null
            },
            onPick = { dateCode = it.encode(); pickingRange = false },
            onDismiss = { pickingRange = false },
        )
    }
}

private val DateMenuWidth = 200.dp

/**
 * ‹ November 2026 › with a menu on the label: All time, This month, Last month or a custom range.
 * The arrows step one month at a time, from the first tracked payment up to this month.
 */
@Composable
private fun DateBar(filter: DateFilter, firstMonth: YearMonth?, onChange: (DateFilter) -> Unit, onPickRange: () -> Unit) {
    val thisMonth = YearMonth.now()
    var menuOpen by remember { mutableStateOf(false) }
    var labelWidth by remember { mutableStateOf(0) }
    val density = LocalDensity.current
    val month = (filter as? DateFilter.Month)?.month
    val canGoBack = when (filter) {
        DateFilter.AllTime -> true
        is DateFilter.Month -> firstMonth == null || filter.month > firstMonth
        is DateFilter.Range -> false
    }
    val canGoForward = month != null && month < thisMonth

    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = { onChange(DateFilter.Month(month?.minusMonths(1) ?: thisMonth)) },
            enabled = canGoBack,
        ) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous month") }
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            Box {
                Row(
                    Modifier
                        .onSizeChanged { labelWidth = it.width }
                        .clip(RoundedCornerShape(50))
                        .clickable { menuOpen = true }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(filter.label(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Icon(Icons.Filled.ArrowDropDown, contentDescription = "Choose dates")
                }
                // A fixed-width menu, centred under the label, with the current choice ticked.
                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false },
                    offset = DpOffset(with(density) { labelWidth.toDp() } / 2 - DateMenuWidth / 2, 4.dp),
                    modifier = Modifier.width(DateMenuWidth),
                ) {
                    listOf(
                        "All time" to DateFilter.AllTime,
                        "This month" to DateFilter.Month(thisMonth),
                        "Last month" to DateFilter.Month(thisMonth.minusMonths(1)),
                        "Custom range…" to null,
                    ).forEach { (label, choice) ->
                        val current = if (choice == null) filter is DateFilter.Range else filter == choice
                        DropdownMenuItem(
                            text = { Text(label, fontWeight = if (current) FontWeight.SemiBold else null) },
                            onClick = { menuOpen = false; if (choice == null) onPickRange() else onChange(choice) },
                            trailingIcon = { if (current) Icon(Icons.Filled.Check, contentDescription = "Selected") },
                        )
                    }
                }
            }
        }
        if (filter is DateFilter.Range) {
            IconButton(onClick = { onChange(DateFilter.AllTime) }) { Icon(Icons.Filled.Close, contentDescription = "Clear dates") }
        } else {
            IconButton(
                onClick = { month?.let { onChange(DateFilter.Month(it.plusMonths(1))) } },
                enabled = canGoForward,
            ) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next month") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RangePickerDialog(initial: DateFilter.Range?, showMonth: YearMonth?, onPick: (DateFilter.Range) -> Unit, onDismiss: () -> Unit) {
    // The picker works in UTC midnights; convert at the edges only.
    fun LocalDate.utc() = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    fun Long.date() = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()
    val today = LocalDate.now()
    val state = rememberDateRangePickerState(
        initialSelectedStartDateMillis = initial?.start?.utc(),
        initialSelectedEndDateMillis = initial?.end?.utc(),
        initialDisplayedMonthMillis = showMonth?.atDay(1)?.utc(),
        selectableDates = object : androidx.compose.material3.SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis.date() <= today
            override fun isSelectableYear(year: Int) = year <= today.year
        },
    )
    val start = state.selectedStartDateMillis?.date()
    val end = state.selectedEndDateMillis?.date()
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = { start?.let { onPick(DateFilter.Range(it, end ?: it)) } },
                enabled = start != null,
            ) { Text("Show") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    ) {
        DateRangePicker(
            state,
            Modifier.weight(1f),
            title = { Text("Choose dates", Modifier.padding(start = 24.dp, top = 16.dp)) },
            headline = {
                Text(
                    if (start == null) "Start date – end date" else DateFilter.Range(start, end ?: start).label(),
                    Modifier.padding(start = 24.dp, end = 12.dp, bottom = 12.dp),
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                )
            },
        )
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
