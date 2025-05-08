package com.example.businesscare.ui.booking

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
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

class AvailableServicesActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAvailableServicesBinding
    private val viewModel: AvailableServicesViewModel by viewModels()
    private lateinit var servicesAdapter: AvailableServicesAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAvailableServicesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()
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

    private fun setupObservers() {
        viewModel.servicesList.observe(this) { services ->
            servicesAdapter.submitList(services)
        }

        viewModel.listDataStatus.observe(this) { status ->
            binding.progressBarAvailable.visibility = if (status == DataStatus.LOADING) View.VISIBLE else View.GONE
            binding.tvAvailableMessage.visibility = View.GONE
            binding.rvAvailableServices.visibility = if (status == DataStatus.SUCCESS) View.VISIBLE else View.INVISIBLE

            when (status) {
                DataStatus.ERROR -> {
                    binding.tvAvailableMessage.visibility = View.VISIBLE
                    binding.tvAvailableMessage.text = viewModel.listErrorMessage.value ?: "Erreur inconnue"
                }
                DataStatus.EMPTY -> {
                    binding.tvAvailableMessage.visibility = View.VISIBLE
                    binding.tvAvailableMessage.text = "Aucun service ou événement disponible."
                }
                else -> {

                }
            }
        }
        viewModel.listErrorMessage.observe(this) { msg ->
            msg?.let { Toast.makeText(this, "Erreur chargement liste: $it", Toast.LENGTH_SHORT).show() }
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
                Toast.makeText(this, "Réservation créée avec succès!", Toast.LENGTH_LONG).show()
                viewModel.resetBookingStatus()
                finish()
            }
        }
        viewModel.bookingErrorMessage.observe(this) { errorMsg ->
            errorMsg?.let {
                Toast.makeText(this, "Erreur Réservation: $it", Toast.LENGTH_LONG).show()
                viewModel.resetBookingStatus()
            }
        }
    }

    private fun showBookingDialog(item: ServiceSummaryDto) {
        val calendar = Calendar.getInstance()
        val notesView = LayoutInflater.from(this).inflate(R.layout.dialog_booking_notes, null)
        val etNotes = notesView.findViewById<EditText>(R.id.etBookingNotes)

        if (item.isEvent && item.startDate != null) {
            calendar.time = item.startDate

            AlertDialog.Builder(this)
                .setTitle("Confirmer participation ?")
                .setMessage("Participer à \"${item.title}\"\nLe ${android.text.format.DateFormat.getLongDateFormat(this).format(calendar.time)} à ${android.text.format.DateFormat.getTimeFormat(this).format(calendar.time)} ?")
                .setView(notesView)
                .setPositiveButton("Confirmer") { _, _ ->
                    val notes = etNotes.text.toString().trim()
                    viewModel.createBooking(item, calendar.timeInMillis, if (notes.isEmpty()) null else notes)
                }
                .setNegativeButton("Annuler", null)
                .show()

        } else {
            calendar.add(Calendar.DAY_OF_YEAR, 1)
            calendar.set(Calendar.HOUR_OF_DAY, 10)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)

            val dateSetListener = DatePickerDialog.OnDateSetListener { _, year, monthOfYear, dayOfMonth ->
                calendar.set(Calendar.YEAR, year)
                calendar.set(Calendar.MONTH, monthOfYear)
                calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth)

                TimePickerDialog(this,
                    { _, hourOfDay, minute ->
                        calendar.set(Calendar.HOUR_OF_DAY, hourOfDay)
                        calendar.set(Calendar.MINUTE, minute)

                        AlertDialog.Builder(this)
                            .setTitle("Confirmer réservation ?")
                            .setMessage("Réserver \"${item.title}\"\nLe ${android.text.format.DateFormat.getLongDateFormat(this).format(calendar.time)} à ${android.text.format.DateFormat.getTimeFormat(this).format(calendar.time)} ?")
                            .setView(notesView)
                            .setPositiveButton("Réserver") { _, _ ->
                                val notes = etNotes.text.toString().trim()
                                viewModel.createBooking(item, calendar.timeInMillis, if (notes.isEmpty()) null else notes)
                            }
                            .setNegativeButton("Annuler", null)
                            .show()

                    }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), true
                ).show()
            }

            DatePickerDialog(this, dateSetListener,
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            ).show()
        }
    }
}