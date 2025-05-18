package io.businesscare.app.ui.booking

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import io.businesscare.app.R
import io.businesscare.app.data.local.TokenManager
import io.businesscare.app.data.model.BookingRequestDto
import io.businesscare.app.data.model.ServiceSummaryDto
import io.businesscare.app.data.network.ApiClient
import io.businesscare.app.ui.schedule.DataStatus
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

enum class BookingStatusState { IDLE, LOADING, SUCCESS, ERROR }

class AvailableServicesViewModel(application: Application) : AndroidViewModel(application) {

    private val apiService = ApiClient.create(application)
    private val tokenManager = TokenManager(application)

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
                    Log.d("BookingDebug", "Fetched services list is empty.")
                    _servicesList.postValue(emptyList())
                    _listDataStatus.postValue(DataStatus.EMPTY)
                } else {
                    Log.d("BookingDebug", "Fetched ${services.size} services. First service providerId from provider.id: ${services.firstOrNull()?.provider?.id}")
                    _servicesList.postValue(services)
                    _listDataStatus.postValue(DataStatus.SUCCESS)
                }
            } catch (e: HttpException) {
                val errorBody = e.response()?.errorBody()?.string()
                Log.e("BookingDebug", "HttpException fetching services: ${e.code()} - $errorBody", e)
                _listErrorMessage.postValue(getApplication<Application>().getString(R.string.http_error_message, e.code(), errorBody ?: e.message()))
                _listDataStatus.postValue(DataStatus.ERROR)
            } catch (e: IOException) {
                Log.e("BookingDebug", "IOException fetching services", e)
                _listErrorMessage.postValue(getApplication<Application>().getString(R.string.network_error_message_services))
                _listDataStatus.postValue(DataStatus.ERROR)
            } catch (e: Exception) {
                Log.e("BookingDebug", "Exception fetching services", e)
                _listErrorMessage.postValue(getApplication<Application>().getString(R.string.unknown_error_message_services, e.message ?: "N/A"))
                _listDataStatus.postValue(DataStatus.ERROR)
            }
        }
    }

    fun createBooking(item: ServiceSummaryDto, selectedTimestamp: Long, notes: String?) {
        Log.d("BookingDebug", "createBooking called for item:")
        Log.d("BookingDebug", "  Title: ${item.title}")
        Log.d("BookingDebug", "  ID: ${item.id}")
        Log.d("BookingDebug", "  IsEvent: ${item.isEvent}")
        Log.d("BookingDebug", "  ProviderId (from item.provider.id): ${item.provider?.id}")
        Log.d("BookingDebug", "  ProviderName (from item.provider.referenceName or fullName): ${item.provider?.referenceName ?: item.provider?.fullName}")
        Log.d("BookingDebug", "  SelectedTimestamp: $selectedTimestamp")
        Log.d("BookingDebug", "  Notes: $notes")

        val currentProviderId = item.provider?.id
        if (currentProviderId == null || currentProviderId <= 0) {
            Log.e("BookingDebug", "Validation failed: Invalid providerId from item.provider.id. ProviderId value: $currentProviderId. Booking aborted.")
            _bookingErrorMessage.postValue(getApplication<Application>().getString(R.string.error_invalid_provider_id))
            _bookingStatus.postValue(BookingStatusState.ERROR)
            return
        }

        val currentEmployeeId = tokenManager.getUserId()
        if (currentEmployeeId == -1) {
            Log.e("BookingDebug", "Validation failed: Invalid employeeId. EmployeeId value: $currentEmployeeId. Booking aborted.")
            _bookingErrorMessage.postValue(getApplication<Application>().getString(R.string.error_invalid_employee_id))
            _bookingStatus.postValue(BookingStatusState.ERROR)
            return
        }

        Log.d("BookingDebug", "ProviderId ($currentProviderId) and EmployeeId ($currentEmployeeId) are valid. Proceeding with booking.")
        _bookingStatus.value = BookingStatusState.LOADING
        _bookingErrorMessage.value = null

        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        val formattedDate = sdf.format(Date(selectedTimestamp))

        val request = BookingRequestDto(
            serviceId = if (item.isEvent) null else item.id,
            eventId = if (item.isEvent) item.id else null,
            providerId = currentProviderId,
            bookingDate = formattedDate,
            notes = notes,
            employeeId = currentEmployeeId
        )

        Log.d("BookingDebug", "BookingRequestDto created: $request")

        viewModelScope.launch {
            try {
                apiService.createBooking(request)
                Log.i("BookingDebug", "Booking successful for serviceId: ${request.serviceId}, eventId: ${request.eventId}")
                _bookingStatus.postValue(BookingStatusState.SUCCESS)
            } catch (e: HttpException) {
                val errorBody = e.response()?.errorBody()?.string()
                Log.e("BookingDebug", "HttpException during booking: ${e.code()} - $errorBody. Request was: $request", e)
                _bookingErrorMessage.postValue(getApplication<Application>().getString(R.string.http_error_booking, e.code(), errorBody ?: e.message()))
                _bookingStatus.postValue(BookingStatusState.ERROR)
            } catch (e: IOException) {
                Log.e("BookingDebug", "IOException during booking. Request was: $request", e)
                _bookingErrorMessage.postValue(getApplication<Application>().getString(R.string.network_error_booking))
                _bookingStatus.postValue(BookingStatusState.ERROR)
            } catch (e: Exception) {
                Log.e("BookingDebug", "Exception during booking. Request was: $request", e)
                _bookingErrorMessage.postValue(getApplication<Application>().getString(R.string.unknown_error_booking, e.message ?: "N/A"))
                _bookingStatus.postValue(BookingStatusState.ERROR)
            }
        }
    }

    fun resetBookingStatus() {
        _bookingStatus.value = BookingStatusState.IDLE
        _bookingErrorMessage.value = null
    }
}