package io.businesscare.app.ui.schedule

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.applandeo.materialcalendarview.CalendarDay
import io.businesscare.app.R
import io.businesscare.app.data.local.TokenManager
import io.businesscare.app.data.model.BookingItem
import io.businesscare.app.data.model.BookingRequestDto
import io.businesscare.app.data.model.ServiceSummaryDto
import io.businesscare.app.data.network.ApiClient
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

enum class DataStatus { IDLE, LOADING, SUCCESS, ERROR, EMPTY }
enum class CancelStatus { IDLE, LOADING, SUCCESS, ERROR }
enum class RebookStatus { IDLE, LOADING, SUCCESS, ERROR }

class ScheduleViewModel(application: Application) : AndroidViewModel(application) {

    private val apiService = ApiClient.create(application)
    private val tokenManager = TokenManager(application)
    private var fullScheduleList: List<BookingItem> = emptyList()

    private val _displayedScheduleList = MutableLiveData<List<BookingItem>>()
    val displayedScheduleList: LiveData<List<BookingItem>> = _displayedScheduleList

    private val _dataStatus = MutableLiveData<DataStatus>(DataStatus.IDLE)
    val dataStatus: LiveData<DataStatus> = _dataStatus

    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    private val _isDateFilterActive = MutableLiveData<Boolean>(false)
    val isDateFilterActive: LiveData<Boolean> = _isDateFilterActive

    private val _eventDays = MutableLiveData<List<CalendarDay>>()
    val eventDays: LiveData<List<CalendarDay>> = _eventDays

    private val _cancelStatus = MutableLiveData<CancelStatus>(CancelStatus.IDLE)
    val cancelStatus: LiveData<CancelStatus> = _cancelStatus

    private val _cancelErrorMessage = MutableLiveData<String?>()
    val cancelErrorMessage: LiveData<String?> = _cancelErrorMessage

    private val _rebookEventStatus = MutableLiveData<RebookStatus>(RebookStatus.IDLE)
    val rebookEventStatus: LiveData<RebookStatus> = _rebookEventStatus

    private val _rebookEventErrorMessage = MutableLiveData<String?>()
    val rebookEventErrorMessage: LiveData<String?> = _rebookEventErrorMessage

    init {
        fetchSchedule()
    }

    fun fetchSchedule() {
        _dataStatus.value = DataStatus.LOADING
        _errorMessage.value = null

        viewModelScope.launch {
            try {
                val bookings = apiService.getSchedule()
                fullScheduleList = bookings.sortedBy { it.bookingDate }
                prepareEventDays(fullScheduleList)

                if (_isDateFilterActive.value == true) {
                    val currentSelectedDate = _displayedScheduleList.value?.firstOrNull()?.bookingDate
                    if (currentSelectedDate != null) {
                        val calendar = Calendar.getInstance().apply { time = currentSelectedDate }
                        filterScheduleByDate(calendar.timeInMillis, false)
                    } else {
                        clearDateFilterLogic()
                    }
                } else {
                    clearDateFilterLogic()
                }

            } catch (e: HttpException) {
                val errorBody = e.response()?.errorBody()?.string()
                _errorMessage.postValue(getApplication<Application>().getString(R.string.http_error_message, e.code(), errorBody ?: e.message()))
                _dataStatus.postValue(DataStatus.ERROR)
            } catch (e: IOException) {
                _errorMessage.postValue(getApplication<Application>().getString(R.string.network_error_message_schedule))
                _dataStatus.postValue(DataStatus.ERROR)
            } catch (e: Exception) {
                _errorMessage.postValue(getApplication<Application>().getString(R.string.unknown_error_message_schedule, e.message ?: "N/A"))
                _dataStatus.postValue(DataStatus.ERROR)
            }
        }
    }

    private fun clearDateFilterLogic() {
        if (fullScheduleList.isEmpty()) {
            _displayedScheduleList.postValue(emptyList())
            _dataStatus.postValue(DataStatus.EMPTY)
        } else {
            _displayedScheduleList.postValue(fullScheduleList)
            _dataStatus.postValue(DataStatus.SUCCESS)
        }
        _isDateFilterActive.postValue(false)
    }

    private fun prepareEventDays(bookings: List<BookingItem>) {
        val events: MutableList<CalendarDay> = mutableListOf()
        val processedDates = mutableSetOf<String>()

        bookings.forEach { booking ->
            val cal = Calendar.getInstance().apply { time = booking.bookingDate }
            val dateKey = "${cal.get(Calendar.YEAR)}-${cal.get(Calendar.MONTH)}-${cal.get(Calendar.DAY_OF_MONTH)}"

            if (!processedDates.contains(dateKey)) {
                val day = CalendarDay(cal)
                day.imageResource = R.drawable.drawable_event_dot
                events.add(day)
                processedDates.add(dateKey)
            }
        }
        _eventDays.postValue(events)
    }

    fun filterScheduleByDate(selectedDateMillis: Long, updateFilterState: Boolean = true) {
        val calSelected = Calendar.getInstance().apply { timeInMillis = selectedDateMillis }
        val yearSelected = calSelected.get(Calendar.YEAR)
        val monthSelected = calSelected.get(Calendar.MONTH)
        val daySelected = calSelected.get(Calendar.DAY_OF_MONTH)

        val filteredList = fullScheduleList.filter { booking ->
            val calBooking = Calendar.getInstance().apply { time = booking.bookingDate }
            val yearBooking = calBooking.get(Calendar.YEAR)
            val monthBooking = calBooking.get(Calendar.MONTH)
            val dayBooking = calBooking.get(Calendar.DAY_OF_MONTH)
            yearBooking == yearSelected && monthBooking == monthSelected && dayBooking == daySelected
        }

        _displayedScheduleList.value = filteredList
        if (updateFilterState) {
            _isDateFilterActive.value = true
        }

        if (filteredList.isEmpty() && dataStatus.value != DataStatus.LOADING && dataStatus.value != DataStatus.ERROR) {
            _dataStatus.value = DataStatus.EMPTY
        } else if (dataStatus.value != DataStatus.LOADING && dataStatus.value != DataStatus.ERROR && filteredList.isNotEmpty()) {
            _dataStatus.value = DataStatus.SUCCESS
        } else if (dataStatus.value != DataStatus.LOADING && dataStatus.value != DataStatus.ERROR && fullScheduleList.isEmpty() && _isDateFilterActive.value == false ) {
            _dataStatus.value = DataStatus.EMPTY
        }
    }

    fun clearDateFilter() {
        if (_isDateFilterActive.value == true) {
            clearDateFilterLogic()
        }
    }

    fun cancelBooking(bookingId: Int) {
        _cancelStatus.value = CancelStatus.LOADING
        _cancelErrorMessage.value = null

        viewModelScope.launch {
            try {
                val response = apiService.cancelBooking(bookingId)
                if (response.isSuccessful) {
                    _cancelStatus.postValue(CancelStatus.SUCCESS)
                    fetchSchedule()
                } else {
                    val errorBody = response.errorBody()?.string()
                    if (response.code() == 400 || response.code() == 404 || response.code() == 409) {
                        _cancelErrorMessage.postValue(getApplication<Application>().getString(R.string.already_cancelled))
                        fetchSchedule()
                    } else {
                        _cancelErrorMessage.postValue(getApplication<Application>().getString(R.string.cancellation_error_code, response.code(), errorBody ?: getApplication<Application>().getString(R.string.cancellation_failed_generic)))
                    }
                    _cancelStatus.postValue(CancelStatus.ERROR)
                }
            } catch (e: HttpException) {
                if (e.code() == 400 || e.code() == 404 || e.code() == 409) {
                    _cancelErrorMessage.postValue(getApplication<Application>().getString(R.string.already_cancelled))
                    fetchSchedule()
                } else {
                    val errorBody = e.response()?.errorBody()?.string()
                    _cancelErrorMessage.postValue(getApplication<Application>().getString(R.string.http_error_message_cancellation, e.code(), errorBody ?: e.message()))
                }
                _cancelStatus.postValue(CancelStatus.ERROR)
            } catch (e: IOException) {
                _cancelErrorMessage.postValue(getApplication<Application>().getString(R.string.network_error_cancellation))
                _cancelStatus.postValue(CancelStatus.ERROR)
            } catch (e: Exception) {
                _cancelErrorMessage.postValue(getApplication<Application>().getString(R.string.unknown_error_cancellation, e.message ?: "N/A"))
                _cancelStatus.postValue(CancelStatus.ERROR)
            }
        }
    }

    fun resetCancelStatus() {
        _cancelStatus.value = CancelStatus.IDLE
        _cancelErrorMessage.value = null
    }

    fun rebookFixedEvent(originalBookingItem: BookingItem, serviceTitleForLookup: String) {
        _rebookEventStatus.value = RebookStatus.LOADING
        _rebookEventErrorMessage.value = null

        val currentEmployeeId = tokenManager.getUserId()
        if (currentEmployeeId == -1) {
            Log.e("BookingDebug", "[ScheduleVM] Validation failed: Invalid employeeId for rebooking. EmployeeId: $currentEmployeeId")
            _rebookEventErrorMessage.postValue(getApplication<Application>().getString(R.string.error_invalid_employee_id))
            _rebookEventStatus.postValue(RebookStatus.ERROR)
            return
        }

        viewModelScope.launch {
            try {
                val availableServices: List<ServiceSummaryDto> = apiService.getAvailableServices()
                val targetServiceSummary = availableServices.find { it.title.equals(serviceTitleForLookup, ignoreCase = true) }

                if (targetServiceSummary == null) {
                    Log.e("BookingDebug", "[ScheduleVM] Service to rebook not found in available services: $serviceTitleForLookup")
                    _rebookEventErrorMessage.postValue(getApplication<Application>().getString(R.string.rebook_service_not_found_error, serviceTitleForLookup))
                    _rebookEventStatus.postValue(RebookStatus.ERROR)
                    return@launch
                }

                Log.d("BookingDebug", "[ScheduleVM] Target service for rebooking: ${targetServiceSummary.title}, Provider from DTO: ${targetServiceSummary.provider}")

                val providerIdForRebooking = targetServiceSummary.provider?.id
                if (providerIdForRebooking == null || providerIdForRebooking <= 0) {
                    Log.e("BookingDebug", "[ScheduleVM] Invalid providerId for rebooking service ${targetServiceSummary.title}. ProviderId from DTO: $providerIdForRebooking")
                    _rebookEventErrorMessage.postValue(getApplication<Application>().getString(R.string.error_invalid_provider_id_rebooking, serviceTitleForLookup))
                    _rebookEventStatus.postValue(RebookStatus.ERROR)
                    return@launch
                }

                val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
                sdf.timeZone = TimeZone.getTimeZone("UTC")
                val formattedDate = sdf.format(originalBookingItem.bookingDate)

                val request = BookingRequestDto(
                    serviceId = targetServiceSummary.id.takeIf { !targetServiceSummary.isEvent },
                    eventId = targetServiceSummary.id.takeIf { targetServiceSummary.isEvent },
                    providerId = providerIdForRebooking,
                    bookingDate = formattedDate,
                    notes = originalBookingItem.notes,
                    employeeId = currentEmployeeId
                )
                Log.d("BookingDebug", "[ScheduleVM] RebookingRequestDto created: $request")

                apiService.createBooking(request)
                Log.i("BookingDebug", "[ScheduleVM] Rebooking successful for serviceId: ${request.serviceId}, eventId: ${request.eventId}")
                _rebookEventStatus.postValue(RebookStatus.SUCCESS)
                fetchSchedule()

            } catch (e: HttpException) {
                val errorBody = e.response()?.errorBody()?.string()
                Log.e("BookingDebug", "[ScheduleVM] HttpException during rebooking: ${e.code()} - $errorBody", e)
                _rebookEventErrorMessage.postValue(getApplication<Application>().getString(R.string.rebook_event_http_error, e.code(), errorBody ?: e.message()))
                _rebookEventStatus.postValue(RebookStatus.ERROR)
            } catch (e: IOException) {
                Log.e("BookingDebug", "[ScheduleVM] IOException during rebooking", e)
                _rebookEventErrorMessage.postValue(getApplication<Application>().getString(R.string.rebook_event_network_error))
                _rebookEventStatus.postValue(RebookStatus.ERROR)
            } catch (e: Exception) {
                Log.e("BookingDebug", "[ScheduleVM] Exception during rebooking", e)
                _rebookEventErrorMessage.postValue(getApplication<Application>().getString(R.string.rebook_event_unknown_error, e.message ?: "N/A"))
                _rebookEventStatus.postValue(RebookStatus.ERROR)
            }
        }
    }

    fun resetRebookEventStatus() {
        _rebookEventStatus.value = RebookStatus.IDLE
        _rebookEventErrorMessage.value = null
    }

    fun resetStatus() {
        _dataStatus.value = DataStatus.IDLE
        _errorMessage.value = null
    }
}