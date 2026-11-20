package com.amrita.pyqfetcher.core

import android.content.Context
import android.os.Build
import android.os.SystemClock
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * App lock, like WhatsApp / Telegram: when enabled, the app asks for the fingerprint, face or
 * phone screen lock after it has been in the background longer than the chosen delay.
 */
@Singleton
class AppLock @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefsState: PrefsState,
) {
    private val _locked = MutableStateFlow(true)
    /** True while the lock screen must cover the app. Starts locked until prefs say otherwise. */
    val locked: StateFlow<Boolean> = _locked.asStateFlow()
    private var backgroundedAt = -1L
    private var decidedAtStart = false

    /** Fingerprint/face where available, falling back to the phone's PIN/pattern on Android 11+. */
    val authenticators: Int
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) BIOMETRIC_WEAK or DEVICE_CREDENTIAL else BIOMETRIC_WEAK

    fun canUseLock(): Boolean =
        BiometricManager.from(context).canAuthenticate(authenticators) == BiometricManager.BIOMETRIC_SUCCESS

    /** Called once preferences are loaded: a cold start of a locked app shows the lock screen. */
    fun onPrefsLoaded(lockEnabled: Boolean) {
        if (decidedAtStart) return
        decidedAtStart = true
        _locked.value = lockEnabled && canUseLock()
    }

    fun onBackground() {
        backgroundedAt = SystemClock.elapsedRealtime()
    }

    fun onForeground() {
        val p = prefsState.state.value ?: return
        if (!p.appLock || backgroundedAt < 0 || !canUseLock()) return
        val away = (SystemClock.elapsedRealtime() - backgroundedAt) / 1000
        if (away >= p.lockAfterSec) _locked.value = true
    }

    fun unlocked() { _locked.value = false }

    /** Shows the system prompt. [onSuccess] runs only after a successful check. */
    fun prompt(activity: FragmentActivity, title: String, onSuccess: () -> Unit) {
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle("Amrita PYQ Fetcher")
            .setAllowedAuthenticators(authenticators)
            .apply { if (authenticators and DEVICE_CREDENTIAL == 0) setNegativeButtonText("Cancel") }
            .build()
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onSuccess()
            },
        )
        prompt.authenticate(info)
    }
}

/** Lets any screen ask for authentication: (title, onSuccess). Provided by MainActivity. */
val LocalAuthenticate = staticCompositionLocalOf<(String, () -> Unit) -> Unit> { { _, ok -> ok() } }
