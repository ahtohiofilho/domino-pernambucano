package com.ahtohiofilho.dominopernambucano.ui

import com.ahtohiofilho.dominopernambucano.online.isValidOnlineAccountTableCode
import com.ahtohiofilho.dominopernambucano.online.normalizeOnlineAccountTableCodeInput
import com.ahtohiofilho.dominopernambucano.online.suggestOnlineAccountTableCode

internal enum class TableIdentityIntent {
    ONLINE_MATCHMAKING,
    OFFLINE_MATCH,
    CREATE_PRIVATE_ROOM,
    JOIN_PRIVATE_ROOM,
}

internal data class TableIdentityGateResolution(
    val confirmedCode: String?,
    val suggestedCode: String,
) {
    val requiresConfirmation: Boolean
        get() = confirmedCode == null
}

internal fun resolveTableIdentityGate(
    accountConnected: Boolean,
    connectedCodeConfirmedByPlayer: Boolean,
    connectedTableCode: String,
    storedOfflineTableCode: String?,
    displayName: String,
): TableIdentityGateResolution {
    val normalizedConnected =
        normalizeOnlineAccountTableCodeInput(connectedTableCode)
    val normalizedOffline =
        normalizeOnlineAccountTableCodeInput(storedOfflineTableCode)

    val connectedRecord =
        if (
            accountConnected &&
            connectedCodeConfirmedByPlayer &&
            isValidOnlineAccountTableCode(connectedTableCode)
        ) {
            normalizedConnected
        } else {
            null
        }

    val offlineRecord =
        if (isValidOnlineAccountTableCode(storedOfflineTableCode)) {
            normalizedOffline
        } else {
            null
        }

    val confirmed = connectedRecord ?: offlineRecord

    val suggestion = confirmed
        ?: normalizedConnected
            .takeIf {
                isValidOnlineAccountTableCode(connectedTableCode)
            }
        ?: suggestOnlineAccountTableCode(displayName)

    return TableIdentityGateResolution(
        confirmedCode = confirmed,
        suggestedCode = suggestion,
    )
}