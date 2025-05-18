package io.businesscare.app.ui.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews
import io.businesscare.app.R

class ScheduleWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
        super.onUpdate(context, appWidgetManager, appWidgetIds)
    }

    private fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
        val serviceIntent = Intent(context, ScheduleWidgetService::class.java)
        serviceIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        serviceIntent.data = Uri.parse(serviceIntent.toUri(Intent.URI_INTENT_SCHEME))

        val views = RemoteViews(context.packageName, R.layout.widget_schedule_layout)
        views.setRemoteAdapter(R.id.list_view_widget_schedule, serviceIntent)
        views.setEmptyView(R.id.list_view_widget_schedule, R.id.text_view_widget_empty_message)

        appWidgetManager.updateAppWidget(appWidgetId, views)
    }
}