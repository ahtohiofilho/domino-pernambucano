package com.ahtohiofilho.dominopernambucano.ui.account

/**
 * Identifies why the Identity Hub was opened.
 *
 * Entry point and return intent are intentionally separate:
 * authentication or onboarding may change the current Hub stage,
 * but must never silently discard the flow that brought the player here.
 */
internal enum class IdentityHubEntryPoint {
    MAIN_MENU_PROFILE,
    LEGACY_ACCOUNT_CARD,
    RANKED_ACCOUNT_REQUIRED,
    POST_AUTH_ONBOARDING,
}

/**
 * Destination that must be resumed when the Identity Hub finishes.
 *
 * This is deliberately provider-agnostic. Google, password and future
 * authentication providers must all preserve the same navigation contract.
 */
internal enum class IdentityHubReturnIntent {
    MAIN_MENU,
    RESUME_RANKED_ENTRY,
}

internal data class IdentityHubLaunchRequest(
    val entryPoint: IdentityHubEntryPoint,
    val returnIntent: IdentityHubReturnIntent,
) {
    /**
     * Authentication can advance the Hub into onboarding without changing
     * the destination that originally requested identity/account setup.
     */
    fun afterAuthentication(): IdentityHubLaunchRequest {
        return copy(
            entryPoint = IdentityHubEntryPoint.POST_AUTH_ONBOARDING,
        )
    }
}

internal fun mainMenuProfileIdentityHubRequest():
    IdentityHubLaunchRequest {
    return IdentityHubLaunchRequest(
        entryPoint = IdentityHubEntryPoint.MAIN_MENU_PROFILE,
        returnIntent = IdentityHubReturnIntent.MAIN_MENU,
    )
}

internal fun legacyAccountIdentityHubRequest():
    IdentityHubLaunchRequest {
    return IdentityHubLaunchRequest(
        entryPoint = IdentityHubEntryPoint.LEGACY_ACCOUNT_CARD,
        returnIntent = IdentityHubReturnIntent.MAIN_MENU,
    )
}

internal fun rankedIdentityHubRequest():
    IdentityHubLaunchRequest {
    return IdentityHubLaunchRequest(
        entryPoint = IdentityHubEntryPoint.RANKED_ACCOUNT_REQUIRED,
        returnIntent = IdentityHubReturnIntent.RESUME_RANKED_ENTRY,
    )
}