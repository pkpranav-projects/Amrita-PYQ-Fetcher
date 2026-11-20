package com.amrita.pyqfetcher

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.amrita.pyqfetcher.core.AppLock
import com.amrita.pyqfetcher.core.LocalAuthenticate
import com.amrita.pyqfetcher.core.PrefsState
import com.amrita.pyqfetcher.ui.AppRoot
import com.amrita.pyqfetcher.ui.Messages
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import javax.inject.Inject

/** FragmentActivity (not ComponentActivity) because the biometric prompt needs it. */
@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    @Inject lateinit var messages: Messages
    @Inject lateinit var appLock: AppLock
    @Inject lateinit var prefsState: PrefsState

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.CREATED) {
                prefsState.state.filterNotNull().collect { p ->
                    appLock.onPrefsLoaded(p.appLock)
                    applyRecentsPrivacy(p.hideInRecents)
                }
            }
        }

        setContent {
            CompositionLocalProvider(
                LocalAuthenticate provides { title, onSuccess -> appLock.prompt(this, title, onSuccess) },
            ) {
                AppRoot(messages = messages, appLock = appLock)
            }
        }
    }

    /**
     * Hides the app preview in recent apps. Android 13+ can do this without blocking
     * screenshots; older versions need FLAG_SECURE, which also blocks screenshots.
     */
    private fun applyRecentsPrivacy(hide: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            setRecentsScreenshotEnabled(!hide)
        } else if (hide) {
            window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    override fun onStart() {
        super.onStart()
        appLock.onForeground()
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) appLock.onBackground()
    }
}
