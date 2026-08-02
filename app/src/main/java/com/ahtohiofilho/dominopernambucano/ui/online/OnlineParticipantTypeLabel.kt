package com.ahtohiofilho.dominopernambucano.ui.online

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.ahtohiofilho.dominopernambucano.R
import androidx.compose.ui.text.font.FontWeight
import com.ahtohiofilho.dominopernambucano.online.OnlineParticipantTypeDto
import com.ahtohiofilho.dominopernambucano.ui.theme.DominoSemanticColors

@Composable
fun OnlineParticipantTypeLabel(
    participantType: OnlineParticipantTypeDto,
    modifier: Modifier = Modifier,
) {
    if (
        participantType !=
        OnlineParticipantTypeDto.APPLICATION
    ) {
        return
    }

    Text(
        text = stringResource(R.string.online_participant_app_controlled),
        modifier = modifier,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = DominoSemanticColors.primaryTextOnDark.copy(
            alpha = 0.72f,
        ),
    )
}
