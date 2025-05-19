package com.example.businesscare.ui.booking

import android.app.Application
import android.util.Log
import android.text.format.DateFormat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.businesscare.R
import com.example.businesscare.data.model.BookingItem
import com.example.businesscare.data.model.BookingRequestDto
import com.example.businesscare.data.model.ServiceSummaryDto
import com.example.businesscare.data.network.ApiClient
import com.example.businesscare.ui.schedule.DataStatus
import kotlinx.coroutines.launch
import org.json.JSONObject
import retrofit2.HttpException
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

enum class BookingStatusState { IDLE, LOADING, SUCCESS, ERROR }

enum class FilterType { ALL, SERVICE, EVENT }

class AvailableServicesViewModel(application: Application) : AndroidViewModel(application) {

    private val apiService = ApiClient.create(application)

    private val _servicesList = MutableLiveData<List<ServiceSummaryDto>>()
    val servicesList: LiveData<List<ServiceSummaryDto>> = _servicesList

    private var originalServicesList: List<ServiceSummaryDto> = emptyList()

    private val _listDataStatus = MutableLiveData<DataStatus>(DataStatus.IDLE)
    val listDataStatus: LiveData<DataStatus> = _listDataStatus

    private val _listErrorMessage = MutableLiveData<String?>()
    val listErrorMessage: LiveData<String?> = _listErrorMessage

    private val _currentFilterType = MutableLiveData<FilterType>(FilterType.ALL)
    val currentFilterType: LiveData<FilterType> = _currentFilterType

    private val _currentFilterDate = MutableLiveData<Date?>(null)
    val currentFilterDate: LiveData<Date?> = _currentFilterDate

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
                originalServicesList = services
                logInitialEventDates()
                applyFilters()
            } catch (e: HttpException) {
                val errorBody = e.response()?.errorBody()?.string()
                _listErrorMessage.postValue(getApplication<Application>().getString(R.string.error_loading_list_http, e.code(), errorBody ?: e.message()))
                _listDataStatus.postValue(DataStatus.ERROR)
            } catch (e: IOException) {
                _listErrorMessage.postValue(getApplication<Application>().getString(R.string.network_error_message_services))
                _listDataStatus.postValue(DataStatus.ERROR)
            } catch (e: Exception) {
                _listErrorMessage.postValue(getApplication<Application>().getString(R.string.unknown_error_message_services, e.message ?: "N/A"))
                _listDataStatus.postValue(DataStatus.ERROR)
            }
        }
    }

    fun setFilterType(filterType: FilterType) {
        _currentFilterType.value = filterType
        applyFilters()
    }

    fun setFilterDate(date: Date?) {
        _currentFilterDate.value = date
        applyFilters()
    }

    fun clearFilters() {
        _currentFilterType.value = FilterType.ALL
        _currentFilterDate.value = null
        applyFilters()
    }

    private fun logInitialEventDates() {
        if (originalServicesList.any { it.isEvent }) {
            Log.d("EventDateCheck", "--- Initial Event Dates ---")
            originalServicesList.filter { it.isEvent }.forEach { event ->
                if (event.startDate != null) {
                    val cal = Calendar.getInstance().apply { time = event.startDate!! }
                    Log.d("EventDateCheck", "Event: '${event.title}', StartDate (raw object): ${event.startDate}, Formatted (local TZ): ${DateFormat.format("yyyy-MM-dd HH:mm:ss z", cal)}")
                } else {
                    Log.d("EventDateCheck", "Event: '${event.title}', StartDate: null")
                }
            }
            Log.d("EventDateCheck", "--- End Initial Event Dates ---")
        } else {
            Log.d("EventDateCheck", "No events in original list to check dates for.")
        }
    }

    private fun applyFilters() {
        var filteredList = originalServicesList
        Log.d("FilterDebug", "Applying filters. Original list size: ${originalServicesList.size}")

        _currentFilterType.value?.let { type ->
            Log.d("FilterDebug", "Applying type filter: $type")
            filteredList = when (type) {
                FilterType.SERVICE -> filteredList.filter { it.isService }
                FilterType.EVENT -> filteredList.filter { it.isEvent }
                FilterType.ALL -> filteredList
            }
            Log.d("FilterDebug", "After type filter, list size: ${filteredList.size}")
        }

        _currentFilterDate.value?.let { filterDate ->
            val filterCalendar = Calendar.getInstance().apply { time = filterDate }
            Log.d("FilterDebug", "Applying date filter for (local TZ): ${DateFormat.format("yyyy-MM-dd HH:mm:ss", filterCalendar)} (Year: ${filterCalendar.get(Calendar.YEAR)}, DayOfYear: ${filterCalendar.get(Calendar.DAY_OF_YEAR)})")

            val previouslyFilteredListSize = filteredList.size
            filteredList = filteredList.filter { item ->
                if (item.isEvent && item.startDate != null) {
                    val itemCalendar = Calendar.getInstance().apply { time = item.startDate!! }
                    Log.d("FilterDebug", "Event: '${item.title}', EventStartDate (raw): ${item.startDate}, EventDate (local TZ): ${DateFormat.format("yyyy-MM-dd HH:mm:ss", itemCalendar)} (Year: ${itemCalendar.get(Calendar.YEAR)}, DayOfYear: ${itemCalendar.get(Calendar.DAY_OF_YEAR)})")

                    val yearMatch = filterCalendar.get(Calendar.YEAR) == itemCalendar.get(Calendar.YEAR)
                    val dayMatch = filterCalendar.get(Calendar.DAY_OF_YEAR) == itemCalendar.get(Calendar.DAY_OF_YEAR)

                    val matches = yearMatch && dayMatch
                    if (!matches && yearMatch) {
                        Log.w("FilterDebug", "--> DAY MISMATCH for ${item.title}: Filter Y/D: ${filterCalendar.get(Calendar.YEAR)}/${filterCalendar.get(Calendar.DAY_OF_YEAR)}, Event Y/D: ${itemCalendar.get(Calendar.YEAR)}/${itemCalendar.get(Calendar.DAY_OF_YEAR)}")
                    } else if (!yearMatch) {
                        Log.w("FilterDebug", "--> YEAR MISMATCH for ${item.title}: Filter Y: ${filterCalendar.get(Calendar.YEAR)}, Event Y: ${itemCalendar.get(Calendar.YEAR)}")
                    }
                    matches
                } else {
                    if (item.isEvent) {
                        Log.d("FilterDebug", "Event: '${item.title}' has null startDate. Filtered out by date.")
                    } else {
                        Log.d("FilterDebug", "Service: '${item.title}'. Filtered out by date when date filter is active.")
                    }
                    false
                }
            }
            Log.d("FilterDebug", "After date filter (on ${previouslyFilteredListSize} items), list size: ${filteredList.size}")
        }

        _servicesList.postValue(filteredList)
        Log.d("FilterDebug", "Final filtered list posted. Size: ${filteredList.size}")

        if (filteredList.isEmpty()) {
            _listDataStatus.postValue(DataStatus.EMPTY)
            if (_currentFilterType.value != FilterType.ALL || _currentFilterDate.value != null) {
                _listErrorMessage.postValue(getApplication<Application>().getString(R.string.no_results_for_filters))
                Log.d("FilterDebug", "Message: No results for filters")
            } else {
                _listErrorMessage.postValue(getApplication<Application>().getString(R.string.no_services_available))
                Log.d("FilterDebug", "Message: No services available")
            }
        } else {
            _listDataStatus.postValue(DataStatus.SUCCESS)
            _listErrorMessage.postValue(null)
            Log.d("FilterDebug", "Status: Success, list is not empty.")
        }
    }

    private suspend fun fetchCurrentUserBookings(): Boolean {
        try {
            currentUserBookings = apiService.getSchedule().sortedBy { it.bookingDate }
            return true
        } catch (e: HttpException) {
            _bookingErrorMessage.postValue(getApplication<Application>().getString(R.string.booking_check_error_http, e.code()))
            return false
        } catch (e: IOException) {
            _bookingErrorMessage.postValue(getApplication<Application>().getString(R.string.booking_check_error_network))
            return false
        } catch (e: Exception) {
            _bookingErrorMessage.postValue(getApplication<Application>().getString(R.string.booking_check_error_unknown))
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
                            booking.serviceTitle.equals(itemToBook.title, ignoreCase = true) &&
                            booking.status.equals("CONFIRMED", ignoreCase = true)
                }
                if (alreadyBookedEvent) {
                    _bookingErrorMessage.postValue(getApplication<Application>().getString(R.string.event_already_booked_error))
                    _bookingStatus.postValue(BookingStatusState.ERROR)
                    resetBookingStatusAfterDelay()
                    return@launch
                }
            } else if (itemToBook.isService) {
                val alreadyBookedServiceSlot = currentUserBookings.any { booking ->
                    (!booking.itemType.equals("EVENT", ignoreCase = true) && !booking.itemType.equals("EVENT_FIXED", ignoreCase = true)) &&
                            booking.serviceTitle.equals(itemToBook.title, ignoreCase = true) &&
                            areDatesAndTimesEffectivelyEqual(booking.bookingDate, selectedDateForComparison) &&
                            booking.status.equals("CONFIRMED", ignoreCase = true)
                }
                if (alreadyBookedServiceSlot) {
                    _bookingErrorMessage.postValue(getApplication<Application>().getString(R.string.service_already_booked_slot_error))
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
                apiService.createBooking(requestBody)
                _bookingStatus.postValue(BookingStatusState.SUCCESS)
            } catch (e: HttpException) {
                val errorBodyString = e.response()?.errorBody()?.string()
                var specificMessage: String? = null
                if (e.code() == 400 && errorBodyString != null) {
                    try {
                        val jsonObj = JSONObject(errorBodyString)
                        if (jsonObj.has("message") && jsonObj.getString("message").contains("prestataire n'est pas disponible")) {
                            specificMessage = getApplication<Application>().getString(R.string.provider_unavailable_error)
                        } else if (jsonObj.has("message")) {
                            specificMessage = jsonObj.getString("message")
                        }
                    } catch (jsonE: Exception) {
                        specificMessage = errorBodyString ?: e.message()
                    }
                }
                _bookingErrorMessage.postValue(specificMessage ?: getApplication<Application>().getString(R.string.booking_error_http, e.code(), errorBodyString ?: e.message()))
                _bookingStatus.postValue(BookingStatusState.ERROR)
            } catch (e: IOException) {
                _bookingErrorMessage.postValue(getApplication<Application>().getString(R.string.network_error_booking))
                _bookingStatus.postValue(BookingStatusState.ERROR)
            } catch (e: Exception) {
                _bookingErrorMessage.postValue(getApplication<Application>().getString(R.string.unknown_error_booking, e.message ?: "N/A"))
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
            if (_bookingStatus.value == BookingStatusState.ERROR &&
                (_bookingErrorMessage.value == getApplication<Application>().getString(R.string.event_already_booked_error) ||
                        _bookingErrorMessage.value == getApplication<Application>().getString(R.string.service_already_booked_slot_error))) {
                resetBookingStatus()
            }
        }
    }
}