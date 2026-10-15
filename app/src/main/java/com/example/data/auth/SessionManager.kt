package com.example.data.auth

import android.content.Context
import android.content.SharedPreferences
import com.example.data.local.entity.StaffEntity
import com.example.data.repository.BillingRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SessionManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("billingku_auth_session", Context.MODE_PRIVATE)

    private val _currentUser = MutableStateFlow<StaffEntity?>(null)
    val currentUser: StateFlow<StaffEntity?> = _currentUser.asStateFlow()

    init {
        loadSession()
    }

    private fun loadSession() {
        val id = prefs.getLong(KEY_ID, -1L)
        if (id != -1L) {
            val username = prefs.getString(KEY_USERNAME, "") ?: ""
            val name = prefs.getString(KEY_NAME, "") ?: ""
            val role = prefs.getString(KEY_ROLE, "KASIR") ?: "KASIR"
            val phone = prefs.getString(KEY_PHONE, "") ?: ""
            val pin = prefs.getString(KEY_PIN, "") ?: ""
            val isActive = prefs.getBoolean(KEY_ACTIVE, true)

            _currentUser.value = StaffEntity(
                id = id,
                username = username,
                name = name,
                role = role,
                phone = phone,
                pin = pin,
                isActive = isActive
            )
        }
    }

    fun saveSession(staff: StaffEntity) {
        val editor = prefs.edit()
            .putLong(KEY_ID, staff.id)
            .putString(KEY_USERNAME, staff.username)
            .putString(KEY_NAME, staff.name)
            .putString(KEY_ROLE, staff.role)
            .putString(KEY_PHONE, staff.phone)
            .putString(KEY_PIN, staff.pin)
            .putBoolean(KEY_ACTIVE, staff.isActive)

        if (staff.role.equals("ADMIN", ignoreCase = true)) {
            val hash = if (com.example.util.SecurityUtils.isHashed(staff.pin)) {
                staff.pin
            } else {
                com.example.util.SecurityUtils.hashPin(staff.pin)
            }
            editor.putString(KEY_CACHED_ADMIN_PIN_HASH, hash)
        }

        editor.apply()
        _currentUser.value = staff
    }

    fun updateCachedAdminPin(newPin: String) {
        val hash = if (com.example.util.SecurityUtils.isHashed(newPin)) {
            newPin
        } else {
            com.example.util.SecurityUtils.hashPin(newPin)
        }
        prefs.edit().putString(KEY_CACHED_ADMIN_PIN_HASH, hash).apply()
    }

    fun verifyAdminPin(enteredPin: String): Boolean {
        if (isAdmin) {
            val current = _currentUser.value ?: return true
            return com.example.util.SecurityUtils.verifyPin(enteredPin, current.pin)
        }
        val cachedHash = prefs.getString(KEY_CACHED_ADMIN_PIN_HASH, null)
        if (!cachedHash.isNullOrBlank()) {
            return com.example.util.SecurityUtils.verifyPin(enteredPin, cachedHash)
        }
        // Fallback default bootstrap jika belum ada hash tersimpan
        return com.example.util.SecurityUtils.verifyPin(enteredPin, "1234")
    }

    fun clearSession() {
        prefs.edit().clear().apply()
        _currentUser.value = null
    }

    suspend fun refreshWithDb(repository: BillingRepository) {
        val current = _currentUser.value ?: return
        val fresh = repository.getStaffById(current.id)
        if (fresh != null && fresh.isActive) {
            saveSession(fresh)
        } else if (fresh != null && !fresh.isActive) {
            // User deactivated by admin
            clearSession()
        }
    }

    val isLoggedIn: Boolean
        get() = _currentUser.value != null

    val isAdmin: Boolean
        get() = _currentUser.value?.role?.equals("ADMIN", ignoreCase = true) == true

    val isCashier: Boolean
        get() = _currentUser.value?.role?.equals("KASIR", ignoreCase = true) == true

    val isTechnician: Boolean
        get() = _currentUser.value?.role?.equals("TEKNISI", ignoreCase = true) == true

    companion object {
        private const val KEY_ID = "staff_id"
        private const val KEY_USERNAME = "staff_username"
        private const val KEY_NAME = "staff_name"
        private const val KEY_ROLE = "staff_role"
        private const val KEY_PHONE = "staff_phone"
        private const val KEY_PIN = "staff_pin"
        private const val KEY_ACTIVE = "staff_is_active"
        private const val KEY_CACHED_ADMIN_PIN_HASH = "cached_admin_pin_hash"
    }
}
