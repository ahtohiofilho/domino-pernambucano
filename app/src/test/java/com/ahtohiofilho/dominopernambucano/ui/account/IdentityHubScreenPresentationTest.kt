package com.ahtohiofilho.dominopernambucano.ui.account

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IdentityHubScreenPresentationTest {
    @Test
    fun two_word_name_uses_first_and_last_initials() {
        assertEquals(
            "AF",
            identityHubAvatarLabel(
                displayName = "Antônio Filho",
                tableName = "ANT",
            ),
        )
    }

    @Test
    fun long_name_uses_first_and_last_meaningful_initials() {
        assertEquals(
            "RA",
            identityHubAvatarLabel(
                displayName = "Roselle Fernanda Santos de Araujo",
                tableName = "ROS",
            ),
        )
    }

    @Test
    fun single_name_uses_single_initial() {
        assertEquals(
            "A",
            identityHubAvatarLabel(
                displayName = "Antônio",
                tableName = "ANT",
            ),
        )
    }

    @Test
    fun compact_display_name_preserves_meaningful_public_name() {
        assertEquals(
            "Antônio Filho",
            identityHubCompactDisplayName(
                displayName = "Antônio Filho",
                tableName = "ANT",
            ),
        )
    }

    @Test
    fun blank_display_name_uses_meaningful_short_name() {
        assertEquals(
            "A",
            identityHubAvatarLabel(
                displayName = "   ",
                tableName = "ant",
            ),
        )
        assertEquals(
            "ANT",
            identityHubCompactDisplayName(
                displayName = "   ",
                tableName = "ant",
            ),
        )
    }

    @Test
    fun generic_legacy_identity_uses_profile_icon_instead_of_fake_initial() {
        assertNull(
            identityHubAvatarLabel(
                displayName = "Jogador",
                tableName = "JOG",
            ),
        )
        assertNull(
            identityHubCompactDisplayName(
                displayName = "Jogador",
                tableName = "JOG",
            ),
        )
        assertNull(
            identityHubMeaningfulTableName(
                displayName = "Jogador",
                tableName = "JOG",
            ),
        )
    }

    @Test
    fun completely_empty_identity_uses_generic_profile_presentation() {
        assertNull(
            identityHubAvatarLabel(
                displayName = null,
                tableName = null,
            ),
        )
        assertNull(
            identityHubCompactDisplayName(
                displayName = null,
                tableName = null,
            ),
        )
    }

    @Test
    fun meaningful_public_name_keeps_short_name_visible() {
        assertEquals(
            "JOG",
            identityHubMeaningfulTableName(
                displayName = "João Gomes",
                tableName = "JOG",
            ),
        )
    }

    @Test
    fun ui_state_derives_two_initials_without_provider_specific_data() {
        val state = IdentityHubUiState(
            displayName = "Maria Silva",
            tableName = "MAS",
            accountState = IdentityHubAccountState.CONNECTED,
        )

        assertEquals("MS", state.avatarLabel)
        assertEquals("Maria Silva", state.compactDisplayName)
        assertEquals("MAS", state.meaningfulTableName)
    }
}
