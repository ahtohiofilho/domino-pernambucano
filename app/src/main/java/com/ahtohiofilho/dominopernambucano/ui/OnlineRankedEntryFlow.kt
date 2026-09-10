package com.ahtohiofilho.dominopernambucano.ui

/*
 * Ranked entry keeps account access separate from matchmaking.
 *
 * An already connected account enters matchmaking immediately.
 * Otherwise the player first sees the Dominó PE account-entry surface and
 * explicitly chooses how to authenticate. External providers such as Google
 * must never be launched automatically from the ranked-game button.
 */
internal fun enterOnlineRankedFlow(
    accountConnected: Boolean,
    openAccountSetup: () -> Unit,
    openMatchmaking: () -> Unit,
) {
    if (accountConnected) {
        openMatchmaking()
        return
    }

    openAccountSetup()
}
