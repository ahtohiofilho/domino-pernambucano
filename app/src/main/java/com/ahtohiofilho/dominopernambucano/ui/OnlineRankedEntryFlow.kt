package com.ahtohiofilho.dominopernambucano.ui

import com.ahtohiofilho.dominopernambucano.online.OnlineGoogleAccountStatus

internal fun enterOnlineRankedFlow(
    accountStatus: OnlineGoogleAccountStatus,
    openAccountSetup: () -> Unit,
    restoreSavedAccount: () -> Unit,
    openMatchmaking: () -> Unit,
) {
    when (accountStatus) {
        OnlineGoogleAccountStatus.CONNECTED -> openMatchmaking()
        OnlineGoogleAccountStatus.RECOVERY_REQUIRED -> restoreSavedAccount()
        OnlineGoogleAccountStatus.UNAVAILABLE,
        OnlineGoogleAccountStatus.NO_LOCAL_CREDENTIAL,
        OnlineGoogleAccountStatus.VISITOR -> openAccountSetup()
    }
}
