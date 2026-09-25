package com.ahtohiofilho.dominopernambucano.advertising

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.ahtohiofilho.dominopernambucano.BuildConfig
import com.google.android.libraries.ads.mobile.sdk.MobileAds
import com.google.android.libraries.ads.mobile.sdk.common.AdLoadCallback
import com.google.android.libraries.ads.mobile.sdk.common.AdRequest
import com.google.android.libraries.ads.mobile.sdk.common.FullScreenContentError
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError
import com.google.android.libraries.ads.mobile.sdk.initialization.InitializationConfig
import com.google.android.libraries.ads.mobile.sdk.interstitial.InterstitialAd
import com.google.android.libraries.ads.mobile.sdk.interstitial.InterstitialAdEventCallback
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AndroidAdvertisingController(
    context: Context,
) {
    private val applicationContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val consentInformation =
        UserMessagingPlatform.getConsentInformation(applicationContext)
    private val frequencyPreferences =
        applicationContext.getSharedPreferences(
            FrequencyPreferencesName,
            Context.MODE_PRIVATE,
        )
    private val frequencyGate = AdvertisingFrequencyGate(
        readCompletedMatchCount = {
            frequencyPreferences.getInt(
                CompletedMatchesKey,
                0,
            )
        },
        writeCompletedMatchCount = { value ->
            frequencyPreferences.edit()
                .putInt(
                    CompletedMatchesKey,
                    value,
                )
                .apply()
        },
    )
    private val interstitialCooldownGate =
        AdvertisingInterstitialCooldownGate(
            readLastShownEpochMillis = {
                frequencyPreferences.getLong(
                    LastInterstitialShownEpochMillisKey,
                    0L,
                )
            },
            writeLastShownEpochMillis = { value ->
                frequencyPreferences.edit()
                    .putLong(
                        LastInterstitialShownEpochMillisKey,
                        value,
                    )
                    .apply()
            },
            nowEpochMillis = {
                System.currentTimeMillis()
            },
        )

    private val _privacyOptionsRequired =
        MutableStateFlow(false)

    val privacyOptionsRequired: StateFlow<Boolean> =
        _privacyOptionsRequired.asStateFlow()

    private val _adsReady =
        MutableStateFlow(false)

    val adsReady: StateFlow<Boolean> =
        _adsReady.asStateFlow()

    private var initializationStarted = false
    private var initializationCompleted = false
    private var interstitialLoadInProgress = false
    private var interstitialAd: InterstitialAd? = null
    private var pendingMatchOpportunity = false
    private var transitionInFlight = false

    fun start(
        activity: Activity,
    ) {
        runOnMainThread {
            val requestParameters =
                ConsentRequestParameters.Builder()
                    .build()

            consentInformation.requestConsentInfoUpdate(
                activity,
                requestParameters,
                {
                    refreshPrivacyOptionsRequirement()

                    UserMessagingPlatform
                        .loadAndShowConsentFormIfRequired(
                            activity,
                        ) { formError ->
                            if (formError != null) {
                                Log.w(
                                    LogTag,
                                    "Consent form unavailable: " +
                                        formError.message,
                                )
                            }

                            refreshPrivacyOptionsRequirement()
                            maybeInitializeAds()
                        }

                    maybeInitializeAds()
                },
                { requestError ->
                    Log.w(
                        LogTag,
                        "Consent information update failed: " +
                            requestError.message,
                    )

                    refreshPrivacyOptionsRequirement()
                    maybeInitializeAds()
                },
            )

            maybeInitializeAds()
        }
    }

    fun recordMatchFinished() {
        runOnMainThread {
            transitionInFlight = false
            pendingMatchOpportunity =
                frequencyGate.recordCompletedMatch()
        }
    }

    fun runAfterMatchFinishedTransition(
        activity: Activity,
        continuation: () -> Unit,
    ) {
        runOnMainThread {
            if (transitionInFlight) {
                return@runOnMainThread
            }

            val shouldAttemptInterstitial =
                pendingMatchOpportunity

            pendingMatchOpportunity = false

            runAfterInterstitialTransitionOnMainThread(
                activity = activity,
                shouldAttemptInterstitial =
                    shouldAttemptInterstitial,
                continuation = continuation,
            )
        }
    }

    fun runAfterOfflineMatchExitTransition(
        activity: Activity,
        continuation: () -> Unit,
    ) {
        runOnMainThread {
            if (transitionInFlight) {
                return@runOnMainThread
            }

            runAfterInterstitialTransitionOnMainThread(
                activity = activity,
                shouldAttemptInterstitial = true,
                continuation = continuation,
            )
        }
    }

    private fun runAfterInterstitialTransitionOnMainThread(
        activity: Activity,
        shouldAttemptInterstitial: Boolean,
        continuation: () -> Unit,
    ) {
        transitionInFlight = true

        if (
            !shouldAttemptInterstitial ||
            !interstitialCooldownGate.canShowInterstitial()
        ) {
            completeTransition(
                continuation = continuation,
            )
            return
        }

        if (!consentInformation.canRequestAds()) {
            completeTransition(
                continuation = continuation,
            )
            return
        }

        val ad = interstitialAd

        if (ad == null) {
            ensureInterstitialLoaded()
            completeTransition(
                continuation = continuation,
            )
            return
        }

        interstitialAd = null

        val continuationConsumed =
            AtomicBoolean(false)

        fun continueOnce() {
            if (
                continuationConsumed.compareAndSet(
                    false,
                    true,
                )
            ) {
                completeTransition(
                    continuation = continuation,
                )
            }
        }

        ad.adEventCallback =
            object : InterstitialAdEventCallback {
                override fun onAdShowedFullScreenContent() {
                    runOnMainThread {
                        interstitialCooldownGate
                            .recordInterstitialShown()
                    }
                }

                override fun onAdDismissedFullScreenContent() {
                    runOnMainThread {
                        continueOnce()
                        ensureInterstitialLoaded()
                    }
                }

                override fun onAdFailedToShowFullScreenContent(
                    fullScreenContentError: FullScreenContentError,
                ) {
                    Log.w(
                        LogTag,
                        "Interstitial failed to show: " +
                            fullScreenContentError.message,
                    )

                    runOnMainThread {
                        continueOnce()
                        ensureInterstitialLoaded()
                    }
                }
            }

        try {
            ad.show(activity)
        } catch (error: RuntimeException) {
            Log.w(
                LogTag,
                "Interstitial show threw an exception.",
                error,
            )

            continueOnce()
            ensureInterstitialLoaded()
        }
    }

    fun showPrivacyOptions(
        activity: Activity,
    ) {
        runOnMainThread {
            UserMessagingPlatform.showPrivacyOptionsForm(
                activity,
            ) { formError ->
                if (formError != null) {
                    Log.w(
                        LogTag,
                        "Privacy options form unavailable: " +
                            formError.message,
                    )
                }

                refreshPrivacyOptionsRequirement()
                maybeInitializeAds()
            }
        }
    }

    private fun refreshPrivacyOptionsRequirement() {
        _privacyOptionsRequired.value =
            consentInformation.privacyOptionsRequirementStatus ==
                ConsentInformation
                    .PrivacyOptionsRequirementStatus
                    .REQUIRED
    }

    private fun maybeInitializeAds() {
        if (!consentInformation.canRequestAds()) {
            _adsReady.value = false
            return
        }

        if (
            initializationStarted ||
            initializationCompleted
        ) {
            if (initializationCompleted) {
                ensureInterstitialLoaded()
            }
            return
        }

        initializationStarted = true

        Thread(
            {
                try {
                    MobileAds.initialize(
                        applicationContext,
                        InitializationConfig.Builder(
                            BuildConfig.ADMOB_APP_ID,
                        ).build(),
                        null,
                    )

                    runOnMainThread {
                        initializationCompleted = true
                        _adsReady.value = true
                        ensureInterstitialLoaded()
                    }
                } catch (error: RuntimeException) {
                    Log.w(
                        LogTag,
                        "GMA Next-Gen initialization failed.",
                        error,
                    )

                    runOnMainThread {
                        initializationStarted = false
                        initializationCompleted = false
                        _adsReady.value = false
                    }
                }
            },
            "domino-ads-init",
        ).start()
    }

    private fun ensureInterstitialLoaded() {
        if (
            !initializationCompleted ||
            !consentInformation.canRequestAds() ||
            interstitialLoadInProgress ||
            interstitialAd != null
        ) {
            return
        }

        interstitialLoadInProgress = true

        InterstitialAd.load(
            AdRequest.Builder(
                BuildConfig.ADMOB_INTERSTITIAL_UNIT_ID,
            ).build(),
            object : AdLoadCallback<InterstitialAd> {
                override fun onAdLoaded(
                    ad: InterstitialAd,
                ) {
                    runOnMainThread {
                        interstitialLoadInProgress = false
                        interstitialAd = ad
                    }
                }

                override fun onAdFailedToLoad(
                    adError: LoadAdError,
                ) {
                    Log.w(
                        LogTag,
                        "Interstitial failed to load: " +
                            adError.message,
                    )

                    runOnMainThread {
                        interstitialLoadInProgress = false
                        interstitialAd = null
                    }
                }
            },
        )
    }

    private fun completeTransition(
        continuation: () -> Unit,
    ) {
        try {
            continuation()
        } finally {
            transitionInFlight = false
        }
    }

    private fun runOnMainThread(
        action: () -> Unit,
    ) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            action()
        } else {
            mainHandler.post(action)
        }
    }

    private companion object {
        const val LogTag = "DominoAdvertising"
        const val FrequencyPreferencesName =
            "domino_advertising_frequency"
        const val CompletedMatchesKey =
            "completed_matches"
        const val LastInterstitialShownEpochMillisKey =
            "last_interstitial_shown_epoch_millis"
    }
}
