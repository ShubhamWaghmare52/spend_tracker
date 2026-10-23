package com.shubham.spendtracker.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.widget.RemoteViews
import com.shubham.spendtracker.MainActivity
import com.shubham.spendtracker.R
import com.shubham.spendtracker.data.AppSettings
import com.shubham.spendtracker.data.DailyLimit
import com.shubham.spendtracker.spendApp
import kotlinx.coroutines.launch

/** Home screen widget: today's spending against the daily limit, drawn as the app-icon ring. */
class SpendWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        context.spendApp.appScope.launch {
            try {
                refresh(context)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private const val MINT = 0xFF9BF2CF.toInt()
        private const val AMBER = 0xFFFFC86B.toInt()
        private const val CORAL = 0xFFFF8A80.toInt()

        /** Redraws every placed widget. Cheap no-op when none are on the home screen. */
        suspend fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, SpendWidget::class.java))
            if (ids.isEmpty()) return
            val spent = context.spendApp.repository.spentSince(DailyLimit.startOfToday())
            val limit = DailyLimit.limit(context).value
            val settings = AppSettings.current(context)
            manager.updateAppWidget(ids, build(context, WidgetContent.of(spent, limit, settings.appLock, settings.hideAmounts)))
        }

        private fun build(context: Context, content: WidgetContent): RemoteViews {
            val color = when (content.level) {
                DailyLimit.Level.OK -> MINT
                DailyLimit.Level.NEAR -> AMBER
                DailyLimit.Level.OVER -> CORAL
            }
            val open = PendingIntent.getActivity(
                context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
            )
            return RemoteViews(context.packageName, R.layout.widget_spend).apply {
                setImageViewBitmap(R.id.widget_ring, ring(context, content.fraction, color))
                setTextViewText(R.id.widget_amount, content.amount)
                setTextViewText(R.id.widget_detail, content.detail)
                setOnClickPendingIntent(android.R.id.background, open)
            }
        }

        private fun ring(context: Context, fraction: Float, color: Int): Bitmap {
            val px = (56 * context.resources.displayMetrics.density).toInt()
            val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            val stroke = px * 0.11f
            val inset = stroke / 2 + 1
            val rect = RectF(inset, inset, px - inset, px - inset)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = stroke
                strokeCap = Paint.Cap.ROUND
            }
            canvas.drawArc(rect, 0f, 360f, false, paint.apply { this.color = Color.argb(56, 255, 255, 255) })
            if (fraction > 0f) canvas.drawArc(rect, -90f, 360f * fraction, false, paint.apply { this.color = color })
            val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = Color.WHITE
                textSize = px * 0.38f
                typeface = Typeface.DEFAULT_BOLD
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText("₹", px / 2f, px / 2f - (text.descent() + text.ascent()) / 2, text)
            return bmp
        }
    }
}
