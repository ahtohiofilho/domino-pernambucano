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
import com.ahtohiofilho.dominopernambucano.ui.theme.DominoScreenLayout
import com.ahtohiofilho.dominopernambucano.ui.theme.DominoScreenScaffold
import com.ahtohiofilho.dominopernambucano.ui.theme.DominoSemanticColors
import com.ahtohiofilho.dominopernambucano.ui.theme.dominoSpacing

@Composable
fun TermsOfUseScreen(
    onBackClick: () -> Unit,
) {
    DominoScreenScaffold(
        title = stringResource(R.string.terms_title),
        layout = DominoScreenLayout.Top,
        onBackClick = onBackClick,
        backContentDescription = stringResource(R.string.common_back),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(
                MaterialTheme.dominoSpacing.lg,
            ),
        ) {
            Text(
                text = stringResource(R.string.terms_intro),
                color = DominoSemanticColors.brandSupportingText,
                style = MaterialTheme.typography.bodyLarge,
            )

            TermsSection(
                title = stringResource(R.string.terms_use_title),
                body = stringResource(R.string.terms_use_body),
            )
            TermsSection(
                title = stringResource(R.string.terms_account_title),
                body = stringResource(R.string.terms_account_body),
            )
            TermsSection(
                title = stringResource(R.string.terms_ads_title),
                body = stringResource(R.string.terms_ads_body),
            )
            TermsSection(
                title = stringResource(R.string.terms_privacy_title),
                body = stringResource(R.string.terms_privacy_body),
            )
            TermsSection(
                title = stringResource(R.string.terms_availability_title),
                body = stringResource(R.string.terms_availability_body),
            )
            TermsSection(
                title = stringResource(R.string.terms_changes_title),
                body = stringResource(R.string.terms_changes_body),
            )
            TermsSection(
                title = stringResource(R.string.terms_contact_title),
                body = stringResource(R.string.terms_contact_body),
            )


        }
    }
}

@Composable
private fun TermsSection(
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
