package io.businesscare.app.data.local

import android.content.Context
import android.content.SharedPreferences

class TokenManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_TOKEN_FILE, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_TOKEN_FILE = "TokenPrefs"
        private const val USER_TOKEN = "user_token"
        private const val USER_ROLE = "user_role"
        private const val USER_ID = "user_id"
    }

    fun saveToken(token: String) {
        prefs.edit().putString(USER_TOKEN, token).apply()
    }

    fun getToken(): String? {
        return prefs.getString(USER_TOKEN, null)
    }

    fun saveUserRole(role: String) {
        prefs.edit().putString(USER_ROLE, role).apply()
    }

    fun getUserRole(): String? {
        return prefs.getString(USER_ROLE, null)
    }

    fun saveUserId(userId: Int) {
        prefs.edit().putInt(USER_ID, userId).apply()
    }

    fun getUserId(): Int {
        return prefs.getInt(USER_ID, -1)
    }

    fun clear() {
        prefs.edit()
            .remove(USER_TOKEN)
            .remove(USER_ROLE)
            .remove(USER_ID)
            .apply()
    }
}