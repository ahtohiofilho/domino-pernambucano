package com.ahtohiofilho.dominopernambucano.ui.account

import org.junit.Assert.assertEquals
import org.junit.Test

class IdentityHubNavigationContractTest {
    @Test
    fun profile_chip_returns_to_main_menu() {
        assertEquals(
            IdentityHubLaunchRequest(
                entryPoint = IdentityHubEntryPoint.MAIN_MENU_PROFILE,
                returnIntent = IdentityHubReturnIntent.MAIN_MENU,
            ),
            mainMenuProfileIdentityHubRequest(),
        )
    }

    @Test
    fun legacy_account_card_returns_to_main_menu() {
        assertEquals(
            IdentityHubLaunchRequest(
                entryPoint = IdentityHubEntryPoint.LEGACY_ACCOUNT_CARD,
                returnIntent = IdentityHubReturnIntent.MAIN_MENU,
            ),
            legacyAccountIdentityHubRequest(),
        )
    }

    @Test
    fun ranked_identity_requirement_preserves_ranked_destination() {
        assertEquals(
            IdentityHubLaunchRequest(
                entryPoint = IdentityHubEntryPoint.RANKED_ACCOUNT_REQUIRED,
                returnIntent =
                    IdentityHubReturnIntent.RESUME_RANKED_ENTRY,
            ),
            rankedIdentityHubRequest(),
        )
    }

    @Test
    fun authentication_from_ranked_flow_preserves_ranked_return_intent() {
        val request =
            rankedIdentityHubRequest()
                .afterAuthentication()

        assertEquals(
            IdentityHubEntryPoint.POST_AUTH_ONBOARDING,
            request.entryPoint,
        )
        assertEquals(
            IdentityHubReturnIntent.RESUME_RANKED_ENTRY,
            request.returnIntent,
        )
    }

    @Test
    fun authentication_from_main_menu_preserves_main_menu_return_intent() {
        val request =
            mainMenuProfileIdentityHubRequest()
                .afterAuthentication()

        assertEquals(
            IdentityHubEntryPoint.POST_AUTH_ONBOARDING,
            request.entryPoint,
        )
        assertEquals(
            IdentityHubReturnIntent.MAIN_MENU,
            request.returnIntent,
        )
    }
}