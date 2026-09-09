package com.ahtohiofilho.dominopernambucano

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.ahtohiofilho.dominopernambucano.advertising.AndroidAdvertisingController
import com.ahtohiofilho.dominopernambucano.ui.DominoPernambucanoApp
import com.ahtohiofilho.dominopernambucano.ui.audio.AndroidMenuMusicController
import com.ahtohiofilho.dominopernambucano.ui.settings.AndroidAppLanguageManager
import com.ahtohiofilho.dominopernambucano.ui.theme.DominoPernambucanoTheme

class MainActivity : ComponentActivity() {
    private lateinit var advertisingController: AndroidAdvertisingController

    override fun attachBaseContext(
        newBase: Context,
    ) {
        super.attachBaseContext(
            AndroidAppLanguageManager.localizedContext(
                baseContext = newBase,
            ),
        )
    }

    override fun onCreate(
        savedInstanceState: Bundle?,
    ) {
        super.onCreate(savedInstanceState)

        advertisingController = AndroidAdvertisingController(
            context = applicationContext,
        )

        enableEdgeToEdge()
        enableImmersiveMode()

        setContent {
            val privacyOptionsRequired by
                advertisingController.privacyOptionsRequired.collectAsState()
            val adsReady by
                advertisingController.adsReady.collectAsState()

            DominoPernambucanoTheme {
                DominoPernambucanoApp(
                    onMatchFinished = {
                        advertisingController.recordMatchFinished()
                    },
                    onMatchFinishedTransition = { continuation ->
                        advertisingController.runAfterMatchFinishedTransition(
                            activity = this@MainActivity,
                            continuation = continuation,
                        )
                    },
                    privacyOptionsRequired = privacyOptionsRequired,
                    bannerAdsReady = adsReady,
                    onPrivacyOptionsClick = {
                        advertisingController.showPrivacyOptions(
                            activity = this@MainActivity,
                        )
                    },
                )
            }
        }

        advertisingController.start(
            activity = this,
        )
    }

    override fun onStart() {
        super.onStart()

        AndroidMenuMusicController.setAppForeground(
            context = applicationContext,
            foreground = true,
        )
    }

    override fun onStop() {
        AndroidMenuMusicController.setAppForeground(
            context = applicationContext,
            foreground = false,
        )

        super.onStop()
    }

    override fun onDestroy() {
        AndroidMenuMusicController.release()
        super.onDestroy()
    }

    override fun onWindowFocusChanged(
        hasFocus: Boolean,
    ) {
        super.onWindowFocusChanged(hasFocus)

        if (hasFocus) {
            enableImmersiveMode()
        }
    }

    private fun enableImmersiveMode() {
        WindowCompat.setDecorFitsSystemWindows(
            window,
            false,
        )

        val controller = WindowInsetsControllerCompat(
            window,
            window.decorView,
        )

        controller.hide(
            WindowInsetsCompat.Type.statusBars() or
                    WindowInsetsCompat.Type.navigationBars()
        )

        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }
}