package com.ahtohiofilho.dominopernambucano.advertising

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.viewinterop.AndroidView
import com.ahtohiofilho.dominopernambucano.BuildConfig
import com.google.android.libraries.ads.mobile.sdk.banner.AdSize
import com.google.android.libraries.ads.mobile.sdk.banner.AdView
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAd
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAdRequest
import com.google.android.libraries.ads.mobile.sdk.common.AdLoadCallback
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError
import kotlin.math.roundToInt

@Composable
fun DominoBannerAd(
    placement: AdvertisingPlacement,
    adsReady: Boolean,
    modifier: Modifier = Modifier,
) {
    val adUnitId = BuildConfig.ADMOB_BANNER_UNIT_ID
    val inspectionMode = LocalInspectionMode.current

    if (
        !adsReady ||
        inspectionMode ||
        adUnitId.isBlank() ||
        !AdvertisingPlacementPolicy.allowsBanner(placement)
    ) {
        return
    }

    val context = LocalContext.current

    BoxWithConstraints(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        val widthDp = maxWidth.value
            .roundToInt()
            .coerceAtLeast(1)
        val adSize = remember(
            context,
            widthDp,
        ) {
            AdSize.getLargeAnchoredAdaptiveBannerAdSize(
                context,
                widthDp,
            )
        }
        val adView = remember(
            context,
            adUnitId,
            adSize,
        ) {
            AdView(context)
        }

        DisposableEffect(adView) {
            onDispose {
                adView.destroy()
            }
        }

        LaunchedEffect(
            adView,
            adUnitId,
            adSize,
        ) {
            val request = BannerAdRequest.Builder(
                adUnitId,
                adSize,
            ).build()

            adView.loadAd(
                request,
                object : AdLoadCallback<BannerAd> {
                    override fun onAdLoaded(
                        ad: BannerAd,
                    ) = Unit

                    override fun onAdFailedToLoad(
                        adError: LoadAdError,
                    ) = Unit
                },
            )
        }

        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            AndroidView(
                modifier = Modifier.wrapContentHeight(),
                factory = {
                    adView
                },
            )
        }
    }
}
