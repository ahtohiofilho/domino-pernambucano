package com.ahtohiofilho.dominopernambucano.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import com.ahtohiofilho.dominopernambucano.BuildConfig
import com.ahtohiofilho.dominopernambucano.R
import com.ahtohiofilho.dominopernambucano.ui.audio.AndroidMenuMusicController
import com.ahtohiofilho.dominopernambucano.ui.audio.AndroidSoundEffectsPreferences
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
internal const val SettingsRulesHelpTag =
    "settings_rules_help"
internal const val SettingsTermsTag =
    "settings_terms"
internal const val SettingsSupportTag =
    "settings_support"
internal const val SettingsAboutTag =
    "settings_about"

private const val PrivacyPolicyUrl =
    "https://api.dominope.com.br/privacy"
private const val AccountDeletionUrl =
    "https://api.dominope.com.br/account-deletion"
private const val SupportEmail =
    "dominopernambucano@gmail.com"

@Composable
fun SettingsScreen(
    currentSelection: AppLanguageSelection,
    onSelectionChange: (AppLanguageSelection) -> Unit,
    onBackClick: () -> Unit,
    privacyOptionsRequired: Boolean = false,
    onPrivacyOptionsClick: () -> Unit = {},
    onRulesHelpClick: () -> Unit = {},
    onTermsClick: () -> Unit = {},
) {
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    var menuMusicEnabled by remember(context) {
        mutableStateOf(
            AndroidMenuMusicController.isEnabled(context),
        )
    }
    var soundEffectsEnabled by remember(context) {
        mutableStateOf(
            AndroidSoundEffectsPreferences.isEnabled(context),
        )
    }
    var licensesExpanded by remember {
        mutableStateOf(false)
    }

    DominoScreenScaffold(
        title = stringResource(R.string.settings_title),
        layout = DominoScreenLayout.Top,
        onBackClick = onBackClick,
        backContentDescription = stringResource(R.string.common_back),
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
                    selection = AppLanguageSelection.PortugueseBrazil,
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

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(
                    MaterialTheme.dominoSpacing.sm,
                ),
            ) {
                Text(
                    modifier = Modifier.semantics {
                        heading()
                    },
                    text = stringResource(
                        R.string.settings_audio_title,
                    ),
                    color = DominoSemanticColors.brandText,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )

                MenuMusicOptionCard(
                    enabled = menuMusicEnabled,
                    onEnabledChange = { enabled ->
                        AndroidMenuMusicController.setEnabled(
                            context = context,
                            enabled = enabled,
                        )
                        menuMusicEnabled = enabled
                    },
                )

                SoundEffectsOptionCard(
                    enabled = soundEffectsEnabled,
                    onEnabledChange = { enabled ->
                        AndroidSoundEffectsPreferences.setEnabled(
                            context = context,
                            enabled = enabled,
                        )
                        soundEffectsEnabled = enabled
                    },
                )
            }
            SettingsSectionHeading(
                text = stringResource(R.string.settings_group_help),
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(SettingsRulesHelpTag),
            ) {
                DominoSecondaryActionCard(
                    title = stringResource(R.string.settings_rules_title),
                    supportingText = stringResource(
                        R.string.settings_rules_description,
                    ),
                    accent = DominoBrandAccent.Blue,
                    onClick = onRulesHelpClick,
                    leadingContent = {
                        SettingsSymbol("?")
                    },
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(SettingsTermsTag),
            ) {
                DominoSecondaryActionCard(
                    title = stringResource(R.string.settings_terms_title),
                    supportingText = stringResource(
                        R.string.settings_terms_description,
                    ),
                    accent = DominoBrandAccent.Blue,
                    onClick = onTermsClick,
                    leadingContent = {
                        SettingsSymbol("§")
                    },
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(SettingsSupportTag),
            ) {
                DominoSecondaryActionCard(
                    title = stringResource(R.string.settings_support_title),
                    supportingText = stringResource(
                        R.string.settings_support_description,
                    ),
                    accent = DominoBrandAccent.Blue,
                    onClick = {
                        uriHandler.openUri("mailto:$SupportEmail")
                    },
                    leadingContent = {
                        SettingsSymbol("✉")
                    },
                )
            }

            if (privacyOptionsRequired) {
                Box(
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
                            SettingsSymbol("◉")
                        },
                    )
                }
            }

            Box(
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
                        SettingsSymbol("◎")
                    },
                )
            }

            SettingsSectionHeading(
                text = stringResource(R.string.settings_group_account),
            )

            Box(
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
                    accent = DominoBrandAccent.Red,
                    onClick = {
                        uriHandler.openUri(AccountDeletionUrl)
                    },
                    leadingContent = {
                        SettingsSymbol("×")
                    },
                )
            }

            DominoSecondaryActionCard(
                title = stringResource(R.string.settings_licenses_title),
                supportingText = null,
                accent = DominoBrandAccent.Blue,
                onClick = {
                    licensesExpanded = !licensesExpanded
                },
                leadingContent = {
                    SettingsSymbol(if (licensesExpanded) "-" else "+")
                },
            )

            if (licensesExpanded) {            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(
                    MaterialTheme.dominoSpacing.xs,
                ),
            ) {
                Text(
                    modifier = Modifier.semantics {
                        heading()
                    },
                    text = stringResource(
                        R.string.settings_licenses_title,
                    ),
                    color = DominoSemanticColors.brandText,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = DominoBrandShapes.card,
                    color = DominoSemanticColors.brandSurfaceElevated,
                    contentColor = DominoSemanticColors.brandText,
                ) {
                    Column(
                        modifier = Modifier.padding(
                            MaterialTheme.dominoSpacing.md,
                        ),
                        verticalArrangement = Arrangement.spacedBy(
                            MaterialTheme.dominoSpacing.xs,
                        ),
                    ) {
                        Text(
                            text = stringResource(
                                R.string.settings_licenses_sound_effects,
                            ),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                        )

                        Text(
                            text = stringResource(
                                R.string.settings_licenses_domino_table,
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                        )

                        Text(
                            text = stringResource(
                                R.string.settings_licenses_source,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                        )

                        Text(
                            text = stringResource(
                                R.string.settings_licenses_creator,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                        )

                        Text(
                            text = stringResource(
                                R.string.settings_licenses_license,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                        )

                        Text(
                            text = stringResource(
                                R.string.settings_licenses_adapted,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text(
                            text = stringResource(
                                R.string.settings_licenses_match_victory,
                            ),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                        )

                        Text(
                            text = stringResource(
                                R.string.settings_licenses_source,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                        )

                        Text(
                            text = stringResource(
                                R.string.settings_licenses_match_victory_creator,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                        )

                        Text(
                            text = stringResource(
                                R.string.settings_licenses_license,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                        )

                        Text(
                            text = stringResource(
                                R.string.settings_licenses_original_unmodified,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                        )

                        Text(
                            text = stringResource(
                                R.string.settings_licenses_match_defeat,
                            ),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                        )

                        Text(
                            text = stringResource(
                                R.string.settings_licenses_source,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                        )

                        Text(
                            text = stringResource(
                                R.string.settings_licenses_match_defeat_creator,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                        )

                        Text(
                            text = stringResource(
                                R.string.settings_licenses_license,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                        )

                        Text(
                            text = stringResource(
                                R.string.settings_licenses_original_unmodified,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(SettingsAboutTag),
                verticalArrangement = Arrangement.spacedBy(
                    MaterialTheme.dominoSpacing.xs,
                ),
            ) {
                Text(
                    modifier = Modifier.semantics {
                        heading()
                    },
                    text = stringResource(R.string.settings_about_title),
                    color = DominoSemanticColors.brandText,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = stringResource(
                        R.string.settings_version_format,
                        BuildConfig.VERSION_NAME,
                    ),
                    color = DominoSemanticColors.brandSupportingText,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }


        }
    }
}

@Composable
private fun SettingsSectionHeading(
    text: String,
) {
    Text(
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                heading()
            },
        text = text,
        color = DominoSemanticColors.brandText,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
    )
}
@Composable
private fun SettingsSymbol(
    text: String,
) {
    Text(
        text = text,
        color = DominoSemanticColors.brandText,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Black,
    )
}

@Composable
private fun MenuMusicOptionCard(
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("settings_menu_music")
            .clickable(
                role = Role.Switch,
                onClick = {
                    onEnabledChange(!enabled)
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
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(
                    MaterialTheme.dominoSpacing.xs,
                ),
            ) {
                Text(
                    text = stringResource(
                        R.string.settings_menu_music_title,
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                )

                Text(
                    text = stringResource(
                        R.string.settings_menu_music_description,
                    ),
                    color = DominoSemanticColors.brandSupportingText,
                    style = MaterialTheme.typography.bodyMedium,
                )

                Text(
                    text = stringResource(
                        if (enabled) {
                            R.string.settings_menu_music_on
                        } else {
                            R.string.settings_menu_music_off
                        },
                    ),
                    color = DominoSemanticColors.brandSupportingText,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Switch(
                checked = enabled,
                onCheckedChange = null,
            )
        }
    }
}

@Composable
private fun SoundEffectsOptionCard(
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                role = Role.Switch,
                onClick = {
                    onEnabledChange(!enabled)
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
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(
                    MaterialTheme.dominoSpacing.xs,
                ),
            ) {
                Text(
                    text = stringResource(
                        R.string.settings_sound_effects_title,
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                )

                Text(
                    text = stringResource(
                        R.string.settings_sound_effects_description,
                    ),
                    color = DominoSemanticColors.brandSupportingText,
                    style = MaterialTheme.typography.bodyMedium,
                )

                Text(
                    text = stringResource(
                        if (enabled) {
                            R.string.settings_sound_effects_on
                        } else {
                            R.string.settings_sound_effects_off
                        },
                    ),
                    color = DominoSemanticColors.brandSupportingText,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Switch(
                checked = enabled,
                onCheckedChange = null,
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
                    selectedColor = DominoColorTokens.AccentYellow,
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
