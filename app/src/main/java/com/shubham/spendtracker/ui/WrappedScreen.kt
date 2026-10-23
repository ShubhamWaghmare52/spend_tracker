package com.shubham.spendtracker.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shubham.spendtracker.data.Transaction
import com.shubham.spendtracker.insights.WrapStats
import com.shubham.spendtracker.insights.Wrapped
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

private data class StoryPage(val emoji: String, val kicker: String, val headline: String, val detail: String, val colors: List<Color>)

// Each page gets its own gradient so swiping feels like a story, not a list.
private val palettes = listOf(
    listOf(Color(0xFF2A8A72), Color(0xFF0E4A3C)),
    listOf(Color(0xFFE76F51), Color(0xFF8C2F1C)),
    listOf(Color(0xFF5C6BC0), Color(0xFF26306E)),
    listOf(Color(0xFFD81B60), Color(0xFF6D0F33)),
    listOf(Color(0xFF00897B), Color(0xFF004D45)),
    listOf(Color(0xFFEF6C00), Color(0xFF7A3500)),
    listOf(Color(0xFF8E24AA), Color(0xFF45105A)),
    listOf(Color(0xFF2A8A72), Color(0xFF0E4A3C)),
)

private fun pagesFor(w: WrapStats, isCurrentMonth: Boolean): List<StoryPage> {
    val monthName = w.month.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH)
    val share = (w.topCategoryPaise * 100 / w.spentPaise.coerceAtLeast(1)).toInt()
    val pages = buildList {
        add(StoryPage("✨", if (isCurrentMonth) "$monthName so far" else "$monthName in review",
            formatRupeesShort(w.spentPaise), "spent across ${w.payments} payments", palettes[0]))
        add(StoryPage(categoryEmoji(w.topCategory), "Most of it went on", w.topCategory,
            "${formatRupeesShort(w.topCategoryPaise)} · $share% of your spending", palettes[1]))
        w.topMerchant?.let {
            add(StoryPage("🏆", "Your number one place", it, "${formatRupeesShort(w.topMerchantPaise)} this month", palettes[2]))
        }
        w.mostVisited?.let {
            add(StoryPage("🔁", "Your regular", it, "You paid them ${w.visits} times", palettes[3]))
        }
        add(StoryPage("💥", "Biggest single payment", formatRupeesShort(w.biggest.amountPaise),
            "${w.biggest.merchant ?: w.biggest.category} · ${formatDay(w.biggest.timestamp)}", palettes[4]))
        add(StoryPage("📅", "Your priciest day of the week",
            w.priciestWeekday.getDisplayName(TextStyle.FULL, Locale.ENGLISH) + "s",
            "${formatRupeesShort(w.priciestWeekdayPaise)} spent on them", palettes[5]))
        w.savingsRate?.let { rate ->
            add(
                if (rate >= 0) StoryPage("🐷", "You kept", "$rate%", "of the money that came in", palettes[6])
                else StoryPage("📉", "Heads up", "${-rate}% over", "you spent more than came in", palettes[6]),
            )
        }
    }
    return pages + StoryPage("👋", "That's a wrap", "See you next month", "Keep tracking, it all adds up", palettes[7])
}

/** A swipeable, story-style recap of a month's spending. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WrappedScreen(txns: List<Transaction>, onDone: () -> Unit, modifier: Modifier = Modifier) {
    val month = remember { Wrapped.monthFor(LocalDate.now()) }
    val stats = remember(txns) { Wrapped.compute(txns, month) }
    if (stats == null) {
        Box(modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
            Text("Not enough payments yet for a recap. Check back after a few days of spending.", textAlign = TextAlign.Center)
        }
        return
    }
    val pages = remember(stats) { pagesFor(stats, month == YearMonth.now()) }
    val pager = rememberPagerState { pages.size }

    Column(modifier.fillMaxSize().padding(16.dp)) {
        HorizontalPager(pager, Modifier.weight(1f), pageSpacing = 12.dp) { i ->
            val p = pages[i]
            // Entrance keyed per page so content rises in each time a page is swiped to.
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(32.dp))
                    .background(Brush.linearGradient(p.colors)),
                contentAlignment = Alignment.Center,
            ) {
                if (pager.currentPage == i) {
                    Column(Modifier.padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Entrance(0, true) { Text(p.emoji, fontSize = 64.sp) }
                        Spacer(Modifier.height(24.dp))
                        Entrance(1, true) {
                            Text(p.kicker, color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                        }
                        Spacer(Modifier.height(8.dp))
                        Entrance(2, true) {
                            Text(p.headline, color = Color.White, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                        }
                        Spacer(Modifier.height(12.dp))
                        Entrance(3, true) {
                            Text(p.detail, color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
                        }
                        if (i == pages.lastIndex) {
                            Spacer(Modifier.height(32.dp))
                            Button(
                                onClick = onDone,
                                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = p.colors.last()),
                            ) { Text("Done", fontWeight = FontWeight.SemiBold) }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            repeat(pages.size) { i ->
                Box(
                    Modifier
                        .padding(horizontal = 3.dp)
                        .size(width = if (i == pager.currentPage) 20.dp else 8.dp, height = 8.dp)
                        .background(
                            if (i == pager.currentPage) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                            RoundedCornerShape(50),
                        ),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Swipe for more",
            Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
