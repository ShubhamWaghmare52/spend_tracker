package com.shubham.spendtracker.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/** Material "emphasized decelerate": quick start, long gentle settle. */
val EaseOutEmphasized = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

private const val STAGGER_MS = 70L

/**
 * Fades and lifts [content] into place, [index] steps after the first item.
 * When [enabled] is false it renders in place straight away (e.g. returning to a tab).
 */
@Composable
fun Entrance(index: Int, enabled: Boolean, content: @Composable () -> Unit) {
    val progress = remember { Animatable(if (enabled) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (progress.value < 1f) {
            delay(index * STAGGER_MS)
            progress.animateTo(1f, tween(520, easing = EaseOutEmphasized))
        }
    }
    Box(
        Modifier.graphicsLayer {
            alpha = progress.value
            translationY = (1f - progress.value) * 28.dp.toPx()
        },
    ) { content() }
}

/** Counts from the previously shown amount (₹0 the first time) up or down to [paise]. */
@Composable
fun CountUpRupees(paise: Long, style: TextStyle, fontWeight: FontWeight? = null) {
    val from = remember { longArrayOf(0L) }
    val progress = remember { Animatable(1f) }
    val start = remember(paise) { from[0] }
    LaunchedEffect(paise) {
        progress.snapTo(0f)
        progress.animateTo(1f, tween(900, easing = EaseOutEmphasized))
    }
    val shown = start + ((paise - start) * progress.value).toLong()
    from[0] = shown
    Text(formatRupeesShort(shown), style = style, fontWeight = fontWeight)
}

/** 0→1 progress that replays whenever [key] changes; used to grow bars and fill meters. */
@Composable
fun rememberGrowth(key: Any?, durationMs: Int = 700, delayMs: Long = 0): Animatable<Float, *> {
    val growth = remember { Animatable(0f) }
    LaunchedEffect(key) {
        growth.snapTo(0f)
        delay(delayMs)
        growth.animateTo(1f, tween(durationMs, easing = EaseOutEmphasized))
    }
    return growth
}
