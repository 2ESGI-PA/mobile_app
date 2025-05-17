package io.businesscare.app.ui.schedule

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.applandeo.materialcalendarview.CalendarView
import com.applandeo.materialcalendarview.EventDay
import com.applandeo.materialcalendarview.listeners.OnDayClickListener
import io.businesscare.app.R
import io.businesscare.app.data.model.BookingItem
import io.businesscare.app.data.model.BookingRequestDto
import io.businesscare.app.databinding.ActivityScheduleBinding
import io.businesscare.app.ui.booking.AvailableServicesActivity
import io.businesscare.app.ui.settings.SettingsActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import io.businesscare.app.util.AppConstants

const val EXTRA_SERVICE_TITLE_TO_REBOOK = AppConstants.EXTRA_SERVICE_TITLE_TO_REBOOK
const val ITEM_TYPE_EVENT_FIXED = AppConstants.ITEM_TYPE_EVENT_FIXED


class ScheduleActivity : AppCompatActivity() {

    private lateinit var binding: ActivityScheduleBinding
    private val scheduleViewModel: ScheduleViewModel by viewModels()
    private lateinit var scheduleAdapter: ScheduleAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityScheduleBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbarSchedule)
        supportActionBar?.title = getString(R.string.schedule_title_toolbar)

        setupRecyclerView()
        setupCalendarViewListener()
        setupFabListener()
        setupObservers()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.schedule_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun formatDate(date: Date?, pattern: String = "dd/MM/yyyy"): String {
        return date?.let { SimpleDateFormat(pattern, Locale.getDefault()).format(it) } ?: getString(R.string.not_available_short)
    }

    private fun showBookingDetailsDialog(bookingItem: BookingItem) {
        val detailsMessage = buildString {
            appendLine("${getString(R.string.booking_field_service)} ${bookingItem.serviceTitle ?: bookingItem.itemType}")
            appendLine("${getString(R.string.booking_field_client)} ${bookingItem.clientName ?: getString(R.string.not_available_short)}")
            appendLine("${getString(R.string.booking_field_date)} ${formatDate(bookingItem.bookingDate)}")
            appendLine("${getString(R.string.booking_field_time)} ${formatDate(bookingItem.bookingDate, "HH:mm")}")

            bookingItem.durationMinutes?.let {
                appendLine("${getString(R.string.booking_field_duration)} ${getString(R.string.minutes_format, it)}")
            }
            appendLine("${getString(R.string.booking_field_status)} ${bookingItem.status}")

            bookingItem.location?.takeIf { it.isNotBlank() }?.let {
                appendLine("${getString(R.string.booking_field_location)} $it")
            }
            bookingItem.providerName?.takeIf { it.isNotBlank() }?.let {
                appendLine("${getString(R.string.booking_field_provider)} $it")
            }
            bookingItem.notes?.takeIf { it.isNotBlank() }?.let {
                appendLine("${getString(R.string.booking_field_notes)} $it")
            }
            val createdAtLabel = getString(R.string.booking_field_created_at)
            val formattedCreatedAt = formatDate(bookingItem.createdAt)
            appendLine("$createdAtLabel $formattedCreatedAt")
        }

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.booking_details_title))
            .setMessage(detailsMessage)
            .setPositiveButton(getString(R.string.dialog_close_button), null)
            .show()
    }

    private fun showManageOptionsDialog(bookingItem: BookingItem) {
        val options = arrayOf(
            getString(R.string.manage_booking_option_details),
            getString(R.string.manage_booking_option_cancel)
        )
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.manage_booking_dialog_title))
            .setItems(options) { dialog, which ->
                when (which) {
                    0 -> showBookingDetailsDialog(bookingItem)
                    1 -> showCancelConfirmationDialog(bookingItem.id)
                }
                dialog.dismiss()
            }
            .setNegativeButton(getString(R.string.dialog_back_button)) { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun setupRecyclerView() {
        scheduleAdapter = ScheduleAdapter(
            onItemClicked = { bookingItem ->
                showBookingDetailsDialog(bookingItem)
            },
            onCancelClicked = { bookingItem ->
                showCancelConfirmationDialog(bookingItem.id)
            },
            onConfirmClicked = { bookingItem ->
                Toast.makeText(this, getString(R.string.toast_confirm_action_for, bookingItem.serviceTitle ?: bookingItem.itemType), Toast.LENGTH_SHORT).show()
            },
            onManageClicked = { bookingItem ->
                showManageOptionsDialog(bookingItem)
            },
            onRebookClicked = { bookingItem ->
                val serviceTitleForLookup = bookingItem.serviceTitle ?: bookingItem.itemType

                if (bookingItem.itemType.equals(ITEM_TYPE_EVENT_FIXED, ignoreCase = true)) {
                    Toast.makeText(this, getString(R.string.rebooking_attempt_for, serviceTitleForLookup), Toast.LENGTH_LONG).show()

                    val dateForRequest = bookingItem.bookingDate
                    val formattedDate = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
                        timeZone = TimeZone.getTimeZone("UTC")
                    }.format(dateForRequest)


                    val request = BookingRequestDto(
                        serviceId = null,
                        eventId = bookingItem.id,
                        bookingDate = formattedDate,
                        notes = bookingItem.notes
                    )
                    scheduleViewModel.rebookFixedEvent(request, serviceTitleForLookup)
                } else {
                    Toast.makeText(this, getString(R.string.redirecting_to_reschedule, serviceTitleForLookup), Toast.LENGTH_LONG).show()
                    val intent = Intent(this, AvailableServicesActivity::class.java)

                    if (serviceTitleForLookup.isNotBlank()) {
                        intent.putExtra(EXTRA_SERVICE_TITLE_TO_REBOOK, serviceTitleForLookup)
                    }

                    bookingItem.notes?.let {
                        intent.putExtra(AppConstants.EXTRA_NOTES_TO_REBOOK, it)
                    }

                    startActivity(intent)
                }
            }
        )
        binding.recyclerViewSchedule.apply {
            adapter = scheduleAdapter
            layoutManager = LinearLayoutManager(this@ScheduleActivity)
        }
    }

    private fun setupCalendarViewListener() {
        (binding.calendarView as? CalendarView)?.setOnDayClickListener(object : OnDayClickListener {
            override fun onDayClick(eventDay: EventDay) {
                scheduleViewModel.filterScheduleByDate(eventDay.calendar.timeInMillis)
            }
        }) ?: run {
            Toast.makeText(this,getString(R.string.calendar_init_error), Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupFabListener() {
        binding.fabAddBooking.setOnClickListener {
            startActivity(Intent(this, AvailableServicesActivity::class.java))
        }
    }

    private fun showCancelConfirmationDialog(bookingId: Int) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.confirm_cancellation_title))
            .setMessage(getString(R.string.confirm_cancellation_message))
            .setPositiveButton(getString(R.string.yes_cancel)) { _, _ ->
                scheduleViewModel.cancelBooking(bookingId)
            }
            .setNegativeButton(getString(R.string.no_keep_booking), null)
            .show()
    }

    private fun setupObservers() {
        scheduleViewModel.displayedScheduleList.observe(this) { bookings ->
            scheduleAdapter.submitList(bookings)
        }

        scheduleViewModel.dataStatus.observe(this) { status ->
            binding.progressBarSchedule.visibility = if (status == DataStatus.LOADING) View.VISIBLE else View.GONE
            binding.textViewScheduleMessage.visibility = View.GONE
            binding.recyclerViewSchedule.visibility = if (status == DataStatus.SUCCESS && !scheduleViewModel.displayedScheduleList.value.isNullOrEmpty()) View.VISIBLE else View.INVISIBLE

            when (status) {
                DataStatus.ERROR -> {
                    binding.textViewScheduleMessage.visibility = View.VISIBLE
                    binding.textViewScheduleMessage.text = scheduleViewModel.errorMessage.value ?: getString(R.string.unknown_error)
                    binding.recyclerViewSchedule.visibility = View.GONE
                }
                DataStatus.EMPTY -> {
                    binding.textViewScheduleMessage.visibility = View.VISIBLE
                    binding.textViewScheduleMessage.text = if (scheduleViewModel.isDateFilterActive.value == true) {
                        getString(R.string.no_event_for_this_day)
                    } else {
                        getString(R.string.your_schedule_is_empty)
                    }
                    binding.recyclerViewSchedule.visibility = View.GONE
                }
                DataStatus.SUCCESS -> {
                    if (scheduleViewModel.displayedScheduleList.value.isNullOrEmpty()){
                        binding.textViewScheduleMessage.visibility = View.VISIBLE
                        binding.textViewScheduleMessage.text = if (scheduleViewModel.isDateFilterActive.value == true) {
                            getString(R.string.no_event_for_this_day)
                        } else {
                            getString(R.string.your_schedule_is_empty)
                        }
                        binding.recyclerViewSchedule.visibility = View.GONE
                    } else {
                        binding.textViewScheduleMessage.visibility = View.GONE
                        binding.recyclerViewSchedule.visibility = View.VISIBLE
                    }
                }
                else -> { }
            }
        }

        scheduleViewModel.errorMessage.observe(this) { message ->
            message?.let {
                if (scheduleViewModel.dataStatus.value == DataStatus.ERROR) {
                    Toast.makeText(this, it, Toast.LENGTH_LONG).show()
                }
            }
        }

        scheduleViewModel.eventDays.observe(this) { eventDays ->
            (binding.calendarView as? CalendarView)?.setEvents(eventDays) ?: run {
                Toast.makeText(this, getString(R.string.calendar_layout_error), Toast.LENGTH_SHORT).show()
            }
        }

        scheduleViewModel.cancelStatus.observe(this) { status ->
            if (status == CancelStatus.SUCCESS) {
                Toast.makeText(this, getString(R.string.booking_cancelled_success), Toast.LENGTH_SHORT).show()
                scheduleViewModel.resetCancelStatus()
            }
        }

        scheduleViewModel.cancelErrorMessage.observe(this) { errorMsg ->
            errorMsg?.let {
                Toast.makeText(this, it, Toast.LENGTH_LONG).show()
                scheduleViewModel.resetCancelStatus()
            }
        }

        scheduleViewModel.rebookEventStatus.observe(this) { status ->
            if (status == RebookStatus.SUCCESS) {
                Toast.makeText(this, getString(R.string.rebook_event_success), Toast.LENGTH_SHORT).show()
                scheduleViewModel.resetRebookEventStatus()
            }
        }
        scheduleViewModel.rebookEventErrorMessage.observe(this) { errorMsg ->
            errorMsg?.let {
                Toast.makeText(this, it, Toast.LENGTH_LONG).show()
                scheduleViewModel.resetRebookEventStatus()
            }
        }
    }

    override fun onBackPressed() {
        if (scheduleViewModel.isDateFilterActive.value == true) {
            scheduleViewModel.clearDateFilter()
        } else {
            super.onBackPressed()
        }
    }

    override fun onResume() {
        super.onResume()
        if (scheduleViewModel.dataStatus.value != DataStatus.LOADING) {
            scheduleViewModel.fetchSchedule()
        }
    }
}