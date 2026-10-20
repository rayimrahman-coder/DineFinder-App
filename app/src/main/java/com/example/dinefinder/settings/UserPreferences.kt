package com.example.dinefinder.settings

import android.content.Context
import java.util.Locale

private const val PROFILE_PREFERENCES = "dinefinder_profile"
private const val EMAIL_KEY = "email"
private const val DARK_MODE_KEY = "dark_mode"
private const val USER_NAME_PREFIX = "user_name:"
const val USER_NAME_MAX_LENGTH = 40

/** Local display preferences for DineFinder's demo sign-in. */
class UserPreferences(context: Context) {
    private val preferences = context.applicationContext
        .getSharedPreferences(PROFILE_PREFERENCES, Context.MODE_PRIVATE)

    fun currentEmail(): String = preferences.getString(EMAIL_KEY, "").orEmpty().trim()

    fun signIn(email: String) {
        preferences.edit().putString(EMAIL_KEY, email.trim()).apply()
    }

    fun signOut() {
        preferences.edit().remove(EMAIL_KEY).apply()
    }

    fun userName(email: String): String {
        return preferences.getString(userNameKey(email), null)
            ?.takeIf { userNameValidationError(it) == null }
            ?: defaultUserName(email)
    }

    /** Returns a validation message, or null after saving the trimmed display name. */
    fun saveUserName(email: String, name: String): String? {
        userNameValidationError(name)?.let { return it }
        preferences.edit().putString(userNameKey(email), name.trim()).apply()
        return null
    }

    fun isDarkMode(): Boolean = preferences.getBoolean(DARK_MODE_KEY, false)

    fun setDarkMode(enabled: Boolean) {
        preferences.edit().putBoolean(DARK_MODE_KEY, enabled).apply()
    }

    private fun userNameKey(email: String): String =
        USER_NAME_PREFIX + email.trim().lowercase(Locale.ROOT)

    private fun defaultUserName(email: String): String {
        val normalizedEmail = email.trim()
        if (normalizedEmail.equals("guest@dinefinder.local", ignoreCase = true)) return "User"
        return normalizedEmail.substringBefore("@")
            .replace('.', ' ')
            .replace('_', ' ')
            .split(' ')
            .filter(String::isNotBlank)
            .joinToString(" ") { it.replaceFirstChar(Char::titlecase) }
            .ifBlank { "User" }
    }
}

fun userNameValidationError(name: String): String? {
    val trimmedName = name.trim()
    return when {
        trimmedName.isBlank() -> "Enter a user name."
        trimmedName.length > USER_NAME_MAX_LENGTH -> "Use $USER_NAME_MAX_LENGTH characters or fewer."
        else -> null
    }
}
