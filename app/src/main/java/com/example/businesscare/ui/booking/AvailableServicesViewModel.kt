package com.example.businesscare.ui.booking

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.businesscare.data.model.BookingItem
import com.example.businesscare.data.model.BookingRequestDto
import com.example.businesscare.data.model.ServiceSummaryDto
import com.example.businesscare.data.network.ApiClient
import com.example.businesscare.ui.schedule.DataStatus
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

enum class BookingStatusState { IDLE, LOADING, SUCCESS, ERROR }

class AvailableServicesViewModel(application: Application) : AndroidViewModel(application) {

    private val apiService = ApiClient.create(application)

    private val _servicesList = MutableLiveData<List<ServiceSummaryDto>>()
    val servicesList: LiveData<List<ServiceSummaryDto>> = _servicesList

    private val _listDataStatus = MutableLiveData<DataStatus>(DataStatus.IDLE)
    val listDataStatus: LiveData<DataStatus> = _listDataStatus

    private val _listErrorMessage = MutableLiveData<String?>()
    val listErrorMessage: LiveData<String?> = _listErrorMessage

    private val _bookingStatus = MutableLiveData<BookingStatusState>(BookingStatusState.IDLE)
    val bookingStatus: LiveData<BookingStatusState> = _bookingStatus

    private val _bookingErrorMessage = MutableLiveData<String?>()
    val bookingErrorMessage: LiveData<String?> = _bookingErrorMessage

    private var currentUserBookings: List<BookingItem> = emptyList()

    init {
        fetchAvailableServices()
    }

    fun fetchAvailableServices() {
        _listDataStatus.value = DataStatus.LOADING
        _listErrorMessage.value = null

        viewModelScope.launch {
            try {
                val services = apiService.getAvailableServices()
                if (services.isEmpty()) {
                    _servicesList.postValue(emptyList())
                    _listDataStatus.postValue(DataStatus.EMPTY)
                } else {
                    _servicesList.postValue(services)
                    _listDataStatus.postValue(DataStatus.SUCCESS)
                }
            } catch (e: HttpException) {
                val errorBody = e.response()?.errorBody()?.string()
                _listErrorMessage.postValue("Erreur ${e.code()}: ${errorBody ?: e.message()}")
                _listDataStatus.postValue(DataStatus.ERROR)
            } catch (e: IOException) {
                _listErrorMessage.postValue("Erreur réseau.")
                _listDataStatus.postValue(DataStatus.ERROR)
            } catch (e: Exception) {
                _listErrorMessage.postValue("Erreur inconnue: ${e.message}")
                _listDataStatus.postValue(DataStatus.ERROR)
            }
        }
    }

    private suspend fun fetchCurrentUserBookings(): Boolean {
        try {
            currentUserBookings = apiService.getSchedule().sortedBy { it.bookingDate }
            return true
        } catch (e: HttpException) {
            _bookingErrorMessage.postValue("Erreur vérification (HTTP ${e.code()}): Impossible de récupérer les réservations existantes.")
            return false
        } catch (e: IOException) {
            _bookingErrorMessage.postValue("Erreur vérification (Réseau): Impossible de récupérer les réservations existantes.")
            return false
        } catch (e: Exception) {
            _bookingErrorMessage.postValue("Erreur vérification: Impossible de récupérer les réservations existantes.")
            return false
        }
    }

    fun createBooking(itemToBook: ServiceSummaryDto, selectedDateTimeMillis: Long, notes: String?) {
        viewModelScope.launch {
            _bookingStatus.value = BookingStatusState.LOADING
            _bookingErrorMessage.value = null

            if (!fetchCurrentUserBookings()) {
                _bookingStatus.postValue(BookingStatusState.ERROR)
                return@launch
            }

            val selectedDateForComparison = Date(selectedDateTimeMillis)

            if (itemToBook.isEvent) {
                val alreadyBookedEvent = currentUserBookings.any { booking ->
                    (booking.itemType.equals("EVENT", ignoreCase = true) || booking.itemType.equals("EVENT_FIXED", ignoreCase = true)) &&
                            booking.serviceTitle.equals(itemToBook.title, ignoreCase = true)
                }
                if (alreadyBookedEvent) {
                    _bookingErrorMessage.postValue("Vous êtes déjà inscrit(e) à cet événement.")
                    _bookingStatus.postValue(BookingStatusState.ERROR)
                    resetBookingStatusAfterDelay()
                    return@launch
                }
            } else if (itemToBook.isService) {
                val alreadyBookedServiceSlot = currentUserBookings.any { booking ->
                    (!booking.itemType.equals("EVENT", ignoreCase = true) && !booking.itemType.equals("EVENT_FIXED", ignoreCase = true)) &&
                            booking.serviceTitle.equals(itemToBook.title, ignoreCase = true) &&
                            areDatesAndTimesEffectivelyEqual(booking.bookingDate, selectedDateForComparison)
                }
                if (alreadyBookedServiceSlot) {
                    _bookingErrorMessage.postValue("Vous avez déjà une réservation pour ce service à cette date/heure.")
                    _bookingStatus.postValue(BookingStatusState.ERROR)
                    resetBookingStatusAfterDelay()
                    return@launch
                }
            }

            val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
            dateFormat.timeZone = TimeZone.getTimeZone("UTC")
            val bookingDateString = dateFormat.format(Date(selectedDateTimeMillis))

            val requestBody = BookingRequestDto(
                serviceId = if (itemToBook.isService) itemToBook.id else null,
                eventId = if (itemToBook.isEvent) itemToBook.id else null,
                bookingDate = bookingDateString,
                notes = notes
            )

            try {
                val createdBooking = apiService.createBooking(requestBody)
                _bookingStatus.postValue(BookingStatusState.SUCCESS)
            } catch (e: HttpException) {
                val errorBody = e.response()?.errorBody()?.string()
                _bookingErrorMessage.postValue("Erreur réservation ${e.code()}: ${errorBody ?: e.message()}")
                _bookingStatus.postValue(BookingStatusState.ERROR)
            } catch (e: IOException) {
                _bookingErrorMessage.postValue("Erreur réseau lors de la réservation.")
                _bookingStatus.postValue(BookingStatusState.ERROR)
            } catch (e: Exception) {
                _bookingErrorMessage.postValue("Erreur inconnue lors de la réservation: ${e.message}")
                _bookingStatus.postValue(BookingStatusState.ERROR)
            }
        }
    }

    private fun areDatesAndTimesEffectivelyEqual(date1: Date, date2: Date): Boolean {
        val cal1 = Calendar.getInstance().apply { time = date1 }
        val cal2 = Calendar.getInstance().apply { time = date2 }
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                cal1.get(Calendar.MONTH) == cal2.get(Calendar.MONTH) &&
                cal1.get(Calendar.DAY_OF_MONTH) == cal2.get(Calendar.DAY_OF_MONTH) &&
                cal1.get(Calendar.HOUR_OF_DAY) == cal2.get(Calendar.HOUR_OF_DAY) &&
                cal1.get(Calendar.MINUTE) == cal2.get(Calendar.MINUTE)
    }

    fun resetBookingStatus() {
        _bookingStatus.value = BookingStatusState.IDLE
        _bookingErrorMessage.value = null
    }

    private fun resetBookingStatusAfterDelay() {
        viewModelScope.launch {
            kotlinx.coroutines.delay(3000)
            if (_bookingStatus.value == BookingStatusState.ERROR && _bookingErrorMessage.value?.startsWith("Vous êtes déjà") == true) {
                resetBookingStatus()
            }
        }
    }
}