package com.shubham.spendtracker.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.shubham.spendtracker.data.DailyLimit
import kotlin.math.abs

private val OnBrand = Color.White
private val OnBrandMuted = Color.White.copy(alpha = 0.72f)

/**
 * The Overview's focal point, in the same deep-green gradient and mint ring as the
 * launcher icon, splash and widget. The ring is today's spend against the daily limit.
 */
@Composable
fun HeroCard(
    label: String,
    spent: Long,
    previous: Long,
    previousNoun: String,
    payments: Int,
    received: Long,
    spentToday: Long,
    dailyLimit: Long,
    fill: () -> Float,
    onEditLimit: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(Brush.linearGradient(listOf(BrandGradientStart, BrandGradientEnd))),
    ) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelLarge, color = OnBrandMuted)
                CountUpRupees(
                    spent,
                    MaterialTheme.typography.displaySmall.copy(color = OnBrand),
                    FontWeight.SemiBold,
                )
                Spacer(Modifier.height(10.dp))
                if (previous > 0 || spent > 0) {
                    val diff = spent - previous
                    val up = diff > 0
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            (if (up) "▲ " else "▼ ") + formatRupeesShort(abs(diff)),
                            Modifier
                                .background(Color.White.copy(alpha = 0.14f), RoundedCornerShape(50))
                                .padding(horizontal = 10.dp, vertical = 3.dp),
                            color = if (up) BrandCoral else BrandMint,
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.labelLarge,
                        )
                        Text(
                            "${if (up) "more" else "less"} than $previousNoun",
                            color = OnBrandMuted,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                }
                Text(
                    "$payments ${if (payments == 1) "payment" else "payments"} · ${formatRupeesShort(received)} received",
                    color = OnBrandMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Spacer(Modifier.width(16.dp))
            LimitRing(spentToday, dailyLimit, fill, onEditLimit)
        }
    }
}

@Composable
private fun LimitRing(spentToday: Long, limit: Long, fill: () -> Float, onClick: () -> Unit) {
    val level = DailyLimit.level(spentToday, limit)
    val arc = when (level) {
        DailyLimit.Level.OK -> BrandMint
        DailyLimit.Level.NEAR -> BrandAmber
        DailyLimit.Level.OVER -> BrandCoral
    }
    val target = if (limit > 0) (spentToday.toFloat() / limit).coerceIn(0f, 1f) else 0f
    Column(
        Modifier.clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(92.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(92.dp)) {
                val stroke = 9.dp.toPx()
                val inset = stroke / 2
                val arcSize = Size(size.width - stroke, size.height - stroke)
                drawArc(Color.White.copy(alpha = 0.18f), 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
                val sweep = 360f * target * fill()
                if (sweep > 0f) {
                    drawArc(arc, -90f, sweep, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (limit > 0) {
                    val over = level == DailyLimit.Level.OVER
                    Text(
                        formatRupeesShort(if (over) spentToday - limit else limit - spentToday),
                        color = if (over) BrandCoral else OnBrand,
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(if (over) "over" else "left", color = OnBrandMuted, style = MaterialTheme.typography.labelSmall)
                } else {
                    Text(
                        "Set\nlimit",
                        color = OnBrand,
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            if (limit > 0) "of ${formatRupeesShort(limit)} today" else "Daily limit",
            color = OnBrandMuted,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}
