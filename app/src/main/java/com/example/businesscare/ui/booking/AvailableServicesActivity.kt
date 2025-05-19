package com.example.businesscare.ui.booking

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.businesscare.R
import com.example.businesscare.data.model.ServiceSummaryDto
import com.example.businesscare.databinding.ActivityAvailableServicesBinding
import com.example.businesscare.ui.schedule.DataStatus
import java.util.Calendar
import java.util.Date
import android.text.format.DateFormat

class AvailableServicesActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAvailableServicesBinding
    private val viewModel: AvailableServicesViewModel by viewModels()
    private lateinit var servicesAdapter: AvailableServicesAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAvailableServicesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()
        setupFilterControls()
        setupObservers()
    }

    private fun setupRecyclerView() {
        servicesAdapter = AvailableServicesAdapter(onItemClicked = { serviceOrEvent ->
            showBookingDialog(serviceOrEvent)
        })
        binding.rvAvailableServices.apply {
            adapter = servicesAdapter
            layoutManager = LinearLayoutManager(this@AvailableServicesActivity)
        }
    }

    private fun setupFilterControls() {
        binding.chipGroupFilterType.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.chipFilterAll -> viewModel.setFilterType(FilterType.ALL)
                R.id.chipFilterServices -> viewModel.setFilterType(FilterType.SERVICE)
                R.id.chipFilterEvents -> viewModel.setFilterType(FilterType.EVENT)
                else -> viewModel.setFilterType(FilterType.ALL)
            }
        }

        binding.btnFilterDate.setOnClickListener {
            val calendar = Calendar.getInstance()
            viewModel.currentFilterDate.value?.let {
                calendar.time = it
            }

            val datePickerDialog = DatePickerDialog(this,
                { _, year, monthOfYear, dayOfMonth ->
                    val selectedCalendar = Calendar.getInstance()
                    selectedCalendar.set(Calendar.YEAR, year)
                    selectedCalendar.set(Calendar.MONTH, monthOfYear)
                    selectedCalendar.set(Calendar.DAY_OF_MONTH, dayOfMonth)
                    selectedCalendar.set(Calendar.HOUR_OF_DAY, 0)
                    selectedCalendar.set(Calendar.MINUTE, 0)
                    selectedCalendar.set(Calendar.SECOND, 0)
                    selectedCalendar.set(Calendar.MILLISECOND, 0)
                    viewModel.setFilterDate(selectedCalendar.time)
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            )
            datePickerDialog.show()
        }

        binding.btnClearDateFilter.setOnClickListener {
            viewModel.setFilterDate(null)
        }
    }

    private fun setupObservers() {
        viewModel.servicesList.observe(this) { services ->
            servicesAdapter.submitList(services)
            if (services.isEmpty() && (viewModel.currentFilterType.value != FilterType.ALL || viewModel.currentFilterDate.value != null)) {
                binding.tvAvailableMessage.text = getString(R.string.no_results_for_filters)
                binding.tvAvailableMessage.visibility = View.VISIBLE
            } else if (services.isEmpty()) {
                binding.tvAvailableMessage.text = getString(R.string.no_services_available)
                binding.tvAvailableMessage.visibility = View.VISIBLE
            } else {
                binding.tvAvailableMessage.visibility = View.GONE
            }
        }

        viewModel.listDataStatus.observe(this) { status ->
            binding.progressBarAvailable.visibility = if (status == DataStatus.LOADING) View.VISIBLE else View.GONE
            binding.rvAvailableServices.visibility = if (status == DataStatus.SUCCESS || (status == DataStatus.EMPTY && (viewModel.currentFilterType.value != FilterType.ALL || viewModel.currentFilterDate.value != null))) View.VISIBLE else View.INVISIBLE

            when (status) {
                DataStatus.ERROR -> {
                    binding.tvAvailableMessage.visibility = View.VISIBLE
                    binding.tvAvailableMessage.text = viewModel.listErrorMessage.value ?: getString(R.string.unknown_error)
                }
                DataStatus.EMPTY -> {
                    if (viewModel.currentFilterType.value == FilterType.ALL && viewModel.currentFilterDate.value == null) {
                        binding.tvAvailableMessage.visibility = View.VISIBLE
                        binding.tvAvailableMessage.text = getString(R.string.no_services_available)
                    }
                }
                else -> Unit
            }
        }

        viewModel.listErrorMessage.observe(this) { msg ->
            if(msg != null && msg != getString(R.string.no_services_available) && msg != getString(R.string.no_results_for_filters)) {
                Toast.makeText(this, getString(R.string.error_loading_list, msg), Toast.LENGTH_SHORT).show()
            }
        }

        viewModel.currentFilterDate.observe(this) { date ->
            if (date != null) {
                binding.btnFilterDate.text = DateFormat.getMediumDateFormat(this).format(date)
                binding.btnClearDateFilter.visibility = View.VISIBLE
            } else {
                binding.btnFilterDate.text = getString(R.string.label_filter_date)
                binding.btnClearDateFilter.visibility = View.GONE
            }
        }

        viewModel.bookingStatus.observe(this) { status ->
            if (status == BookingStatusState.LOADING) {
                if(viewModel.listDataStatus.value != DataStatus.LOADING) {
                    binding.progressBarAvailable.visibility = View.VISIBLE
                }
            } else {
                if(viewModel.listDataStatus.value != DataStatus.LOADING) {
                    binding.progressBarAvailable.visibility = View.GONE
                }
            }

            if (status == BookingStatusState.SUCCESS) {
                Toast.makeText(this, getString(R.string.booking_success), Toast.LENGTH_LONG).show()
                viewModel.resetBookingStatus()
                finish()
            }
        }
        viewModel.bookingErrorMessage.observe(this) { errorMsg ->
            errorMsg?.let {
                Toast.makeText(this, getString(R.string.booking_error, it), Toast.LENGTH_LONG).show()
                viewModel.resetBookingStatus()
            }
        }
    }

    private fun showBookingDialog(item: ServiceSummaryDto) {
        val calendar = Calendar.getInstance()

        if (item.isEvent && item.startDate != null) {
            calendar.time = item.startDate
            showCustomBookingDialog(item, calendar.timeInMillis)
        } else {
            calendar.add(Calendar.DAY_OF_YEAR, 1)
            calendar.set(Calendar.HOUR_OF_DAY, 10)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)

            val datePickerDialog = DatePickerDialog(this,
                { _, year, monthOfYear, dayOfMonth ->
                    calendar.set(Calendar.YEAR, year)
                    calendar.set(Calendar.MONTH, monthOfYear)
                    calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth)

                    TimePickerDialog(this,
                        { _, hourOfDay, minute ->
                            calendar.set(Calendar.HOUR_OF_DAY, hourOfDay)
                            calendar.set(Calendar.MINUTE, minute)
                            showCustomBookingDialog(item, calendar.timeInMillis)
                        },
                        calendar.get(Calendar.HOUR_OF_DAY),
                        calendar.get(Calendar.MINUTE),
                        DateFormat.is24HourFormat(this)
                    ).show()
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            )
            datePickerDialog.datePicker.minDate = System.currentTimeMillis()
            datePickerDialog.show()
        }
    }

    private fun showCustomBookingDialog(item: ServiceSummaryDto, selectedTimestamp: Long) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_booking_notes, null)
        val tvItemTitle = dialogView.findViewById<TextView>(R.id.tvDialogItemTitle)
        val tvItemDateTime = dialogView.findViewById<TextView>(R.id.tvDialogItemDateTime)
        val etNotes = dialogView.findViewById<EditText>(R.id.etBookingNotes)
        val btnSave = dialogView.findViewById<Button>(R.id.btnSaveNotes)
        val btnCancel = dialogView.findViewById<Button>(R.id.btnCancelNotes)

        val builder = AlertDialog.Builder(this)
        builder.setView(dialogView)
        val dialog = builder.create()

        tvItemTitle.text = item.title
        val dateTimeCalendar = Calendar.getInstance().apply { timeInMillis = selectedTimestamp }
        val dateString = DateFormat.getLongDateFormat(this).format(dateTimeCalendar.time)
        val timeString = DateFormat.getTimeFormat(this).format(dateTimeCalendar.time)
        tvItemDateTime.text = getString(R.string.date_time_format, dateString, timeString)

        btnSave.setOnClickListener {
            val notes = etNotes.text.toString().trim()
            viewModel.createBooking(item, selectedTimestamp, if (notes.isEmpty()) null else notes)
            dialog.dismiss()
        }

        btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }
}