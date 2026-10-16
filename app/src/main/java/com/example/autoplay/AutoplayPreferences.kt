package com.example.autoplay

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Persistent storage for Autoplay settings using Android SharedPreferences.
 * Defaults to true, mirroring Spotify's "Autoplay similar content" default
 * behavior to deliver an uninterrupted music listening experience.
 */
class AutoplayPreferences(context: Context) {

    companion object {
        private const val PREFS_NAME = "streamtune_autoplay_prefs"
        private const val KEY_AUTOPLAY_ENABLED = "key_autoplay_enabled"
        const val DEFAULT_AUTOPLAY_ENABLED = true
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _isAutoplayEnabledFlow = MutableStateFlow(
        prefs.getBoolean(KEY_AUTOPLAY_ENABLED, DEFAULT_AUTOPLAY_ENABLED)
    )
    val isAutoplayEnabledFlow: StateFlow<Boolean> = _isAutoplayEnabledFlow.asStateFlow()

    fun isAutoplayEnabled(): Boolean {
        return _isAutoplayEnabledFlow.value
    }

    fun setAutoplayEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTOPLAY_ENABLED, enabled).apply()
        _isAutoplayEnabledFlow.value = enabled
    }

    fun toggleAutoplay(): Boolean {
        val next = !_isAutoplayEnabledFlow.value
        setAutoplayEnabled(next)
        return next
    }
}
