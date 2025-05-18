package io.businesscare.app.ui.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import io.businesscare.app.R
import io.businesscare.app.data.model.BookingItem
import io.businesscare.app.data.network.ApiClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.text.SimpleDateFormat
import java.util.Locale

class ScheduleRemoteViewsFactory(
    private val context: Context,
    private val intent: Intent
) : RemoteViewsService.RemoteViewsFactory {

    private var bookingItems: List<BookingItem> = emptyList()
    private val appWidgetId: Int = intent.getIntExtra(
        AppWidgetManager.EXTRA_APPWIDGET_ID,
        AppWidgetManager.INVALID_APPWIDGET_ID
    )
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val job = Job()
    private val coroutineScope = CoroutineScope(Dispatchers.IO + job)

    override fun onCreate() {
        loadData()
    }

    override fun onDataSetChanged() {
        loadData()
    }

    private fun loadData() {
        runBlocking(coroutineScope.coroutineContext) {
            try {
                val apiService = ApiClient.create(context)
                val newBookingItems = apiService.getSchedule().sortedBy { it.bookingDate }
                bookingItems = newBookingItems
            } catch (e: Exception) {
                e.printStackTrace()
                bookingItems = emptyList()
            }
            val appWidgetManager = AppWidgetManager.getInstance(context)
            appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.list_view_widget_schedule)
        }
    }

    override fun onDestroy() {
        bookingItems = emptyList()
        job.cancel()
    }

    override fun getCount(): Int {
        return bookingItems.size
    }

    override fun getViewAt(position: Int): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_item_booking)
        if (position >= bookingItems.size || bookingItems.isEmpty()) {
            return RemoteViews(context.packageName, R.layout.widget_item_booking).apply {
                setTextViewText(R.id.text_view_widget_item_title, context.getString(R.string.widget_loading_or_empty))
                setTextViewText(R.id.text_view_widget_item_time, "")
            }
        }
        val item = bookingItems[position]

        val timeString = try {
            timeFormat.format(item.bookingDate)
        } catch (e: Exception) {
            "N/A"
        }
        views.setTextViewText(R.id.text_view_widget_item_time, timeString)

        val title = item.serviceTitle ?: item.itemType ?: context.getString(R.string.default_booking_title)
        views.setTextViewText(R.id.text_view_widget_item_title, title)
        return views
    }

    override fun getLoadingView(): RemoteViews? {

        val loadingViews = RemoteViews(context.packageName, R.layout.widget_item_booking)
        loadingViews.setTextViewText(R.id.text_view_widget_item_title, context.getString(R.string.widget_loading))
        loadingViews.setTextViewText(R.id.text_view_widget_item_time, "...")
        return loadingViews
    }

    override fun getViewTypeCount(): Int {
        return 1
    }

    override fun getItemId(position: Int): Long {
        return if (position < bookingItems.size && bookingItems.isNotEmpty()) bookingItems[position].id.toLong() else position.toLong()
    }

    override fun hasStableIds(): Boolean {
        return true
    }
}