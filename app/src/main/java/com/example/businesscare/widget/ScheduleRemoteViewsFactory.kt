package com.example.businesscare.widget

import android.content.Context
import android.content.Intent
import android.os.Binder
import android.util.Log
import android.widget.AdapterView
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.example.businesscare.R
import com.example.businesscare.data.local.TokenManager
import com.example.businesscare.data.model.BookingItem
import com.example.businesscare.data.network.ApiClient
import kotlinx.coroutines.runBlocking
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class ScheduleRemoteViewsFactory(private val context: Context) : RemoteViewsService.RemoteViewsFactory {

    private var bookingItems: List<BookingItem> = emptyList()
    private lateinit var tokenManager: TokenManager
    private var apiService = ApiClient.create(context.applicationContext)
    private val outputDateFormat = SimpleDateFormat("dd MMMM yyyy", Locale.getDefault())
    private val outputTimeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())


    override fun onCreate() {
        tokenManager = TokenManager(context)
    }

    override fun onDataSetChanged() {
        val identityToken = Binder.clearCallingIdentity()
        try {
            runBlocking {
                try {
                    if (tokenManager.getToken() != null) {
                        bookingItems = apiService.getSchedule().sortedBy { it.bookingDate }
                        Log.d("WidgetFactory", "Bookings fetched: ${bookingItems.size}")
                    } else {
                        bookingItems = emptyList()
                        Log.d("WidgetFactory", "No token, bookings empty for widget")
                    }
                } catch (e: Exception) {
                    Log.e("WidgetFactory", "Error fetching schedule for widget: ${e.message}", e)
                    bookingItems = emptyList()
                }
            }
        } finally {
            Binder.restoreCallingIdentity(identityToken)
        }
    }

    override fun onDestroy() {
        bookingItems = emptyList()
    }

    override fun getCount(): Int = bookingItems.size

    override fun getViewAt(position: Int): RemoteViews {
        if (position == AdapterView.INVALID_POSITION || position >= bookingItems.size) {
            return RemoteViews(context.packageName, R.layout.widget_item_booking)
        }

        val item = bookingItems[position]
        val views = RemoteViews(context.packageName, R.layout.widget_item_booking)

        views.setTextViewText(R.id.tvWidgetItemTitle, item.serviceTitle ?: context.getString(R.string.widget_untitled_booking))

        try {

            val dateStr = outputDateFormat.format(item.bookingDate)
            val timeStr = outputTimeFormat.format(item.bookingDate)
            views.setTextViewText(R.id.tvWidgetItemDateTime, "$dateStr - $timeStr")
        } catch (e: Exception) {
            views.setTextViewText(R.id.tvWidgetItemDateTime, context.getString(R.string.widget_date_unavailable))
            Log.e("WidgetFactory", "Error formatting date for widget item: ${item.bookingDate}", e)
        }


        views.setTextViewText(R.id.tvWidgetItemType, context.getString(R.string.widget_item_type_prefix) + " " + item.itemType.uppercase(Locale.getDefault()))

        val fillInIntent = Intent()
        views.setOnClickFillInIntent(R.id.tvWidgetItemTitle, fillInIntent)

        return views
    }

    override fun getLoadingView(): RemoteViews {
        return RemoteViews(context.packageName, R.layout.widget_item_booking).apply {
            setTextViewText(R.id.tvWidgetItemTitle, context.getString(R.string.widget_loading_data))
            setTextViewText(R.id.tvWidgetItemDateTime, "")
            setTextViewText(R.id.tvWidgetItemType, "")
        }
    }

    override fun getViewTypeCount(): Int = 1

    override fun getItemId(position: Int): Long = position.toLong()

    override fun hasStableIds(): Boolean = false

}