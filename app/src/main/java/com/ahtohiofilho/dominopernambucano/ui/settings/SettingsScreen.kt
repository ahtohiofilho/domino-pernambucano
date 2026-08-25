package com.ahtohiofilho.dominopernambucano.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import com.ahtohiofilho.dominopernambucano.R
import com.ahtohiofilho.dominopernambucano.ui.theme.DominoBrandAccent
import com.ahtohiofilho.dominopernambucano.ui.theme.DominoBrandShapes
import com.ahtohiofilho.dominopernambucano.ui.theme.DominoColorTokens
import com.ahtohiofilho.dominopernambucano.ui.theme.DominoScreenLayout
import com.ahtohiofilho.dominopernambucano.ui.theme.DominoScreenScaffold
import com.ahtohiofilho.dominopernambucano.ui.theme.DominoSecondaryActionCard
import com.ahtohiofilho.dominopernambucano.ui.theme.DominoSemanticColors
import com.ahtohiofilho.dominopernambucano.ui.theme.dominoSpacing

internal const val SettingsScreenTag = "settings_screen"
internal const val SettingsLanguageGroupTag =
    "settings_language_group"
internal const val SettingsPrivacyOptionsTag =
    "settings_privacy_options"
internal const val SettingsPrivacyPolicyTag =
    "settings_privacy_policy"
internal const val SettingsAccountDeletionTag =
    "settings_account_deletion"

private const val PrivacyPolicyUrl =
    "https://api.dominope.com.br/privacy"
private const val AccountDeletionUrl =
    "https://api.dominope.com.br/account-deletion"

@Composable
fun SettingsScreen(
    currentSelection: AppLanguageSelection,
    onSelectionChange: (AppLanguageSelection) -> Unit,
    onBackClick: () -> Unit,
    privacyOptionsRequired: Boolean = false,
    onPrivacyOptionsClick: () -> Unit = {},
) {
    val uriHandler = LocalUriHandler.current

    DominoScreenScaffold(
        title = stringResource(R.string.settings_title),
        layout = DominoScreenLayout.Top,
        contentModifier = Modifier.testTag(SettingsScreenTag),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(
                MaterialTheme.dominoSpacing.lg,
            ),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(SettingsLanguageGroupTag),
                verticalArrangement = Arrangement.spacedBy(
                    MaterialTheme.dominoSpacing.sm,
                ),
            ) {
                Text(
                    modifier = Modifier.semantics {
                        heading()
                    },
                    text = stringResource(
                        R.string.settings_language_title,
                    ),
                    color = DominoSemanticColors.brandText,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )

                LanguageOptionCard(
                    title = stringResource(
                        R.string.settings_language_portuguese_brazil,
                    ),
                    selection =
                        AppLanguageSelection.PortugueseBrazil,
                    currentSelection = currentSelection,
                    onSelectionChange = onSelectionChange,
                )

                LanguageOptionCard(
                    title = stringResource(
                        R.string.settings_language_spanish,
                    ),
                    selection = AppLanguageSelection.Spanish,
                    currentSelection = currentSelection,
                    onSelectionChange = onSelectionChange,
                )

                LanguageOptionCard(
                    title = stringResource(
                        R.string.settings_language_english,
                    ),
                    selection = AppLanguageSelection.English,
                    currentSelection = currentSelection,
                    onSelectionChange = onSelectionChange,
                )
            }

            if (privacyOptionsRequired) {
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(SettingsPrivacyOptionsTag),
                ) {
                    DominoSecondaryActionCard(
                        title = stringResource(
                            R.string.settings_privacy_options_title,
                        ),
                        supportingText = stringResource(
                            R.string.settings_privacy_options_description,
                        ),
                        accent = DominoBrandAccent.Blue,
                        onClick = onPrivacyOptionsClick,
                        leadingContent = {
                            Text(
                                text = "◉",
                                color = DominoSemanticColors.brandText,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Black,
                            )
                        },
                    )
                }
            }

            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(SettingsPrivacyPolicyTag),
            ) {
                DominoSecondaryActionCard(
                    title = stringResource(
                        R.string.settings_privacy_policy_title,
                    ),
                    supportingText = stringResource(
                        R.string.settings_privacy_policy_description,
                    ),
                    accent = DominoBrandAccent.Blue,
                    onClick = {
                        uriHandler.openUri(PrivacyPolicyUrl)
                    },
                    leadingContent = {
                        Text(
                            text = "◎",
                            color = DominoSemanticColors.brandText,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black,
                        )
                    },
                )
            }

            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(SettingsAccountDeletionTag),
            ) {
                DominoSecondaryActionCard(
                    title = stringResource(
                        R.string.settings_account_deletion_title,
                    ),
                    supportingText = stringResource(
                        R.string.settings_account_deletion_description,
                    ),
                    accent = DominoBrandAccent.Blue,
                    onClick = {
                        uriHandler.openUri(AccountDeletionUrl)
                    },
                    leadingContent = {
                        Text(
                            text = "×",
                            color = DominoSemanticColors.brandText,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black,
                        )
                    },
                )
            }
            DominoSecondaryActionCard(
                title = stringResource(R.string.common_back),
                supportingText = null,
                accent = DominoBrandAccent.Blue,
                onClick = onBackClick,
                leadingContent = {
                    Text(
                        text = "←",
                        color = DominoSemanticColors.brandText,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                    )
                },
            )
        }
    }
}

@Composable
private fun LanguageOptionCard(
    title: String,
    selection: AppLanguageSelection,
    currentSelection: AppLanguageSelection,
    onSelectionChange: (AppLanguageSelection) -> Unit,
) {
    val selected = currentSelection == selection

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                role = Role.RadioButton,
                onClick = {
                    onSelectionChange(selection)
                },
            ),
        shape = DominoBrandShapes.card,
        color = DominoSemanticColors.brandSurfaceElevated,
        contentColor = DominoSemanticColors.brandText,
    ) {
        Row(
            modifier = Modifier.padding(
                MaterialTheme.dominoSpacing.md,
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(
                MaterialTheme.dominoSpacing.sm,
            ),
        ) {
            RadioButton(
                selected = selected,
                onClick = null,
                colors = RadioButtonDefaults.colors(
                    selectedColor =
                        DominoColorTokens.AccentYellow,
                    unselectedColor =
                        DominoSemanticColors.brandSupportingText,
                ),
            )

            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (selected) {
                    FontWeight.Bold
                } else {
                    FontWeight.Medium
                },
            )
        }
    }
}
