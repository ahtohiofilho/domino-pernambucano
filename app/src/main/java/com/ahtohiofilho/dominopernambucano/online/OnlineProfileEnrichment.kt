package com.ahtohiofilho.dominopernambucano.online

import android.content.Context
import android.content.SharedPreferences

private const val ONLINE_PROFILE_ENRICHMENT_PREFERENCES_NAME =
    "online_profile_enrichment"
private const val ONLINE_PROFILE_ACCOUNT_ID_KEY =
    "google_account_id_v1"
private const val ONLINE_PROFILE_PLAYER_ID_KEY =
    "google_player_id_v1"
private const val ONLINE_PROFILE_PHOTO_URI_KEY =
    "google_profile_photo_uri_v1"

data class OnlineProfileEnrichment(
    val accountId: String? = null,
    val playerId: String? = null,
    val googleProfilePhotoUri: String? = null,
) {
    fun googleProfilePhotoUriFor(
        credential: OnlineSessionCredential?,
    ): String? {
        if (credential?.sessionKind != OnlineSessionKind.ACCOUNT) {
            return null
        }

        val currentAccountId = credential.accountId
            ?.trim()
            ?.takeIf(String::isNotBlank)
            ?: return null

        val currentPlayerId = credential.playerId
            .trim()
            .takeIf(String::isNotBlank)
            ?: return null

        if (
            currentAccountId != accountId ||
            currentPlayerId != playerId
        ) {
            return null
        }

        return normalizeOnlineProfilePhotoUri(
            googleProfilePhotoUri,
        )
    }

    companion object {
        val Empty = OnlineProfileEnrichment()
    }
}

interface OnlineProfileEnrichmentStore {
    fun read(): OnlineProfileEnrichment

    fun writeGoogleProfile(
        accountId: String,
        playerId: String,
        profilePhotoUri: String?,
    ): OnlineProfileEnrichment
}

class SharedPreferencesOnlineProfileEnrichmentStore(
    context: Context,
) : OnlineProfileEnrichmentStore {
    private val preferences: SharedPreferences =
        context.applicationContext.getSharedPreferences(
            ONLINE_PROFILE_ENRICHMENT_PREFERENCES_NAME,
            Context.MODE_PRIVATE,
        )

    override fun read(): OnlineProfileEnrichment {
        val accountId = preferences.getString(
            ONLINE_PROFILE_ACCOUNT_ID_KEY,
            null,
        )
            ?.trim()
            ?.takeIf(String::isNotBlank)

        val playerId = preferences.getString(
            ONLINE_PROFILE_PLAYER_ID_KEY,
            null,
        )
            ?.trim()
            ?.takeIf(String::isNotBlank)

        if (accountId == null || playerId == null) {
            return OnlineProfileEnrichment.Empty
        }

        return OnlineProfileEnrichment(
            accountId = accountId,
            playerId = playerId,
            googleProfilePhotoUri = normalizeOnlineProfilePhotoUri(
                preferences.getString(
                    ONLINE_PROFILE_PHOTO_URI_KEY,
                    null,
                ),
            ),
        )
    }

    override fun writeGoogleProfile(
        accountId: String,
        playerId: String,
        profilePhotoUri: String?,
    ): OnlineProfileEnrichment {
        val normalizedAccountId = accountId
            .trim()
            .also { value ->
                require(value.isNotBlank()) {
                    "O accountId do enriquecimento de perfil não pode ser vazio."
                }
            }

        val normalizedPlayerId = playerId
            .trim()
            .also { value ->
                require(value.isNotBlank()) {
                    "O playerId do enriquecimento de perfil não pode ser vazio."
                }
            }

        val normalizedPhotoUri =
            normalizeOnlineProfilePhotoUri(profilePhotoUri)

        val editor = preferences.edit()
            .putString(
                ONLINE_PROFILE_ACCOUNT_ID_KEY,
                normalizedAccountId,
            )
            .putString(
                ONLINE_PROFILE_PLAYER_ID_KEY,
                normalizedPlayerId,
            )

        if (normalizedPhotoUri == null) {
            editor.remove(ONLINE_PROFILE_PHOTO_URI_KEY)
        } else {
            editor.putString(
                ONLINE_PROFILE_PHOTO_URI_KEY,
                normalizedPhotoUri,
            )
        }

        editor.apply()

        return OnlineProfileEnrichment(
            accountId = normalizedAccountId,
            playerId = normalizedPlayerId,
            googleProfilePhotoUri = normalizedPhotoUri,
        )
    }
}

internal fun normalizeOnlineProfilePhotoUri(
    rawUri: String?,
): String? {
    return rawUri
        ?.trim()
        ?.takeIf(String::isNotBlank)
}
