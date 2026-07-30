package com.ahtohiofilho.dominopernambucano.ui.menu

import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ahtohiofilho.dominopernambucano.R
import com.ahtohiofilho.dominopernambucano.online.OnlineGoogleAccountStatus
import com.ahtohiofilho.dominopernambucano.ui.account.OnlineAccountDialog
import com.ahtohiofilho.dominopernambucano.ui.account.OnlineAccountProfileStatusTag
import com.ahtohiofilho.dominopernambucano.ui.account.OnlineAccountProfileUiState
import com.ahtohiofilho.dominopernambucano.ui.settings.AppLanguageSelection
import com.ahtohiofilho.dominopernambucano.ui.settings.SettingsScreen
import com.ahtohiofilho.dominopernambucano.ui.settings.SettingsScreenTag
import com.ahtohiofilho.dominopernambucano.ui.theme.DominoPernambucanoTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EntryIdentityVisualContractTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun menu_scaffold_reaches_limit_content_by_scrolling() {
        composeRule.setContent {
            DominoPernambucanoTheme {
                MenuScaffold {
                    repeat(24) { index ->
                        Text(
                            modifier = if (index == 23) {
                                Modifier.testTag(LimitContentTag)
                            } else {
                                Modifier
                            },
                            text = "Item $index",
                        )
                    }
                }
            }
        }

        composeRule
            .onNodeWithTag(LimitContentTag)
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun placeholder_is_direct_and_keeps_back_action() {
        var backClicks = 0
        val context =
            InstrumentationRegistry.getInstrumentation().targetContext
        val backLabel = context.getString(R.string.common_back)

        composeRule.setContent {
            DominoPernambucanoTheme {
                MenuPlaceholderScreen(
                    title = "Partida rankeada",
                    description =
                        "A fila rankeada não está disponível neste ambiente.",
                    onBackClick = {
                        backClicks += 1
                    },
                )
            }
        }

        composeRule
            .onNodeWithText("Partida rankeada")
            .assert(isHeading())
            .assertIsDisplayed()

        composeRule
            .onAllNodesWithText(
                "Funcionalidade reservada para o roadmap online.",
            )
            .assertCountEquals(0)

        composeRule
            .onNodeWithText(backLabel)
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()

        composeRule.runOnIdle {
            assertEquals(1, backClicks)
        }
    }

    @Test
    fun visitor_mode_exposes_heading_and_blocks_ranked_action() {
        val context =
            InstrumentationRegistry.getInstrumentation().targetContext
        val rankedLabel =
            context.getString(R.string.play_mode_ranked)
        val accountRequiredMessage =
            context.getString(
                R.string.play_mode_ranked_account_required,
            )

        composeRule.setContent {
            DominoPernambucanoTheme {
                PlayModeScreen(
                    onlineDisplayName = "Antônio Filho",
                    onlineTableName = "antonio",
                    onlineIdentityManagedByAccount = false,
                    onOnlineDisplayNameChange = {},
                    onBackClick = {},
                    rankedAccountAvailable = false,
                    onRankedGameClick = {},
                    onLocalGameClick = {},
                    onCreateOnlineRoomClick = {},
                    onJoinOnlineRoomClick = {},
                )
            }
        }

        composeRule
            .onNodeWithTag(PlayModeTitleTag)
            .assert(isHeading())
            .assertIsDisplayed()

        composeRule
            .onNodeWithText(rankedLabel)
            .assertIsNotEnabled()

        composeRule
            .onNodeWithText(accountRequiredMessage)
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun play_mode_keeps_full_brand_on_home_and_actions_in_initial_viewport() {
        val context =
            InstrumentationRegistry.getInstrumentation().targetContext
        val brandSubtitle =
            context.getString(R.string.brand_subtitle)
        val rankedLabel =
            context.getString(R.string.play_mode_ranked)
        val localLabel =
            context.getString(R.string.play_mode_local)
        val createRoomLabel =
            context.getString(R.string.play_mode_create_room)
        val joinRoomLabel =
            context.getString(R.string.play_mode_join_room)
        val backLabel =
            context.getString(R.string.common_back)

        composeRule.setContent {
            DominoPernambucanoTheme {
                PlayModeScreen(
                    onlineDisplayName = "Antônio Filho",
                    onlineTableName = "antonio",
                    onlineIdentityManagedByAccount = true,
                    onOnlineDisplayNameChange = {},
                    onBackClick = {},
                    rankedAccountAvailable = true,
                    onRankedGameClick = {},
                    onLocalGameClick = {},
                    onCreateOnlineRoomClick = {},
                    onJoinOnlineRoomClick = {},
                )
            }
        }

        composeRule.waitForIdle()

        composeRule
            .onAllNodesWithText(brandSubtitle)
            .assertCountEquals(0)

        composeRule
            .onNodeWithTag(PlayModeTitleTag)
            .assert(isHeading())
            .assertIsDisplayed()

        composeRule.onNodeWithText(rankedLabel).assertIsDisplayed()
        composeRule.onNodeWithText(localLabel).assertIsDisplayed()
        composeRule.onNodeWithText(createRoomLabel).assertIsDisplayed()
        composeRule.onNodeWithText(joinRoomLabel).assertIsDisplayed()
        composeRule.onNodeWithText(backLabel).assertIsDisplayed()
    }

    @Test
    fun settings_screen_is_compact_and_vertically_balanced() {
        val context =
            InstrumentationRegistry.getInstrumentation().targetContext
        val explanatoryText =
            context.getString(R.string.settings_language_description)

        composeRule.setContent {
            DominoPernambucanoTheme {
                SettingsScreen(
                    currentSelection =
                        AppLanguageSelection.PortugueseBrazil,
                    onSelectionChange = {},
                    onBackClick = {},
                )
            }
        }

        composeRule.waitForIdle()

        composeRule
            .onAllNodesWithText(explanatoryText)
            .assertCountEquals(0)

        val rootBounds =
            composeRule
                .onRoot()
                .fetchSemanticsNode()
                .boundsInRoot

        val contentBounds =
            composeRule
                .onNodeWithTag(SettingsScreenTag)
                .fetchSemanticsNode()
                .boundsInRoot

        val contentCenterY =
            (contentBounds.top + contentBounds.bottom) / 2f

        val minimumAcceptedCenterY =
            rootBounds.top + rootBounds.height * 0.38f

        val maximumAcceptedCenterY =
            rootBounds.top + rootBounds.height * 0.62f

        assertTrue(
            "Configurações continua excessivamente concentrada no topo.",
            contentCenterY >= minimumAcceptedCenterY,
        )

        assertTrue(
            "Configurações foi deslocada excessivamente para baixo.",
            contentCenterY <= maximumAcceptedCenterY,
        )
    }

    @Test
    fun main_menu_content_is_not_concentrated_at_the_top() {
        composeRule.setContent {
            DominoPernambucanoTheme {
                MainMenuScreen(
                    pendingOnlineParticipation =
                        com.ahtohiofilho.dominopernambucano.online
                            .OnlinePendingParticipationLocalResolution
                            .NoPendingParticipation,
                    pendingOnlineParticipationInspection =
                        com.ahtohiofilho.dominopernambucano.session
                            .OnlinePendingParticipationInspectionState
                            .NotRequested,
                    pendingOnlineMatchResumeInProgress = false,
                    onPlayClick = {},
                    onRankingClick = {},
                    onInspectPendingOnlineParticipationClick = {},
                    onResumePendingOnlineMatchClick = {},
                )
            }
        }

        composeRule.waitForIdle()

        val rootBounds =
            composeRule
                .onRoot()
                .fetchSemanticsNode()
                .boundsInRoot

        val contentBounds =
            composeRule
                .onNodeWithTag(MainMenuContentGroupTag)
                .fetchSemanticsNode()
                .boundsInRoot

        val contentCenterY =
            (contentBounds.top + contentBounds.bottom) / 2f

        val minimumAcceptedCenterY =
            rootBounds.top + rootBounds.height * 0.40f

        val maximumAcceptedCenterY =
            rootBounds.top + rootBounds.height * 0.62f

        assertTrue(
            "O conteúdo principal da Home continua concentrado no topo.",
            contentCenterY >= minimumAcceptedCenterY,
        )

        assertTrue(
            "O conteúdo principal da Home foi deslocado excessivamente.",
            contentCenterY <= maximumAcceptedCenterY,
        )
    }

    @Test
    fun main_menu_feedback_is_announced_politely() {
        composeRule.setContent {
            MainMenuScreen(
                pendingOnlineParticipation =
                    com.ahtohiofilho.dominopernambucano.online
                        .OnlinePendingParticipationLocalResolution
                        .NoPendingParticipation,
                pendingOnlineParticipationInspection =
                    com.ahtohiofilho.dominopernambucano.session
                        .OnlinePendingParticipationInspectionState
                        .NotRequested,
                pendingOnlineMatchResumeInProgress = false,
                pendingOnlineMatchResumeFeedbackMessage =
                    "Não foi possível verificar agora. Tente novamente.",
                onPlayClick = {},
                onRankingClick = {},
                onInspectPendingOnlineParticipationClick = {},
                onResumePendingOnlineMatchClick = {},
            )
        }

        composeRule
            .onNodeWithTag(MainMenuStatusTag)
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.LiveRegion,
                    LiveRegionMode.Polite,
                ),
            )
    }

    @Test
    fun connected_account_loading_exposes_progress_semantics() {
        composeRule.setContent {
            DominoPernambucanoTheme {
                OnlineAccountDialog(
                    status = OnlineGoogleAccountStatus.CONNECTED,
                    actionInProgress = false,
                    feedbackMessage = null,
                    profileState = OnlineAccountProfileUiState.Loading,
                    onPublicDisplayNameChange = {},
                    onTableNameChange = {},
                    onSaveProfileClick = {},
                    onRetryProfileClick = {},
                    onConnectGoogleClick = {},
                    onDismissRequest = {},
                )
            }
        }

        composeRule
            .onNodeWithTag(OnlineAccountProfileStatusTag)
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.ProgressBarRangeInfo,
                    ProgressBarRangeInfo.Indeterminate,
                ),
            )
            .assertIsDisplayed()
    }

    private companion object {
        const val LimitContentTag = "entry_identity_limit_content"
    }
}
