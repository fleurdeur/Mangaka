package app.kurenai.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import app.kurenai.MainActivity
import app.kurenai.R

class ContinueReadingWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val intent = Intent(context, MainActivity::class.java)
        val pending = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        appWidgetIds.forEach { id ->
            val views = RemoteViews(context.packageName, R.layout.widget_continue_reading).apply {
                setTextViewText(R.id.widget_title, "Continue reading")
                setTextViewText(R.id.widget_subtitle, "Open Kurenai to pick up your last chapter")
                setOnClickPendingIntent(R.id.widget_title, pending)
            }
            appWidgetManager.updateAppWidget(id, views)
        }
    }
}
