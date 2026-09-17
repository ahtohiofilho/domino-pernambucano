package com.ahtohiofilho.dominopernambucano.ui.account

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ahtohiofilho.dominopernambucano.R
import com.ahtohiofilho.dominopernambucano.ui.personalization.HandAppearanceTone
import com.ahtohiofilho.dominopernambucano.ui.theme.DominoScreenLayout
import com.ahtohiofilho.dominopernambucano.ui.theme.DominoScreenScaffold
import com.ahtohiofilho.dominopernambucano.ui.theme.DominoSemanticColors
import com.ahtohiofilho.dominopernambucano.ui.theme.dominoSpacing

internal const val IdentityHubScreenTag =
    "identity_hub_screen"

internal const val IdentityHubAvatarTag =
    "identity_hub_avatar"

internal const val IdentityHubDisplayNameTag =
    "identity_hub_display_name"

internal const val IdentityHubTableNameTag =
    "identity_hub_table_name"

internal const val IdentityHubAccountStateTag =
    "identity_hub_account_state"

internal const val IdentityHubEditProfileActionTag =
    "identity_hub_edit_profile_action"

internal const val IdentityHubHandAppearanceTag =
    "identity_hub_hand_appearance"

internal const val IdentityHubAccountActionTag =
    "identity_hub_account_action"

internal enum class IdentityHubAccountState {
    UNAVAILABLE,
    DISCONNECTED,
    CONNECTED,
    RECOVERY_REQUIRED,
}

internal fun identityHubAccountSupportsDisconnect(
    state: IdentityHubAccountState,
): Boolean {
    return state == IdentityHubAccountState.CONNECTED
}

internal data class IdentityHubUiState(
    val displayName: String,
    val tableName: String,
    val accountState: IdentityHubAccountState,
    val profilePhotoUri: String? = null,
    val handAppearanceTone: HandAppearanceTone =
        HandAppearanceTone.TONE_1,
) {
    val avatarLabel: String?
        get() = identityHubAvatarLabel(
            displayName = displayName,
            tableName = tableName,
        )

    val compactDisplayName: String?
        get() = identityHubCompactDisplayName(
            displayName = displayName,
            tableName = tableName,
        )

    val meaningfulTableName: String?
        get() = identityHubMeaningfulTableName(
            displayName = displayName,
            tableName = tableName,
        )

    val headerProfilePhotoUri: String?
        get() = identityHubUsableProfilePhotoUri(
            profilePhotoUri,
        ).takeIf {
            accountState == IdentityHubAccountState.CONNECTED
        }

    val headerAvatarLabel: String?
        get() = avatarLabel.takeIf {
            accountState == IdentityHubAccountState.CONNECTED
        }

    val headerDisplayName: String?
        get() = compactDisplayName.takeIf {
            accountState == IdentityHubAccountState.CONNECTED
        }

    val headerTableName: String?
        get() = meaningfulTableName.takeIf {
            accountState == IdentityHubAccountState.CONNECTED
        }
}

/**
 * Compact avatar fallback used whenever a profile photo is absent or fails.
 *
 * When a meaningful personal name has at least two significant words, the
 * avatar uses the first and last meaningful initials (for example, Antônio
 * Filho -> AF). A single-word identity uses one initial. Common name particles
 * are ignored when choosing the last initial. When there is no meaningful
 * identity, null signals the generic profile icon.
 */
internal fun identityHubAvatarLabel(
    displayName: String?,
    tableName: String?,
): String? {
    val compactName = identityHubCompactDisplayName(
        displayName = displayName,
        tableName = tableName,
    ) ?: return null

    val words = compactName
        .trim()
        .split(Regex("\\s+"))
        .filter { it.isNotBlank() }

    val significantWords = words
        .filterNot { word ->
            word.lowercase() in identityHubNameParticles
        }
        .ifEmpty { words }

    val selectedWords = when {
        significantWords.size >= 2 ->
            listOf(
                significantWords.first(),
                significantWords.last(),
            )

        significantWords.size == 1 -> significantWords
        else -> emptyList()
    }

    return selectedWords
        .mapNotNull { word ->
            word.firstOrNull()?.uppercaseChar()
        }
        .joinToString(separator = "")
        .ifBlank { null }
}

private val identityHubNameParticles = setOf(
    "da",
    "das",
    "de",
    "do",
    "dos",
    "e",
    "del",
    "la",
    "las",
    "los",
    "y",
    "van",
    "von",
)

internal fun identityHubCompactDisplayName(
    displayName: String?,
    tableName: String?,
): String? {
    val normalizedName =
        displayName
            ?.trim()
            .orEmpty()

    if (
        normalizedName.isNotBlank() &&
        !identityHubIsGenericDisplayName(normalizedName)
    ) {
        return normalizedName
    }

    val normalizedTableName =
        tableName
            ?.trim()
            .orEmpty()

    if (
        normalizedTableName.isNotBlank() &&
        !identityHubIsGenericTableName(normalizedTableName)
    ) {
        return normalizedTableName.uppercase()
    }

    return null
}

internal fun identityHubMeaningfulTableName(
    displayName: String?,
    tableName: String?,
): String? {
    val normalizedTableName =
        tableName
            ?.trim()
            .orEmpty()

    if (normalizedTableName.isBlank()) {
        return null
    }

    val normalizedName =
        displayName
            ?.trim()
            .orEmpty()

    if (
        normalizedName.isNotBlank() &&
        !identityHubIsGenericDisplayName(normalizedName)
    ) {
        return normalizedTableName.uppercase()
    }

    return normalizedTableName
        .takeUnless(::identityHubIsGenericTableName)
        ?.uppercase()
}

private fun identityHubIsGenericDisplayName(
    value: String,
): Boolean {
    return value.trim().lowercase() in setOf(
        "jogador",
        "player",
        "jugador",
    )
}

private fun identityHubIsGenericTableName(
    value: String,
): Boolean {
    return value.trim().uppercase() in setOf(
        "DP",
        "JOG",
        "JOGADOR",
        "JOGADORA",
        "JUG",
        "JUGADOR",
        "JUGADORA",
        "PLA",
        "PLAYER",
        "PLY",
    )
}

@Composable
internal fun IdentityAvatar(
    label: String?,
    size: Dp,
    modifier: Modifier = Modifier,
    profilePhotoUri: String? = null,
) {
    val profilePhotoBitmap =
        rememberIdentityProfilePhotoBitmap(profilePhotoUri)

    Surface(
        modifier = modifier.size(size),
        shape = CircleShape,
        color = DominoSemanticColors.brandSurfaceElevated,
        contentColor = DominoSemanticColors.brandText,
        border = BorderStroke(
            width = 1.dp,
            color = DominoSemanticColors.brandBorder,
        ),
    ) {
        Box(
            contentAlignment = Alignment.Center,
        ) {
            if (profilePhotoBitmap != null) {
                Image(
                    bitmap = profilePhotoBitmap,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else if (!label.isNullOrBlank()) {
                Text(
                    text = label.take(2),
                    style = if (size >= 64.dp) {
                        MaterialTheme.typography.headlineMedium
                    } else {
                        MaterialTheme.typography.titleMedium
                    },
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center,
                )
            } else {
                GenericProfileGlyph(
                    modifier = Modifier.size(
                        if (size >= 64.dp) 36.dp else 20.dp,
                    ),
                )
            }
        }
    }
}

@Composable
private fun GenericProfileGlyph(
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val color = DominoSemanticColors.brandText
        val stroke = 2.dp.toPx()

        drawCircle(
            color = color,
            radius = size.minDimension * 0.16f,
            center = Offset(
                x = size.width * 0.50f,
                y = size.height * 0.30f,
            ),
            style = Stroke(
                width = stroke,
            ),
        )

        drawArc(
            color = color,
            startAngle = 205f,
            sweepAngle = 130f,
            useCenter = false,
            topLeft = Offset(
                x = size.width * 0.16f,
                y = size.height * 0.46f,
            ),
            size = Size(
                width = size.width * 0.68f,
                height = size.height * 0.46f,
            ),
            style = Stroke(
                width = stroke,
                cap = StrokeCap.Round,
            ),
        )
    }
}

@Composable
internal fun IdentityHubScreen(
    state: IdentityHubUiState,
    onBackClick: () -> Unit,
    onManageProfileClick: () -> Unit,
    onDisconnectClick: () -> Boolean,
    onHandAppearanceClick: (() -> Unit)? = null,
) {
    var disconnectConfirmationVisible by remember {
        mutableStateOf(false)
    }
    var disconnectConfirmationFailed by remember {
        mutableStateOf(false)
    }
    val profileActionTitle = when (state.accountState) {
        IdentityHubAccountState.CONNECTED ->
            stringResource(R.string.identity_hub_edit_profile)

        IdentityHubAccountState.RECOVERY_REQUIRED ->
            stringResource(R.string.identity_hub_recover_access)

        IdentityHubAccountState.UNAVAILABLE,
        IdentityHubAccountState.DISCONNECTED ->
            stringResource(R.string.identity_hub_sign_in_or_create)
    }
    val profileActionSupportingText = when (state.accountState) {
        IdentityHubAccountState.CONNECTED ->
            stringResource(R.string.identity_hub_edit_profile_support)

        IdentityHubAccountState.RECOVERY_REQUIRED ->
            stringResource(R.string.identity_hub_recover_access_support)

        IdentityHubAccountState.UNAVAILABLE,
        IdentityHubAccountState.DISCONNECTED ->
            stringResource(R.string.identity_hub_sign_in_or_create_support)
    }
    val accountSupportingText = when (state.accountState) {
        IdentityHubAccountState.CONNECTED ->
            stringResource(R.string.identity_hub_account_support)

        IdentityHubAccountState.RECOVERY_REQUIRED ->
            stringResource(R.string.identity_hub_account_recovery_support)

        IdentityHubAccountState.UNAVAILABLE,
        IdentityHubAccountState.DISCONNECTED ->
            stringResource(R.string.identity_hub_account_signed_out_support)
    }
    val accountAction = if (
        identityHubAccountSupportsDisconnect(
            state.accountState,
        )
    ) {
        {
            disconnectConfirmationFailed = false
            disconnectConfirmationVisible = true
        }
    } else {
        null
    }

    DominoScreenScaffold(
        title = stringResource(R.string.account_title),
        layout = DominoScreenLayout.Guided,
        contentModifier = Modifier.testTag(
            IdentityHubScreenTag,
        ),
        onBackClick = onBackClick,
        backContentDescription = stringResource(
            R.string.common_back,
        ),
    ) {
        Spacer(
            modifier = Modifier.height(
                MaterialTheme.dominoSpacing.lg,
            ),
        )

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(
                MaterialTheme.dominoSpacing.md,
            ),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            IdentityAvatar(
                label = state.headerAvatarLabel,
                size = 80.dp,
                modifier = Modifier.testTag(
                    IdentityHubAvatarTag,
                ),
                profilePhotoUri =
                    state.headerProfilePhotoUri,
            )

            Text(
                modifier = Modifier.testTag(
                    IdentityHubDisplayNameTag,
                ),
                text =
                    state.headerDisplayName
                        ?: if (
                            state.accountState ==
                                IdentityHubAccountState.CONNECTED
                        ) {
                            stringResource(
                                R.string.identity_profile_fallback,
                            )
                        } else {
                            stringResource(
                                R.string.identity_hub_account_heading,
                            )
                        },
                color = DominoSemanticColors.brandText,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )

            state.headerTableName?.let { tableName ->
                Text(
                    modifier = Modifier.testTag(
                        IdentityHubTableNameTag,
                    ),
                    text = stringResource(
                        R.string.account_table_code,
                    ) + ": " + tableName,
                    color = DominoSemanticColors.brandSupportingText,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                )
            }

            Spacer(
                modifier = Modifier.height(
                    MaterialTheme.dominoSpacing.xs,
                ),
            )

            IdentityHubSectionTitle(
                text = stringResource(
                    R.string.identity_hub_profile_section,
                ),
            )

            IdentityHubRow(
                modifier = Modifier.testTag(
                    IdentityHubEditProfileActionTag,
                ),
                title = profileActionTitle,
                supportingText = profileActionSupportingText,
                icon = IdentityHubRowIcon.Profile,
                onClick = onManageProfileClick,
            )

            IdentityHubSectionTitle(
                text = stringResource(
                    R.string.identity_hub_personalization_section,
                ),
            )

            IdentityHubRow(
                modifier = Modifier.testTag(
                    IdentityHubHandAppearanceTag,
                ),
                title = stringResource(
                    R.string.identity_hub_hand_appearance,
                ),
                supportingText = stringResource(
                    R.string.identity_hub_hand_appearance_support,
                ),
                icon = IdentityHubRowIcon.Personalization,

                onClick = onHandAppearanceClick,
            )

            IdentityHubSectionTitle(
                text = stringResource(
                    R.string.identity_hub_account_section,
                ),
            )

            IdentityHubRow(
                modifier = Modifier.testTag(
                    IdentityHubAccountActionTag,
                ),
                title = identityHubAccountStateLabel(
                    state.accountState,
                ),
                supportingText = accountSupportingText,
                icon = IdentityHubRowIcon.Account,
                onClick = accountAction,
            )
        }
    }

    if (disconnectConfirmationVisible) {
        AlertDialog(
            onDismissRequest = {
                disconnectConfirmationVisible = false
                disconnectConfirmationFailed = false
            },
            title = {
                Text(
                    text = stringResource(
                        R.string.identity_hub_disconnect_confirm_title,
                    ),
                )
            },
            text = {
                Text(
                    text = if (disconnectConfirmationFailed) {
                        stringResource(
                            R.string.identity_hub_disconnect_failed,
                        )
                    } else {
                        stringResource(
                            R.string.identity_hub_disconnect_confirm_body,
                        )
                    },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (onDisconnectClick()) {
                            disconnectConfirmationVisible = false
                            disconnectConfirmationFailed = false
                        } else {
                            disconnectConfirmationFailed = true
                        }
                    },
                ) {
                    Text(
                        text = stringResource(
                            R.string.identity_hub_disconnect_confirm_action,
                        ),
                        color = DominoSemanticColors.dialogAction,
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        disconnectConfirmationVisible = false
                        disconnectConfirmationFailed = false
                    },
                ) {
                    Text(
                        text = stringResource(
                            R.string.identity_hub_disconnect_cancel,
                        ),
                        color = DominoSemanticColors.dialogDismissAction,
                    )
                }
            },
            containerColor = DominoSemanticColors.dialogSurface,
            titleContentColor = DominoSemanticColors.dialogTitle,
            textContentColor = DominoSemanticColors.dialogBody,
        )
    }
}

@Composable
private fun IdentityHubSectionTitle(
    text: String,
) {
    Text(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                top = MaterialTheme.dominoSpacing.xs,
            ),
        text = text,
        color = DominoSemanticColors.brandText,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
    )
}

private enum class IdentityHubRowIcon {
    Profile,
    Personalization,
    Account,
}

@Composable
private fun IdentityHubRow(
    title: String,
    supportingText: String,
    icon: IdentityHubRowIcon,
    modifier: Modifier = Modifier,
    trailingText: String? = null,
    onClick: (() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(18.dp)
    val interactionModifier = if (onClick != null) {
        Modifier.clickable(
            role = Role.Button,
            onClick = onClick,
        )
    } else {
        Modifier
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .then(interactionModifier),
        shape = shape,
        color = DominoSemanticColors.brandSurfaceElevated,
        contentColor = DominoSemanticColors.brandText,
        border = BorderStroke(
            width = 1.dp,
            color = DominoSemanticColors.brandBorder,
        ),
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = MaterialTheme.dominoSpacing.md,
                vertical = MaterialTheme.dominoSpacing.sm,
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(42.dp),
                shape = CircleShape,
                color = DominoSemanticColors.brandBackground,
                contentColor = DominoSemanticColors.brandText,
                border = BorderStroke(
                    width = 1.dp,
                    color = DominoSemanticColors.brandBorder,
                ),
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                ) {
                    IdentityHubRowGlyph(
                        icon = icon,
                    )
                }
            }

            Spacer(
                modifier = Modifier.width(
                    MaterialTheme.dominoSpacing.sm,
                ),
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(
                    MaterialTheme.dominoSpacing.xxs,
                ),
            ) {
                Text(
                    text = title,
                    color = DominoSemanticColors.brandText,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )

                Text(
                    text = supportingText,
                    color = DominoSemanticColors.brandSupportingText,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            trailingText?.let { value ->
                Spacer(
                    modifier = Modifier.width(
                        MaterialTheme.dominoSpacing.sm,
                    ),
                )

                Text(
                    text = value,
                    color = DominoSemanticColors.brandSupportingText,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            if (onClick != null) {
                Spacer(
                    modifier = Modifier.width(
                        MaterialTheme.dominoSpacing.xs,
                    ),
                )

                IdentityHubChevron()
            }
        }
    }
}

@Composable
private fun IdentityHubRowGlyph(
    icon: IdentityHubRowIcon,
) {
    Canvas(
        modifier = Modifier.size(22.dp),
    ) {
        val color = DominoSemanticColors.brandText
        val stroke = 2.dp.toPx()

        when (icon) {
            IdentityHubRowIcon.Profile -> {
                drawCircle(
                    color = color,
                    radius = size.minDimension * 0.16f,
                    center = Offset(
                        x = size.width * 0.50f,
                        y = size.height * 0.30f,
                    ),
                    style = Stroke(
                        width = stroke,
                    ),
                )

                drawArc(
                    color = color,
                    startAngle = 205f,
                    sweepAngle = 130f,
                    useCenter = false,
                    topLeft = Offset(
                        x = size.width * 0.16f,
                        y = size.height * 0.46f,
                    ),
                    size = Size(
                        width = size.width * 0.68f,
                        height = size.height * 0.46f,
                    ),
                    style = Stroke(
                        width = stroke,
                        cap = StrokeCap.Round,
                    ),
                )
            }

            IdentityHubRowIcon.Personalization -> {
                val radius = size.minDimension * 0.10f
                val centers = listOf(
                    Offset(size.width * 0.30f, size.height * 0.36f),
                    Offset(size.width * 0.60f, size.height * 0.28f),
                    Offset(size.width * 0.70f, size.height * 0.62f),
                    Offset(size.width * 0.38f, size.height * 0.72f),
                )

                centers.forEachIndexed { index, center ->
                    drawCircle(
                        color = color.copy(
                            alpha = 0.45f + index * 0.15f,
                        ),
                        radius = radius,
                        center = center,
                    )
                }
            }

            IdentityHubRowIcon.Account -> {
                drawCircle(
                    color = color,
                    radius = size.minDimension * 0.38f,
                    center = center,
                    style = Stroke(
                        width = stroke,
                    ),
                )

                drawLine(
                    color = color,
                    start = Offset(
                        x = size.width * 0.31f,
                        y = size.height * 0.52f,
                    ),
                    end = Offset(
                        x = size.width * 0.45f,
                        y = size.height * 0.65f,
                    ),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round,
                )

                drawLine(
                    color = color,
                    start = Offset(
                        x = size.width * 0.45f,
                        y = size.height * 0.65f,
                    ),
                    end = Offset(
                        x = size.width * 0.72f,
                        y = size.height * 0.36f,
                    ),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}

@Composable
private fun IdentityHubChevron() {
    Canvas(
        modifier = Modifier.size(18.dp),
    ) {
        val color = DominoSemanticColors.brandText
        val stroke = 2.2.dp.toPx()

        drawLine(
            color = color,
            start = Offset(
                x = size.width * 0.36f,
                y = size.height * 0.24f,
            ),
            end = Offset(
                x = size.width * 0.64f,
                y = size.height * 0.50f,
            ),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )

        drawLine(
            color = color,
            start = Offset(
                x = size.width * 0.64f,
                y = size.height * 0.50f,
            ),
            end = Offset(
                x = size.width * 0.36f,
                y = size.height * 0.76f,
            ),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
    }
}

@Composable
private fun identityHubAccountStateLabel(
    state: IdentityHubAccountState,
): String {
    return when (state) {
        IdentityHubAccountState.UNAVAILABLE,
        IdentityHubAccountState.DISCONNECTED ->
            stringResource(R.string.account_state_disconnected)

        IdentityHubAccountState.CONNECTED ->
            stringResource(R.string.account_state_connected)

        IdentityHubAccountState.RECOVERY_REQUIRED ->
            stringResource(
                R.string.account_state_recovery_required,
            )
    }
}
