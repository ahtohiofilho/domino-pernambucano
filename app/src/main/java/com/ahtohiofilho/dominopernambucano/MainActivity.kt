package com.ahtohiofilho.dominopernambucano

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.ahtohiofilho.dominopernambucano.ui.DominoPernambucanoApp
import com.ahtohiofilho.dominopernambucano.ui.theme.DominoPernambucanoTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(
        savedInstanceState: Bundle?,
    ) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        setContent {
            DominoPernambucanoTheme {
                DominoPernambucanoApp()
            }
        }
    }
}