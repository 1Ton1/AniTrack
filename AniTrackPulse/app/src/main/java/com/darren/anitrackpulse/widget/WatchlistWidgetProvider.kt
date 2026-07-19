package com.darren.anitrackpulse.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.darren.anitrackpulse.MainActivity
import com.darren.anitrackpulse.R
import com.darren.anitrackpulse.airingCountdown
import com.darren.anitrackpulse.data.AppDatabase

/** Classic home-screen widget summarising upcoming episodes across the saved watchlist. */
class WatchlistWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        // Room queries must not run on the main thread; do the DB read on a worker thread.
        val pending = goAsync()
        Thread {
            try {
                val entries = AppDatabase.getDatabase(context).animeDao().getUpcomingBlocking()
                val nowSeconds = System.currentTimeMillis() / 1000
                val airedCount = entries.count { (it.nextAiringAt ?: 0L) in 1..nowSeconds }
                val upcoming = entries.filter { (it.nextAiringAt ?: 0L) > nowSeconds }.take(3)

                val summary = if (airedCount > 0) {
                    "$airedCount new to watch"
                } else {
                    "Up next"
                }

                val rowContainers = intArrayOf(R.id.widget_row_0, R.id.widget_row_1, R.id.widget_row_2)
                val rowTitles = intArrayOf(R.id.widget_row_0_title, R.id.widget_row_1_title, R.id.widget_row_2_title)
                val rowTimes = intArrayOf(R.id.widget_row_0_time, R.id.widget_row_1_time, R.id.widget_row_2_time)

                val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                appWidgetIds.forEach { widgetId ->
                    val views = RemoteViews(context.packageName, R.layout.widget_watchlist)
                    views.setTextViewText(R.id.widget_summary, summary)

                    for (i in rowContainers.indices) {
                        val entry = upcoming.getOrNull(i)
                        if (entry != null) {
                            views.setViewVisibility(rowContainers[i], View.VISIBLE)
                            views.setTextViewText(rowTitles[i], "${entry.title} · Ep ${entry.nextEpisode ?: "?"}")
                            views.setTextViewText(rowTimes[i], airingCountdown(entry.nextAiringAt))
                        } else {
                            views.setViewVisibility(rowContainers[i], View.GONE)
                        }
                    }
                    views.setViewVisibility(R.id.widget_empty, if (upcoming.isEmpty()) View.VISIBLE else View.GONE)

                    val intent = Intent(context, MainActivity::class.java)
                    val pi = PendingIntent.getActivity(context, 0, intent, flags)
                    views.setOnClickPendingIntent(R.id.widget_root, pi)
                    appWidgetManager.updateAppWidget(widgetId, views)
                }
            } finally {
                pending.finish()
            }
        }.start()
    }
}
