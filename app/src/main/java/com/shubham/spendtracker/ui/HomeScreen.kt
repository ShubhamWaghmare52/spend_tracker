package com.shubham.spendtracker.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.shubham.spendtracker.data.DailyLimit
import com.shubham.spendtracker.data.Transaction
import com.shubham.spendtracker.parser.Categorizer
import com.shubham.spendtracker.parser.TxnType
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.abs

enum class Period(val label: String, val noun: String) {
    TODAY("Today", "yesterday"),
    WEEK("Week", "last week"),
    MONTH("Month", "last month"),
}

private val zone: ZoneId get() = ZoneId.systemDefault()
private fun LocalDate.millis() = atStartOfDay(zone).toInstant().toEpochMilli()
private fun Long.toDate() = Instant.ofEpochMilli(this).atZone(zone).toLocalDate()

private fun startDate(period: Period, today: LocalDate = LocalDate.now()) = when (period) {
    Period.TODAY -> today
    Period.WEEK -> today.with(DayOfWeek.MONDAY)
    Period.MONTH -> today.withDayOfMonth(1)
}

private fun startOf(period: Period) = startDate(period).millis()

/** The same stretch of time one period earlier, e.g. Monday–now of last week. */
private fun previousWindow(period: Period): Pair<Long, Long> {
    val start = startDate(period)
    val prev = when (period) {
        Period.TODAY -> start.minusDays(1)
        Period.WEEK -> start.minusWeeks(1)
        Period.MONTH -> start.minusMonths(1)
    }.millis()
    return prev to prev + (System.currentTimeMillis() - start.millis())
}

private val longDayFmt = DateTimeFormatter.ofPattern("EEEE, d MMM")

private fun isSpend(t: Transaction) = t.type == TxnType.DEBIT && t.category != Categorizer.SELF

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    txns: List<Transaction>,
    playIntro: Boolean,
    onIntroStarted: () -> Unit,
    smsGranted: Boolean,
    notifEnabled: Boolean,
    onGrantSms: () -> Unit,
    onEnableNotif: () -> Unit,
    dailyLimit: Long,
    onSetLimit: (Long) -> Unit,
    onSetCategory: (Transaction, String) -> Unit,
    onDelete: (Transaction) -> Unit,
    modifier: Modifier = Modifier,
) {
    var period by rememberSaveable { mutableStateOf(Period.WEEK) }
    var editingLimit by remember { mutableStateOf(false) }
    var listQuery by remember { mutableStateOf<PaymentQuery?>(null) }
    var selectedBar by remember { mutableStateOf<Int?>(null) }
    var detailId by remember { mutableStateOf<Long?>(null) }

    val periodStart = startOf(period)
    val inPeriod = remember(txns, period) { txns.filter { it.timestamp >= periodStart && it.category != Categorizer.SELF } }
    val spent = inPeriod.filter(::isSpend)
    val spentTotal = spent.sumOf { it.amountPaise }
    val receivedTotal = inPeriod.filter { it.type == TxnType.CREDIT }.sumOf { it.amountPaise }
    val prevTotal = remember(txns, period) {
        val (from, to) = previousWindow(period)
        txns.filter { it.timestamp in from until to && isSpend(it) }.sumOf { it.amountPaise }
    }
    val spentToday = remember(txns) {
        val s = startOf(Period.TODAY)
        txns.filter { it.timestamp >= s && isSpend(it) }.sumOf { it.amountPaise }
    }
    LaunchedEffect(Unit) { if (playIntro) onIntroStarted() }
    // Meters fill and bars grow again whenever the period changes.
    val growth = rememberGrowth(period, delayMs = if (playIntro) 200 else 0)

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(Modifier.height(4.dp))
        if (!smsGranted || !notifEnabled) SetupCard(smsGranted, notifEnabled, onGrantSms, onEnableNotif)

        Entrance(0, playIntro) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            Period.entries.forEachIndexed { i, p ->
                SegmentedButton(
                    selected = p == period,
                    onClick = { period = p; selectedBar = null },
                    shape = SegmentedButtonDefaults.itemShape(i, Period.entries.size),
                    icon = {},
                ) { Text(p.label) }
            }
        }
        }

        // Headline total
        Entrance(1, playIntro) {
        Column {
            Text(
                when (period) {
                    Period.TODAY -> "Spent today"
                    Period.WEEK -> "Spent this week"
                    Period.MONTH -> "Spent this month"
                },
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            CountUpRupees(spentTotal, MaterialTheme.typography.displayMedium, FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (prevTotal > 0 || spentTotal > 0) {
                    val diff = spentTotal - prevTotal
                    val up = diff > 0
                    Text(
                        (if (up) "▲ " else "▼ ") + formatRupees(abs(diff)),
                        color = if (up) MaterialTheme.colorScheme.error else CreditGreen,
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        "${if (up) "more" else "less"} than ${period.noun}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            Text(
                "${spent.size} ${if (spent.size == 1) "payment" else "payments"} · ${formatRupees(receivedTotal)} received",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        }

        Entrance(2, playIntro) { LimitCard(spentToday, dailyLimit, growth::value, onEdit = { editingLimit = true }) }

        // Chart, or today's list
        Entrance(3, playIntro) {
        when (period) {
            Period.TODAY -> {
                val today = inPeriod.sortedByDescending { it.timestamp }
                SectionCard("Today's payments", trailing = if (today.size > 5) "See all ${today.size}" else null, onTrailing = {
                    listQuery = PaymentQuery("Today", periodStart, Long.MAX_VALUE)
                }) {
                    if (today.isEmpty()) {
                        EmptyLine("No payments yet today.")
                    } else {
                        Column(Modifier.padding(horizontal = 0.dp)) {
                            today.take(5).forEach { t -> TxnRow(t, onClick = { detailId = t.id }) }
                        }
                    }
                }
            }
            Period.WEEK, Period.MONTH -> {
                val start = startDate(period)
                val days = if (period == Period.WEEK) 7 else start.lengthOfMonth()
                val perDay = LongArray(days)
                spent.forEach {
                    val idx = ChronoUnit.DAYS.between(start, it.timestamp.toDate()).toInt()
                    if (idx in 0 until days) perDay[idx] += it.amountPaise
                }
                val todayIdx = ChronoUnit.DAYS.between(start, LocalDate.now()).toInt()
                val labels = List(days) { i ->
                    if (period == Period.WEEK) start.plusDays(i.toLong()).dayOfWeek.name.take(3).lowercase().replaceFirstChar(Char::uppercase)
                    else if (i % 7 == 0) "${i + 1}" else ""
                }
                SectionCard("Daily spending", subtitle = "Tap a bar to see that day") {
                    BarChart(perDay, labels, todayIdx, selectedBar, growth::value) { i ->
                        selectedBar = i
                        val day = start.plusDays(i.toLong())
                        listQuery = PaymentQuery(day.format(longDayFmt), day.millis(), day.plusDays(1).millis())
                    }
                    val avgDays = (todayIdx + 1).coerceAtLeast(1)
                    Row(Modifier.fillMaxWidth()) {
                        Stat("Daily average", formatRupees(spentTotal / avgDays), Modifier.weight(1f))
                        val best = perDay.withIndex().maxByOrNull { it.value }
                        if (best != null && best.value > 0) {
                            Stat(
                                "Biggest day",
                                "${formatRupees(best.value)} · ${start.plusDays(best.index.toLong()).format(DateTimeFormatter.ofPattern("EEE d"))}",
                                Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }
        }

        // Categories
        val byCategory = spent.groupBy { it.category }
            .mapValues { (_, v) -> v.sumOf { it.amountPaise } }
            .entries.sortedByDescending { it.value }
        Entrance(4, playIntro) {
        SectionCard("Where it went") {
            if (byCategory.isEmpty()) EmptyLine("No spending in this period.")
            byCategory.forEach { (cat, amt) ->
                BreakdownRow(
                    category = cat,
                    title = cat,
                    subtitle = spent.count { it.category == cat }.let { n -> "$n ${if (n == 1) "payment" else "payments"}" },
                    amount = amt,
                    share = amt.toFloat() / spentTotal.coerceAtLeast(1),
                    fill = growth::value,
                    onClick = { listQuery = PaymentQuery("${categoryEmoji(cat)} $cat", periodStart, Long.MAX_VALUE, category = cat) },
                )
            }
        }
        }

        // Top merchants
        val byMerchant = spent.filter { it.merchant != null }.groupBy { it.merchant!! }
            .entries.sortedByDescending { e -> e.value.sumOf { it.amountPaise } }.take(5)
        if (byMerchant.isNotEmpty()) {
            Entrance(5, playIntro) {
            SectionCard("Top places") {
                byMerchant.forEach { (merchant, list) ->
                    val amt = list.sumOf { it.amountPaise }
                    BreakdownRow(
                        category = list.first().category,
                        title = merchant,
                        subtitle = "${list.size} ${if (list.size == 1) "payment" else "payments"} · ${list.first().category}",
                        amount = amt,
                        share = amt.toFloat() / spentTotal.coerceAtLeast(1),
                        fill = growth::value,
                        onClick = { listQuery = PaymentQuery(merchant, periodStart, Long.MAX_VALUE, merchant = merchant) },
                    )
                }
            }
            }
        }
        Spacer(Modifier.height(8.dp))
    }

    listQuery?.let { q ->
        PaymentListSheet(q, txns, onDismiss = { listQuery = null; selectedBar = null }, onOpen = { detailId = it.id })
    }
    txns.find { it.id == detailId }?.let { t ->
        TxnDetailSheet(
            t,
            onDismiss = { detailId = null },
            onSetCategory = { onSetCategory(t, it) },
            onDelete = { onDelete(t); detailId = null },
        )
    }
    if (editingLimit) {
        LimitDialog(dailyLimit, onDismiss = { editingLimit = false }, onSave = { onSetLimit(it); editingLimit = false })
    }
}

@Composable
private fun BarChart(
    values: LongArray,
    labels: List<String>,
    todayIndex: Int,
    selected: Int?,
    growth: () -> Float,
    onTap: (Int) -> Unit,
) {
    val primary = MaterialTheme.colorScheme.primary
    val future = MaterialTheme.colorScheme.surfaceContainerHighest
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val selectedStyle = labelStyle.copy(color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
    val max = (values.maxOrNull() ?: 0L).coerceAtLeast(1L)

    Canvas(
        Modifier
            .fillMaxWidth()
            .height(170.dp)
            .pointerInput(values.size, todayIndex) {
                detectTapGestures { o ->
                    val i = (o.x / (size.width.toFloat() / values.size)).toInt().coerceIn(0, values.size - 1)
                    if (i <= todayIndex) onTap(i)
                }
            },
    ) {
        val labelH = 20.dp.toPx()
        val valueH = 18.dp.toPx()
        val chartH = size.height - labelH - valueH
        val slot = size.width / values.size
        val barW = slot * if (values.size <= 7) 0.55f else 0.62f
        val minH = 4.dp.toPx()
        // Bars rise in a left-to-right wave: each starts a little after its neighbour.
        val g = growth()
        val spread = values.size / 2f + 3f
        values.forEachIndexed { i, v ->
            val p = ((g * (values.size + spread) - i) / spread).coerceIn(0f, 1f)
            val eased = 1f - (1f - p) * (1f - p) * (1f - p)
            val full = if (v == 0L) minH else (chartH * v / max).coerceAtLeast(minH)
            val h = (full * eased).coerceAtLeast(minOf(minH, full))
            val color = when {
                i > todayIndex -> future
                selected != null && selected != i -> primary.copy(alpha = 0.35f)
                else -> primary
            }
            val x = i * slot + (slot - barW) / 2
            drawRoundRect(
                color,
                Offset(x, valueH + chartH - h),
                Size(barW, h),
                CornerRadius(minOf(barW / 2, 8.dp.toPx())),
            )
            if (i == selected && v > 0) {
                val m = measurer.measure(formatRupees(v), selectedStyle)
                val tx = (i * slot + slot / 2 - m.size.width / 2).coerceIn(0f, size.width - m.size.width)
                drawText(m, topLeft = Offset(tx, valueH + chartH - h - m.size.height - 2.dp.toPx()))
            }
            if (labels[i].isNotEmpty()) {
                val m = measurer.measure(labels[i], if (i == selected || (selected == null && i == todayIndex)) selectedStyle else labelStyle)
                val tx = (i * slot + slot / 2 - m.size.width / 2).coerceIn(0f, size.width - m.size.width)
                drawText(m, topLeft = Offset(tx, size.height - labelH + 4.dp.toPx()))
            }
        }
    }
}

@Composable
private fun BreakdownRow(
    category: String,
    title: String,
    subtitle: String,
    amount: Long,
    share: Float,
    fill: () -> Float,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CategoryAvatar(category, 40.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, Modifier.weight(1f), fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(formatRupees(amount), fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(6.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(50)),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(share.coerceIn(0.02f, 1f))
                        .graphicsLayer {
                            scaleX = fill()
                            transformOrigin = TransformOrigin(0f, 0.5f)
                        }
                        .height(5.dp)
                        .background(categoryColor(category), RoundedCornerShape(50)),
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "$subtitle · ${(share * 100).toInt()}%",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier.padding(horizontal = 16.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun EmptyLine(text: String) {
    Text(text, Modifier.padding(horizontal = 16.dp, vertical = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun SectionCard(
    title: String,
    subtitle: String? = null,
    trailing: String? = null,
    onTrailing: () -> Unit = {},
    content: @Composable () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.fillMaxWidth().padding(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    subtitle?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                trailing?.let { TextButton(onClick = onTrailing) { Text(it) } }
            }
            content()
        }
    }
}

@Composable
private fun SetupCard(smsGranted: Boolean, notifEnabled: Boolean, onGrantSms: () -> Unit, onEnableNotif: () -> Unit) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Turn on auto-tracking", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "Payments are read from bank SMS alerts and from GPay / PhonePe / Paytm notifications. Everything stays on this phone.",
                style = MaterialTheme.typography.bodyMedium,
            )
            if (!smsGranted) Button(onClick = onGrantSms) { Text("Allow SMS access") }
            if (!notifEnabled) OutlinedButton(onClick = onEnableNotif) { Text("Allow notification access") }
        }
    }
}

@Composable
private fun LimitCard(spentToday: Long, limit: Long, fill: () -> Float, onEdit: () -> Unit) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (limit <= 0) {
                Text("Daily limit", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "Set how much you want to spend per day. You'll get a notification at 80% and when you cross it.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(onClick = onEdit) { Text("Set daily limit") }
                return@Column
            }
            val level = DailyLimit.level(spentToday, limit)
            val color = when (level) {
                DailyLimit.Level.OK -> MaterialTheme.colorScheme.primary
                DailyLimit.Level.NEAR -> WarnAmber
                DailyLimit.Level.OVER -> MaterialTheme.colorScheme.error
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Today's limit", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                TextButton(onClick = onEdit) { Text("${formatRupees(limit)} · Edit") }
            }
            Row(verticalAlignment = Alignment.Bottom) {
                Text(formatRupees(spentToday), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                Text(
                    if (level == DailyLimit.Level.OVER) "${formatRupees(spentToday - limit)} over"
                    else "${formatRupees(limit - spentToday)} left",
                    color = color,
                    fontWeight = FontWeight.Medium,
                )
            }
            LinearProgressIndicator(
                progress = { (spentToday.toFloat() / limit).coerceIn(0f, 1f) * fill() },
                modifier = Modifier.fillMaxWidth().height(8.dp),
                color = color,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                strokeCap = StrokeCap.Round,
                drawStopIndicator = {},
            )
        }
    }
}

@Composable
private fun LimitDialog(current: Long, onDismiss: () -> Unit, onSave: (Long) -> Unit) {
    var text by remember { mutableStateOf(if (current > 0) (current / 100).toString() else "") }
    val rupees = text.toLongOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Daily spending limit") },
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
            if (current > 0) TextButton(onClick = { onSave(0) }) { Text("Remove limit") }
            else TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
