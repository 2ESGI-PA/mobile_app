package com.businesscare.app.ui.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import io.businesscare.app.R
import io.businesscare.app.data.model.BookingItem
import io.businesscare.app.data.network.ApiClient
import kotlinx.coroutines.runBlocking
import java.text.SimpleDateFormat
import java.util.Locale

class ScheduleRemoteViewsFactory(
    private val context: Context,
    intent: Intent
) : RemoteViewsService.RemoteViewsFactory {

    private var bookingItems: List<BookingItem> = emptyList()
    private val appWidgetId: Int = intent.getIntExtra(
        AppWidgetManager.EXTRA_APPWIDGET_ID,
        AppWidgetManager.INVALID_APPWIDGET_ID
    )
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    override fun onCreate() {
        loadData()
    }

    override fun onDataSetChanged() {
        loadData()
    }

    private fun loadData() {
        try {
            runBlocking {
                val apiService = ApiClient.create(context)
                bookingItems = apiService.getSchedule().sortedBy { it.bookingDate }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            bookingItems = emptyList()
        }
    }

    override fun onDestroy() {
        bookingItems = emptyList()
    }

    override fun getCount(): Int {
        return bookingItems.size
    }

    override fun getViewAt(position: Int): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_item_booking)
        if (position >= bookingItems.size) {
            return views
        }
        val item = bookingItems[position]

        val timeString = timeFormat.format(item.bookingDate)
        views.setTextViewText(R.id.text_view_widget_item_time, timeString)

        val title = item.serviceTitle ?: item.itemType ?: context.getString(R.string.default_booking_title)
        views.setTextViewText(R.id.text_view_widget_item_title, title)

        return views
    }

    override fun getLoadingView(): RemoteViews? {
        return null
    }

    override fun getViewTypeCount(): Int {
        return 1
    }

    override fun getItemId(position: Int): Long {
        return if (position < bookingItems.size) bookingItems[position].id.toLong() else position.toLong()
    }

    override fun hasStableIds(): Boolean {
        return true
    }
}