package com.ahtohiofilho.dominopernambucano

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.ahtohiofilho.dominopernambucano.ui.DominoPernambucanoApp
import com.ahtohiofilho.dominopernambucano.ui.settings.AndroidAppLanguageManager
import com.ahtohiofilho.dominopernambucano.ui.theme.DominoPernambucanoTheme

class MainActivity : ComponentActivity() {
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

        enableEdgeToEdge()
        enableImmersiveMode()

        setContent {
            DominoPernambucanoTheme {
                DominoPernambucanoApp()
            }
        }
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