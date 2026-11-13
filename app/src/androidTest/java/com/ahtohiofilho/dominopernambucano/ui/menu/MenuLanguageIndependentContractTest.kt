package com.ahtohiofilho.dominopernambucano.ui.menu

import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ahtohiofilho.dominopernambucano.R
import com.ahtohiofilho.dominopernambucano.ui.theme.DominoPernambucanoTheme
import java.util.Locale
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Confirms translated Compose UI and online/offline gating in every supported language. */
@RunWith(AndroidJUnit4::class)
class MenuLanguageIndependentContractTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun portuguese_interface_contract() = verifyLanguage("pt-BR")
    @Test fun english_interface_contract() = verifyLanguage("en")
    @Test fun spanish_interface_contract() = verifyLanguage("es")

    private fun verifyLanguage(tag: String) {
        val context = contextFor(tag)
        val configuration = context.resources.configuration
        composeRule.setContent {
            CompositionLocalProvider(
                LocalContext provides context,
                LocalConfiguration provides configuration,
            ) {
                DominoPernambucanoTheme {
                    PlayModeScreen(
                        onBackClick = {},
                        onlineParticipationPending = true,
                        onRankedGameClick = {},
                        onLocalGameClick = {},
                        onCreateOnlineRoomClick = {},
                        onJoinOnlineRoomClick = {},
                    )
                }
            }
        }
        composeRule.onNodeWithText(context.getString(R.string.play_mode_ranked))
            .assertExists().assertIsNotEnabled()
        composeRule.onNodeWithText(context.getString(R.string.play_mode_local))
            .assertExists().assertIsEnabled()
        composeRule.onNodeWithText(context.getString(R.string.play_mode_create_room))
            .assertExists().assertIsNotEnabled()
        composeRule.onNodeWithText(context.getString(R.string.play_mode_join_room))
            .assertExists().assertIsNotEnabled()
    }

    private fun contextFor(tag: String): Context {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val config = Configuration(base.resources.configuration)
        config.setLocale(Locale.forLanguageTag(tag))
        return base.createConfigurationContext(config)
    }
}
