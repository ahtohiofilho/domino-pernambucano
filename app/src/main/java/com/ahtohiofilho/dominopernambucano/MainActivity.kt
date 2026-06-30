package com.ahtohiofilho.dominopernambucano

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.ahtohiofilho.dominopernambucano.ui.DominoPernambucanoApp
import com.ahtohiofilho.dominopernambucano.ui.theme.DominoPernambucanoTheme

class MainActivity : ComponentActivity() {
    private var isAppInForeground: Boolean? by mutableStateOf(
        value = null,
    )

    override fun onCreate(
        savedInstanceState: Bundle?,
    ) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        enableImmersiveMode()

        setContent {
            DominoPernambucanoTheme {
                DominoPernambucanoApp(
                    isAppInForeground = isAppInForeground,
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        isAppInForeground = true
    }

    override fun onStop() {
        isAppInForeground = false
        super.onStop()
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