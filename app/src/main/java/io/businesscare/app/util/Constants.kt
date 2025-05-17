package io.businesscare.app.util

object AppConstants {
    const val PREFS_NAME_LOGIN = "LoginPrefs"
    const val KEY_LOGIN_EMAIL = "email"
    const val KEY_REMEMBER_ME = "rememberMe"
    const val KEY_USER_ROLE_FROM_LOGIN_PREFS = "userRole"

    const val PREFS_NAME_SETTINGS = "SettingsPrefs"
    const val KEY_THEME_MODE = "theme_mode"
    const val KEY_LANGUAGE = "language"

    const val ITEM_TYPE_EVENT_FIXED = "EVENT_FIXED"

    const val EXTRA_SERVICE_TITLE_TO_REBOOK = "io.businesscare.app.SERVICE_TITLE_TO_REBOOK"
    const val EXTRA_NOTES_TO_REBOOK = "io.businesscare.app.NOTES_TO_REBOOK"
    const val EXTRA_PREFILL_EMAIL = "io.businesscare.app.PREFILL_EMAIL"

    const val ACTION_NFC_TAG_RECEIVED = "io.businesscare.app.ACTION_NFC_TAG_RECEIVED"
    const val NFC_NOTIFICATION_CHANNEL_ID = "nfc_channel"
    const val NOTIFICATION_ID_NFC = 1 
}