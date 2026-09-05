package com.ahtohiofilho.dominopernambucano.ui

/*
 * Ranked entry is intentionally split into three responsibilities:
 *
 * 1) authenticate the person;
 * 2) complete the current account-setup/onboarding contract;
 * 3) enter matchmaking only from an already connected account.
 *
 * Account setup is deliberately generic. Today it may collect only the MVP
 * fields. Future production fields belong behind [openAccountSetup] and must
 * not require coupling authentication directly to matchmaking again.
 */
internal fun enterOnlineRankedFlow(
    accountConnected: Boolean,
    authenticate: (onConnected: () -> Unit) -> Unit,
    openAccountSetup: () -> Unit,
    openMatchmaking: () -> Unit,
) {
    if (accountConnected) {
        openMatchmaking()
        return
    }

    authenticate {
        openAccountSetup()
    }
}
