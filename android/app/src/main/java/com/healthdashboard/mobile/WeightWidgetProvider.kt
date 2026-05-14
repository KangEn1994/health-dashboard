package com.healthdashboard.mobile

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
import android.widget.RemoteViews
import com.healthdashboard.mobile.data.AuthRepository
import com.healthdashboard.mobile.data.HealthRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.max

class WeightWidgetProvider : AppWidgetProvider() {
    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val manager = AppWidgetManager.getInstance(context)
        val widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        when (intent.action) {
            ACTION_NEXT_METRIC -> {
                if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                    shiftMetricSelection(context, widgetId, +1)
                    updateWidgets(context, manager, intArrayOf(widgetId))
                }
            }
            ACTION_PREVIOUS_METRIC -> {
                if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                    shiftMetricSelection(context, widgetId, -1)
                    updateWidgets(context, manager, intArrayOf(widgetId))
                }
            }
            ACTION_REFRESH -> {
                val ids = if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) intArrayOf(widgetId) else manager.getAppWidgetIds(
                    ComponentName(context, WeightWidgetProvider::class.java),
                )
                updateWidgets(context, manager, ids)
            }
        }
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        updateWidgets(context, appWidgetManager, appWidgetIds)
    }

    override fun onEnabled(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, WeightWidgetProvider::class.java))
        updateWidgets(context, manager, ids)
    }

    companion object {
        private const val PREFS_NAME = "widget_metric_prefs"
        private const val ACTION_PREVIOUS_METRIC = "com.healthdashboard.mobile.widget.PREVIOUS_METRIC"
        private const val ACTION_NEXT_METRIC = "com.healthdashboard.mobile.widget.NEXT_METRIC"
        private const val ACTION_REFRESH = "com.healthdashboard.mobile.widget.REFRESH"

        fun updateWidgets(
            context: Context,
            manager: AppWidgetManager,
            ids: IntArray,
        ) {
            val repository = HealthRepository(AuthRepository(context.applicationContext))
            CoroutineScope(Dispatchers.IO).launch {
                val metricOptions = runCatching { repository.getWidgetMetricOptions() }.getOrNull().orEmpty()
                val availableMetricIds = buildList {
                    add("weight_kg")
                    if (metricOptions.any { it.id == "body_fat_pct" }) add("body_fat_pct")
                    addAll(metricOptions.map { it.id }.filterNot { it == "weight_kg" || it == "body_fat_pct" })
                    if (metricOptions.any { it.id == "weight_kg" }) add("bmi")
                }.distinct()
                saveMetricOptions(context, availableMetricIds)

                ids.forEach { widgetId ->
                    val selectedMetricId = getSelectedMetricId(context, widgetId, availableMetricIds)
                    val summary = runCatching { repository.getWidgetMetricSummary(selectedMetricId) }.getOrNull()
                    val views = RemoteViews(context.packageName, R.layout.widget_weight_overview)
                    val latest = summary?.latest
                    views.setTextViewText(
                        R.id.widgetValue,
                        latest?.let {
                            val suffix = summary.unit.takeIf { unit -> unit.isNotBlank() }?.let { unit -> " $unit" }.orEmpty()
                            String.format("%.1f%s", it, suffix)
                        } ?: context.getString(R.string.widget_empty),
                    )
                    views.setTextViewText(R.id.widgetTitle, summary?.label ?: context.getString(R.string.widget_title))
                    val subtitle = if (summary?.points?.isNotEmpty() == true) {
                        "最近14天 · ${summary.points.size} 条 · 最新接口数据"
                    } else {
                        "最近14天 · 最新接口数据"
                    }
                    views.setTextViewText(R.id.widgetSubtitle, subtitle)
                    val bitmap = createSparklineBitmap(summary?.points?.map { it.value }.orEmpty())
                    views.setImageViewBitmap(R.id.widgetSparkline, bitmap)

                    val openIntent = Intent(context, MainActivity::class.java)
                    val openPendingIntent = PendingIntent.getActivity(
                        context,
                        widgetId,
                        openIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                    )
                    views.setOnClickPendingIntent(R.id.widgetSparkline, openPendingIntent)
                    views.setOnClickPendingIntent(R.id.widgetTitle, openPendingIntent)
                    views.setOnClickPendingIntent(
                        R.id.widgetPrevious,
                        broadcastPendingIntent(context, widgetId, ACTION_PREVIOUS_METRIC),
                    )
                    views.setOnClickPendingIntent(
                        R.id.widgetNext,
                        broadcastPendingIntent(context, widgetId, ACTION_NEXT_METRIC),
                    )
                    views.setOnClickPendingIntent(
                        R.id.widgetRefresh,
                        broadcastPendingIntent(context, widgetId, ACTION_REFRESH),
                    )

                    manager.updateAppWidget(widgetId, views)
                }
            }
        }

        private fun broadcastPendingIntent(context: Context, widgetId: Int, action: String): PendingIntent {
            val intent = Intent(context, WeightWidgetProvider::class.java).apply {
                this.action = action
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            }
            return PendingIntent.getBroadcast(
                context,
                (widgetId.toString() + action).hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        private fun getSelectedMetricId(context: Context, widgetId: Int, options: List<String>): String {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val stored = prefs.getString("metric_$widgetId", null)
            return stored?.takeIf { it in options } ?: options.firstOrNull().orEmpty()
        }

        private fun shiftMetricSelection(context: Context, widgetId: Int, delta: Int) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val current = prefs.getString("metric_$widgetId", "weight_kg") ?: "weight_kg"
            val options = prefs.getStringSet("metric_options", setOf("weight_kg"))?.toList().orEmpty().ifEmpty { listOf("weight_kg") }
            val currentIndex = options.indexOf(current).takeIf { it >= 0 } ?: 0
            val nextIndex = (currentIndex + delta).mod(options.size)
            prefs.edit().putString("metric_$widgetId", options[nextIndex]).apply()
        }

        private fun saveMetricOptions(context: Context, options: List<String>) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putStringSet("metric_options", options.toSet())
                .apply()
        }

        private fun createSparklineBitmap(values: List<Double>): Bitmap {
            val width = 600
            val height = 180
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.TRANSPARENT)
            if (values.size < 2) return bitmap

            val minValue = values.minOrNull() ?: return bitmap
            val maxValue = values.maxOrNull() ?: return bitmap
            val range = max(maxValue - minValue, 0.1)

            val linePaint = Paint().apply {
                color = Color.WHITE
                strokeWidth = 8f
                style = Paint.Style.STROKE
                isAntiAlias = true
            }
            val fillPaint = Paint().apply {
                color = Color.argb(50, 255, 255, 255)
                strokeWidth = 1f
                style = Paint.Style.FILL_AND_STROKE
                isAntiAlias = true
            }

            val points = values.mapIndexed { index, value ->
                val x = 24f + (index.toFloat() / (values.size - 1).coerceAtLeast(1)) * (width - 48f)
                val normalized = ((value - minValue) / range).toFloat()
                val y = height - 24f - normalized * (height - 48f)
                x to y
            }

            val path = android.graphics.Path().apply {
                moveTo(points.first().first, points.first().second)
                points.drop(1).forEach { (x, y) -> lineTo(x, y) }
            }
            val fillPath = android.graphics.Path(path).apply {
                lineTo(points.last().first, height - 16f)
                lineTo(points.first().first, height - 16f)
                close()
            }
            canvas.drawPath(fillPath, fillPaint)
            canvas.drawPath(path, linePaint)
            return bitmap
        }
    }
}
