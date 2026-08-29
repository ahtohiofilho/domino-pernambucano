package com.ahtohiofilho.dominopernambucano.ui.info

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import com.ahtohiofilho.dominopernambucano.R
import com.ahtohiofilho.dominopernambucano.advertising.AdvertisingPlacement
import com.ahtohiofilho.dominopernambucano.advertising.DominoBannerAd
import com.ahtohiofilho.dominopernambucano.ui.theme.DominoBrandAccent
import com.ahtohiofilho.dominopernambucano.ui.theme.DominoScreenLayout
import com.ahtohiofilho.dominopernambucano.ui.theme.DominoScreenScaffold
import com.ahtohiofilho.dominopernambucano.ui.theme.DominoSecondaryActionCard
import com.ahtohiofilho.dominopernambucano.ui.theme.DominoSemanticColors
import com.ahtohiofilho.dominopernambucano.ui.theme.dominoSpacing

@Composable
fun RulesHelpScreen(
    bannerAdsReady: Boolean,
    onBackClick: () -> Unit,
) {
    DominoScreenScaffold(
        title = stringResource(R.string.rules_help_title),
        layout = DominoScreenLayout.Top,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(
                MaterialTheme.dominoSpacing.lg,
            ),
        ) {
            Text(
                text = stringResource(R.string.rules_help_intro),
                color = DominoSemanticColors.brandSupportingText,
                style = MaterialTheme.typography.bodyLarge,
            )

            HelpSection(
                title = stringResource(R.string.rules_help_match_title),
                body = stringResource(R.string.rules_help_match_body),
            )
            HelpSection(
                title = stringResource(R.string.rules_help_deal_title),
                body = stringResource(R.string.rules_help_deal_body),
            )
            HelpSection(
                title = stringResource(R.string.rules_help_opening_title),
                body = stringResource(R.string.rules_help_opening_body),
            )
            HelpSection(
                title = stringResource(R.string.rules_help_turn_title),
                body = stringResource(R.string.rules_help_turn_body),
            )
            HelpSection(
                title = stringResource(R.string.rules_help_scoring_title),
                body = stringResource(R.string.rules_help_scoring_body),
            )
            HelpSection(
                title = stringResource(R.string.rules_help_blocked_title),
                body = stringResource(R.string.rules_help_blocked_body),
            )
            HelpSection(
                title = stringResource(R.string.rules_help_clock_title),
                body = stringResource(R.string.rules_help_clock_body),
            )
            HelpSection(
                title = stringResource(R.string.rules_help_online_title),
                body = stringResource(R.string.rules_help_online_body),
            )
            HelpSection(
                title = stringResource(R.string.rules_help_ranking_title),
                body = stringResource(R.string.rules_help_ranking_body),
            )

            DominoBannerAd(
                placement = AdvertisingPlacement.RULES_HELP,
                adsReady = bannerAdsReady,
            )

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
private fun HelpSection(
    title: String,
    body: String,
) {
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
            text = title,
            color = DominoSemanticColors.brandText,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = body,
            color = DominoSemanticColors.brandSupportingText,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}
