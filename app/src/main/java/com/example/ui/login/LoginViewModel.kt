package com.example.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.auth.SessionManager
import com.example.data.local.entity.StaffEntity
import com.example.data.repository.BillingRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class LoginUiState(
    val username: String = "",
    val pin: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val availableStaff: List<StaffEntity> = emptyList(),
    val isPinVisible: Boolean = false
)

class LoginViewModel(
    private val repository: BillingRepository,
    private val sessionManager: SessionManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.ensureDefaultStaffExist()
            repository.activeStaff.collect { staffList ->
                _uiState.update { it.copy(availableStaff = staffList) }
            }
        }
    }

    fun onUsernameChanged(value: String) {
        _uiState.update { it.copy(username = value, errorMessage = null) }
    }

    fun onPinChanged(value: String) {
        if (value.length <= 6) {
            _uiState.update { it.copy(pin = value, errorMessage = null) }
        }
    }

    fun togglePinVisibility() {
        _uiState.update { it.copy(isPinVisible = !it.isPinVisible) }
    }

    fun selectAccount(staff: StaffEntity) {
        _uiState.update {
            it.copy(
                username = staff.username,
                pin = staff.pin,
                errorMessage = null
            )
        }
    }

    fun login(onSuccess: (StaffEntity) -> Unit) {
        val state = _uiState.value
        val username = state.username.trim()
        val pin = state.pin.trim()

        if (username.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Silakan masukkan username akun petugas.") }
            return
        }

        if (pin.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Silakan masukkan PIN otorisasi.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            val authenticated = repository.authenticate(username, pin)
            if (authenticated != null) {
                if (!authenticated.isActive) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = "Akun ini telah dinonaktifkan oleh Administrator."
                        )
                    }
                } else {
                    sessionManager.saveSession(authenticated)
                    _uiState.update { it.copy(isLoading = false, errorMessage = null) }
                    onSuccess(authenticated)
                }
            } else {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "Username atau PIN salah. Silakan periksa kembali."
                    )
                }
            }
        }
    }
}

class LoginViewModelFactory(
    private val repository: BillingRepository,
    private val sessionManager: SessionManager
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(LoginViewModel::class.java)) {
            return LoginViewModel(repository, sessionManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
