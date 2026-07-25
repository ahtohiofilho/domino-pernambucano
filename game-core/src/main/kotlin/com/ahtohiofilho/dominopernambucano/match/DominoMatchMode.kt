package com.ahtohiofilho.dominopernambucano.match

import com.ahtohiofilho.dominopernambucano.competitive.RankedMatchClassification
import kotlinx.serialization.Serializable

/**
 * Canonical product-level match modes.
 *
 * This model is deliberately more expressive than the legacy ranked/unranked
 * classification. It separates queue visibility, identity requirements,
 * competitive persistence, server hosting, and MVP availability.
 */
@Serializable
enum class DominoMatchMode(
    val isServerHosted: Boolean,
    val usesPublicMatchmaking: Boolean,
    val requiresAuthenticatedAccount: Boolean,
    val contributesToRanking: Boolean,
    val isEnabledInMvp: Boolean,
) {
    /**
     * The single public matchmaking pool in the MVP.
     */
    PUBLIC_RANKED(
        isServerHosted = true,
        usesPublicMatchmaking = true,
        requiresAuthenticatedAccount = true,
        contributesToRanking = true,
        isEnabledInMvp = true,
    ),

    /**
     * Code/invite-based rooms. Accounts and visitors are both allowed.
     */
    PRIVATE_UNRANKED(
        isServerHosted = true,
        usesPublicMatchmaking = false,
        requiresAuthenticatedAccount = false,
        contributesToRanking = false,
        isEnabledInMvp = true,
    ),

    /**
     * Local play, outside the online competitive infrastructure.
     */
    OFFLINE_LOCAL(
        isServerHosted = false,
        usesPublicMatchmaking = false,
        requiresAuthenticatedAccount = false,
        contributesToRanking = false,
        isEnabledInMvp = true,
    ),

    /**
     * Reserved for a future public casual pool. It is intentionally disabled
     * in the MVP to avoid fragmenting matchmaking liquidity.
     */
    PUBLIC_CASUAL(
        isServerHosted = true,
        usesPublicMatchmaking = true,
        requiresAuthenticatedAccount = true,
        contributesToRanking = false,
        isEnabledInMvp = false,
    ),
    ;

    val allowsAnonymousIdentity: Boolean
        get() = !requiresAuthenticatedAccount

    val rankedMatchClassification: RankedMatchClassification
        get() = if (contributesToRanking) {
            RankedMatchClassification.RANKED
        } else {
            RankedMatchClassification.UNRANKED
        }
}
