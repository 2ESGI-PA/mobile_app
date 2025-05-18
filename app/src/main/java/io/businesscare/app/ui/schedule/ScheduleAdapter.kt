package io.businesscare.app.ui.schedule

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import io.businesscare.app.R
import io.businesscare.app.data.model.BookingItem
import io.businesscare.app.databinding.ItemBookingBinding
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class ScheduleAdapter(
    private val onItemClicked: (BookingItem) -> Unit,
    private val onCancelClicked: (BookingItem) -> Unit,
    private val onConfirmClicked: ((BookingItem) -> Unit)?,
    private val onManageClicked: ((BookingItem) -> Unit)?,
    private val onRebookClicked: ((BookingItem) -> Unit)?
) : ListAdapter<BookingItem, ScheduleAdapter.BookingViewHolder>(BookingDiffCallback()) {

    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BookingViewHolder {
        val binding = ItemBookingBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return BookingViewHolder(binding, timeFormat, onItemClicked, onCancelClicked, onConfirmClicked, onManageClicked, onRebookClicked)
    }

    override fun onBindViewHolder(holder: BookingViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class BookingViewHolder(
        private val binding: ItemBookingBinding,
        private val timeFormat: SimpleDateFormat,
        private val onItemClickedCallback: (BookingItem) -> Unit,
        private val onCancelClickedCallback: (BookingItem) -> Unit,
        private val onConfirmClickedCallback: ((BookingItem) -> Unit)?,
        private val onManageClickedCallback: ((BookingItem) -> Unit)?,
        private val onRebookClickedCallback: ((BookingItem) -> Unit)?
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: BookingItem) {
            val context = binding.root.context

            binding.buttonViewDetails.visibility = View.VISIBLE
            binding.buttonViewDetails.isEnabled = true
            binding.root.alpha = 1.0f
            binding.buttonViewDetails.setTextColor(ContextCompat.getColor(context, R.color.primary_purple))

            val startTime = timeFormat.format(item.bookingDate)
            val durationMinutes = item.durationMinutes ?: 60
            val calendar = Calendar.getInstance().apply {
                time = item.bookingDate
                add(Calendar.MINUTE, durationMinutes)
            }
            val endTime = timeFormat.format(calendar.time)
            binding.textViewBookingTime.text = "$startTime - $endTime"

            val title = when {
                !item.serviceTitle.isNullOrBlank() -> item.serviceTitle
                !item.itemType.isNullOrBlank() -> context.getString(R.string.booking_item_type_prefix, item.itemType)
                else -> context.getString(R.string.default_booking_title)
            }

            binding.textViewBookingLocation.text = item.location ?: context.getString(R.string.location_not_specified)
            val clientNameText = item.clientName ?: context.getString(R.string.default_client_name)

            var itemSpecificBackgroundColor = ContextCompat.getColor(context, R.color.muted_green)

            when (item.status.uppercase(Locale.ROOT)) {
                "CONFIRMED" -> {
                    itemSpecificBackgroundColor = ContextCompat.getColor(context, R.color.primary_purple)
                    if (item.bookingDate.after(Date())) {
                        if (onManageClickedCallback != null) {
                            binding.buttonViewDetails.text = context.getString(R.string.manage_booking)
                            binding.buttonViewDetails.setOnClickListener { onManageClickedCallback.invoke(item) }
                        } else {
                            binding.buttonViewDetails.text = context.getString(R.string.action_cancel)
                            binding.buttonViewDetails.setOnClickListener { onCancelClickedCallback(item) }
                        }
                    } else {
                        binding.buttonViewDetails.text = context.getString(R.string.action_details)
                        binding.buttonViewDetails.setOnClickListener { onItemClickedCallback(item) }
                        binding.buttonViewDetails.isEnabled = true
                    }
                }
                "PENDING" -> {
                    itemSpecificBackgroundColor = ContextCompat.getColor(context, R.color.accent_pink)
                    binding.buttonViewDetails.text = context.getString(R.string.confirm_button_text)
                    if (onConfirmClickedCallback != null) {
                        binding.buttonViewDetails.setOnClickListener { onConfirmClickedCallback.invoke(item) }
                    } else {
                        binding.buttonViewDetails.text = context.getString(R.string.action_details)
                        binding.buttonViewDetails.setOnClickListener { onItemClickedCallback(item) }
                    }
                }
                "CANCELLED", "CANCELLED_EMPLOYEE" -> {
                    itemSpecificBackgroundColor = ContextCompat.getColor(context, R.color.light_purple)
                    binding.root.alpha = 0.7f
                    binding.buttonViewDetails.text = context.getString(R.string.action_reregister)
                    binding.buttonViewDetails.isEnabled = true
                    binding.buttonViewDetails.setOnClickListener { onRebookClickedCallback?.invoke(item) }
                }
                else -> {
                    binding.buttonViewDetails.text = context.getString(R.string.action_details)
                    binding.buttonViewDetails.setOnClickListener { onItemClickedCallback(item) }
                }
            }
            binding.textViewBookingTime.backgroundTintList = ColorStateList.valueOf(itemSpecificBackgroundColor)

            var currentDisplayTitle = title
            val todayCal = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }
            val appointmentCal = Calendar.getInstance().apply {
                time = item.bookingDate
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }

            if (appointmentCal.timeInMillis == todayCal.timeInMillis) {
                if (!currentDisplayTitle.startsWith("🔹 ")) {
                    currentDisplayTitle = "🔹 $currentDisplayTitle"
                }
            }
            binding.textViewBookingTitle.text = currentDisplayTitle

            if (item.bookingDate.after(Date()) && item.status.uppercase(Locale.ROOT) != "CANCELLED" && item.status.uppercase(Locale.ROOT) != "CANCELLED_EMPLOYEE") {
                val diffMillis = item.bookingDate.time - System.currentTimeMillis()
                val diffHours = TimeUnit.MILLISECONDS.toHours(diffMillis)

                val timeRemainingText = when {
                    diffHours < 1 -> {
                        val diffMinutes = TimeUnit.MILLISECONDS.toMinutes(diffMillis)
                        context.getString(R.string.time_remaining_minutes, diffMinutes)
                    }
                    diffHours < 24 -> context.getString(R.string.time_remaining_hours, diffHours)
                    else -> null
                }
                if (timeRemainingText != null) {
                    binding.textViewBookingClient.text = context.getString(R.string.client_with_time_remaining, clientNameText, timeRemainingText)
                } else {
                    binding.textViewBookingClient.text = clientNameText
                }
            } else {
                binding.textViewBookingClient.text = clientNameText
            }
        }
    }

    class BookingDiffCallback : DiffUtil.ItemCallback<BookingItem>() {
        override fun areItemsTheSame(oldItem: BookingItem, newItem: BookingItem): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: BookingItem, newItem: BookingItem): Boolean {
            return oldItem == newItem
        }
    }
}