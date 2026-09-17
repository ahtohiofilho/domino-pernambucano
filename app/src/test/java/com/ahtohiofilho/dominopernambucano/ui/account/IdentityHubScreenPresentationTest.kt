package com.ahtohiofilho.dominopernambucano.ui.account

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
    fun full_generic_table_name_does_not_become_fake_identity() {
        assertNull(
            identityHubAvatarLabel(
                displayName = "Jogador",
                tableName = "JOGADOR",
            ),
        )
        assertNull(
            identityHubCompactDisplayName(
                displayName = "Jogador",
                tableName = "JOGADOR",
            ),
        )
        assertNull(
            identityHubMeaningfulTableName(
                displayName = "Jogador",
                tableName = "JOGADOR",
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

    @Test
    fun only_connected_account_exposes_disconnect_action() {
        assertTrue(
            identityHubAccountSupportsDisconnect(
                IdentityHubAccountState.CONNECTED,
            ),
        )
        assertFalse(
            identityHubAccountSupportsDisconnect(
                IdentityHubAccountState.DISCONNECTED,
            ),
        )
        assertFalse(
            identityHubAccountSupportsDisconnect(
                IdentityHubAccountState.RECOVERY_REQUIRED,
            ),
        )
        assertFalse(
            identityHubAccountSupportsDisconnect(
                IdentityHubAccountState.UNAVAILABLE,
            ),
        )
    }

    @Test
    fun disconnected_state_hides_cached_identity_from_header() {
        val state = IdentityHubUiState(
            displayName = "Maria Silva",
            tableName = "MAS",
            accountState = IdentityHubAccountState.DISCONNECTED,
        )

        assertNull(state.headerAvatarLabel)
        assertNull(state.headerDisplayName)
        assertNull(state.headerTableName)
    }

    @Test
    fun recovery_state_hides_cached_identity_from_header() {
        val state = IdentityHubUiState(
            displayName = "Maria Silva",
            tableName = "MAS",
            accountState = IdentityHubAccountState.RECOVERY_REQUIRED,
        )

        assertNull(state.headerAvatarLabel)
        assertNull(state.headerDisplayName)
        assertNull(state.headerTableName)
    }

    @Test
    fun connected_state_exposes_real_identity_in_header() {
        val state = IdentityHubUiState(
            displayName = "Maria Silva",
            tableName = "MAS",
            accountState = IdentityHubAccountState.CONNECTED,
        )

        assertEquals("MS", state.headerAvatarLabel)
        assertEquals("Maria Silva", state.headerDisplayName)
        assertEquals("MAS", state.headerTableName)
    }

    @Test
    fun connected_state_exposes_normalized_https_profile_photo() {
        val state = IdentityHubUiState(
            displayName = "Maria Silva",
            tableName = "MAS",
            accountState = IdentityHubAccountState.CONNECTED,
            profilePhotoUri =
                " https://example.test/profile.jpg ",
        )

        assertEquals(
            "https://example.test/profile.jpg",
            state.headerProfilePhotoUri,
        )
    }

    @Test
    fun disconnected_state_hides_cached_profile_photo() {
        val state = IdentityHubUiState(
            displayName = "Maria Silva",
            tableName = "MAS",
            accountState = IdentityHubAccountState.DISCONNECTED,
            profilePhotoUri =
                "https://example.test/profile.jpg",
        )

        assertNull(state.headerProfilePhotoUri)
    }

    @Test
    fun profile_photo_accepts_only_https_remote_urls() {
        assertEquals(
            "https://example.test/profile.jpg",
            identityHubUsableProfilePhotoUri(
                " https://example.test/profile.jpg ",
            ),
        )
        assertNull(
            identityHubUsableProfilePhotoUri(
                "http://example.test/profile.jpg",
            ),
        )
        assertNull(
            identityHubUsableProfilePhotoUri(
                "file:///tmp/profile.jpg",
            ),
        )
        assertNull(
            identityHubUsableProfilePhotoUri(
                "not a uri",
            ),
        )
    }
}
