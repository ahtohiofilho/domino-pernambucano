package com.ahtohiofilho.dominopernambucano.ui.account

import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val IDENTITY_PROFILE_PHOTO_CONNECT_TIMEOUT_MILLIS = 4_000
private const val IDENTITY_PROFILE_PHOTO_READ_TIMEOUT_MILLIS = 4_000
private const val IDENTITY_PROFILE_PHOTO_MAX_DECLARED_BYTES = 5_000_000L

internal fun identityHubUsableProfilePhotoUri(
    rawUri: String?,
): String? {
    val normalized = rawUri
        ?.trim()
        ?.takeIf(String::isNotBlank)
        ?: return null

    val parsed = try {
        URI(normalized)
    } catch (_: Exception) {
        return null
    }

    if (
        !parsed.scheme.equals("https", ignoreCase = true) ||
        parsed.host.isNullOrBlank()
    ) {
        return null
    }

    return normalized
}

@Composable
internal fun rememberIdentityProfilePhotoBitmap(
    profilePhotoUri: String?,
): ImageBitmap? {
    val normalizedUri =
        identityHubUsableProfilePhotoUri(profilePhotoUri)

    var bitmap by remember(normalizedUri) {
        mutableStateOf<ImageBitmap?>(null)
    }

    LaunchedEffect(normalizedUri) {
        bitmap = if (normalizedUri == null) {
            null
        } else {
            withContext(Dispatchers.IO) {
                loadIdentityProfilePhotoBitmap(normalizedUri)
            }
        }
    }

    return bitmap
}

private fun loadIdentityProfilePhotoBitmap(
    profilePhotoUri: String,
): ImageBitmap? {
    var connection: HttpURLConnection? = null

    return try {
        connection = URL(profilePhotoUri)
            .openConnection() as? HttpURLConnection
            ?: return null

        connection.requestMethod = "GET"
        connection.instanceFollowRedirects = true
        connection.connectTimeout =
            IDENTITY_PROFILE_PHOTO_CONNECT_TIMEOUT_MILLIS
        connection.readTimeout =
            IDENTITY_PROFILE_PHOTO_READ_TIMEOUT_MILLIS
        connection.useCaches = true
        connection.setRequestProperty(
            "Accept",
            "image/*",
        )

        val responseCode = connection.responseCode
        if (responseCode !in 200..299) {
            return null
        }

        if (
            !connection.url.protocol.equals(
                "https",
                ignoreCase = true,
            )
        ) {
            return null
        }

        val contentType = connection.contentType.orEmpty()
        if (
            contentType.isNotBlank() &&
            !contentType.startsWith(
                prefix = "image/",
                ignoreCase = true,
            )
        ) {
            return null
        }

        val declaredLength = connection.contentLengthLong
        if (
            declaredLength >
            IDENTITY_PROFILE_PHOTO_MAX_DECLARED_BYTES
        ) {
            return null
        }

        val androidBitmap = connection.inputStream.use { input ->
            BitmapFactory.decodeStream(input)
        } ?: return null

        androidBitmap.asImageBitmap()
    } catch (_: Exception) {
        null
    } finally {
        connection?.disconnect()
    }
}
