package com.darren.anitrackpulse.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
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
                val next = entries.firstOrNull { (it.nextAiringAt ?: 0L) > nowSeconds }

                val summary = if (airedCount > 0) {
                    "$airedCount new episode${if (airedCount == 1) "" else "s"} to watch"
                } else {
                    "No new episodes yet"
                }
                val nextLine = next?.let { "Next: ${it.title} · Ep ${it.nextEpisode ?: "?"} · ${airingCountdown(it.nextAiringAt)}" }
                    ?: "No upcoming episodes"

                val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                appWidgetIds.forEach { widgetId ->
                    val views = RemoteViews(context.packageName, R.layout.widget_watchlist)
                    views.setTextViewText(R.id.widget_summary, summary)
                    views.setTextViewText(R.id.widget_next, nextLine)
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
