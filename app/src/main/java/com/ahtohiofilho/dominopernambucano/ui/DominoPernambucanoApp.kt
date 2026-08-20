package com.ahtohiofilho.dominopernambucano.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.ahtohiofilho.dominopernambucano.BuildConfig
import com.ahtohiofilho.dominopernambucano.R
import com.ahtohiofilho.dominopernambucano.online.AndroidGoogleIdTokenProvider
import com.ahtohiofilho.dominopernambucano.online.GoogleSignInConfig
import com.ahtohiofilho.dominopernambucano.online.GoogleSignInEnvironment
import com.ahtohiofilho.dominopernambucano.online.KtorRemoteOnlineApiClient
import com.ahtohiofilho.dominopernambucano.online.OnlineAppConfig
import com.ahtohiofilho.dominopernambucano.online.OnlineAccountProfileRemoteClient
import com.ahtohiofilho.dominopernambucano.online.OnlineAppEnvironment
import com.ahtohiofilho.dominopernambucano.online.OnlineBackendMode
import com.ahtohiofilho.dominopernambucano.online.OnlineDominoMatchCoordinator
import com.ahtohiofilho.dominopernambucano.online.OnlineEmailAccountActionResult
import com.ahtohiofilho.dominopernambucano.online.OnlineEmailAccountFailureReason
import com.ahtohiofilho.dominopernambucano.online.OnlineEmailAccountIntent
import com.ahtohiofilho.dominopernambucano.online.OnlineEmailAccountManager
import com.ahtohiofilho.dominopernambucano.online.OnlineEmailIdentityRepository
import com.ahtohiofilho.dominopernambucano.online.OnlineGoogleAccountActionResult
import com.ahtohiofilho.dominopernambucano.online.OnlineGoogleAccountManager
import com.ahtohiofilho.dominopernambucano.online.OnlineGoogleAccountStatus
import com.ahtohiofilho.dominopernambucano.online.OnlineGoogleIdentityRepository
import com.ahtohiofilho.dominopernambucano.online.OnlineParticipationBindingRepository
import com.ahtohiofilho.dominopernambucano.online.OnlinePublicRankingRemoteClient
import com.ahtohiofilho.dominopernambucano.online.OnlineRankedQueueRemoteClient
import com.ahtohiofilho.dominopernambucano.online.OnlinePendingParticipationLocalResolution
import com.ahtohiofilho.dominopernambucano.online.OnlinePendingParticipationMatchResumeActivation
import com.ahtohiofilho.dominopernambucano.online.OnlinePendingParticipationMatchResumePreparation
import com.ahtohiofilho.dominopernambucano.online.OnlinePendingParticipationRemoteInspection
import com.ahtohiofilho.dominopernambucano.online.OnlineRepositoryFactory
import com.ahtohiofilho.dominopernambucano.online.OnlineSessionCredentialRepository
import com.ahtohiofilho.dominopernambucano.online.SharedPreferencesOnlineSessionCredentialStore
import com.ahtohiofilho.dominopernambucano.online.SharedPreferencesOnlineParticipationBindingStore
import com.ahtohiofilho.dominopernambucano.online.SharedPreferencesOnlinePlayerIdentityStore
import com.ahtohiofilho.dominopernambucano.online.observability.AndroidLogcatOnlineTraceSink
import com.ahtohiofilho.dominopernambucano.online.observability.CompositeOnlineTraceSink
import com.ahtohiofilho.dominopernambucano.online.observability.OnlineTraceBatchUploader
import com.ahtohiofilho.dominopernambucano.online.observability.OnlineTraceLogger
import com.ahtohiofilho.dominopernambucano.online.observability.PersistentOnlineTraceOutbox
import com.ahtohiofilho.dominopernambucano.session.DominoSessionCommand
import com.ahtohiofilho.dominopernambucano.session.DominoSessionState
import com.ahtohiofilho.dominopernambucano.session.LocalDominoSessionCoordinator
import com.ahtohiofilho.dominopernambucano.session.OnlinePendingParticipationInspectionState
import com.ahtohiofilho.dominopernambucano.session.OnlinePendingParticipationSessionRejection
import com.ahtohiofilho.dominopernambucano.ui.account.OnlineAccountProfileUiCoordinator
import com.ahtohiofilho.dominopernambucano.ui.account.OnlineAccountProfileStrings
import com.ahtohiofilho.dominopernambucano.ui.account.OnlineAccountProfileUiState
import com.ahtohiofilho.dominopernambucano.ui.game.DominoGameRoute
import com.ahtohiofilho.dominopernambucano.ui.menu.MainMenuScreen
import com.ahtohiofilho.dominopernambucano.ui.menu.MenuPlaceholderScreen
import com.ahtohiofilho.dominopernambucano.ui.menu.PlayModeScreen
import com.ahtohiofilho.dominopernambucano.ui.online.OnlineCreateRoomRoute
import com.ahtohiofilho.dominopernambucano.ui.online.OnlineJoinRoomRoute
import com.ahtohiofilho.dominopernambucano.ui.online.OnlineRankedQueueRoute
import com.ahtohiofilho.dominopernambucano.ui.ranking.OnlinePublicRankingRoute
import com.ahtohiofilho.dominopernambucano.ui.settings.AndroidAppLanguageManager
import com.ahtohiofilho.dominopernambucano.ui.settings.SettingsScreen
import java.io.File
import java.util.UUID
import kotlinx.coroutines.launch

@Composable
fun DominoPernambucanoApp(
    onlineAppConfig: OnlineAppConfig = OnlineAppEnvironment.Current,
    googleSignInConfig: GoogleSignInConfig =
        GoogleSignInEnvironment.Current,
) {
    val context = LocalContext.current
    val accountProfileStrings = OnlineAccountProfileStrings(
        nameRequired = stringResource(
            R.string.account_profile_name_required,
        ),
        profileSaved = stringResource(
            R.string.account_profile_saved,
        ),
        reviewData = stringResource(
            R.string.account_profile_review_data,
        ),
        sessionUnavailable = stringResource(
            R.string.account_session_unavailable,
        ),
        connectToEditProfile = stringResource(
            R.string.account_connect_to_edit_profile,
        ),
        profileMissing = stringResource(
            R.string.account_profile_missing,
        ),
        reviewNames = stringResource(
            R.string.account_profile_review_names,
        ),
        rateLimited = stringResource(
            R.string.account_profile_rate_limited,
        ),
        loadFailed = stringResource(
            R.string.account_profile_load_failed,
        ),
        invalidResponse = stringResource(
            R.string.account_profile_invalid_response,
        ),
        operationFailed = stringResource(
            R.string.account_profile_operation_failed,
        ),
        tableCodeRequired = stringResource(
            R.string.account_profile_table_code_required,
        ),
        tableCodeMigrationRequired = stringResource(
            R.string.account_profile_table_code_migration_required,
        ),
    )
    val resumeParticipationSessionRejected = stringResource(
        R.string.resume_participation_session_rejected,
    )
    val activeMatchNoLongerAvailable = stringResource(
        R.string.active_match_no_longer_available,
    )
    val activeMatchParticipationNotAuthorized = stringResource(
        R.string.active_match_participation_not_authorized,
    )
    val resumeParticipationDeviceFailed = stringResource(
        R.string.resume_participation_device_failed,
    )
    val resumeParticipationExpired = stringResource(
        R.string.resume_participation_expired,
    )
    val resumeParticipationRetry = stringResource(
        R.string.resume_participation_retry,
    )
    val reopenRoomDeviceFailed = stringResource(
        R.string.reopen_room_device_failed,
    )
    val reopenRoomRetry = stringResource(
        R.string.reopen_room_retry,
    )
    val resumeMatchDeviceFailed = stringResource(
        R.string.resume_match_device_failed,
    )
    val resumeMatchRetry = stringResource(
        R.string.resume_match_retry,
    )
    val accountConnectedSuccess = stringResource(
        R.string.account_connected_success,
    )
    val accountEmailCodeSent = stringResource(
        R.string.account_email_code_sent,
    )
    val accountEmailLinkedSuccess = stringResource(
        R.string.account_email_linked_success,
    )
    val accountEmailInvalidAddress = stringResource(
        R.string.account_email_invalid_address,
    )
    val accountEmailInvalidCode = stringResource(
        R.string.account_email_invalid_code,
    )
    val accountEmailAccountNotFound = stringResource(
        R.string.account_email_account_not_found,
    )
    val accountEmailIdentityConflict = stringResource(
        R.string.account_email_identity_conflict,
    )
    val accountEmailRateLimited = stringResource(
        R.string.account_email_rate_limited,
    )
    val accountEmailServiceUnavailable = stringResource(
        R.string.account_email_service_unavailable,
    )
    val accountEmailSessionConflict = stringResource(
        R.string.account_email_session_conflict,
    )
    val accountEmailSessionExpired = stringResource(
        R.string.account_email_session_expired,
    )
    val accountEmailLocalPersistence = stringResource(
        R.string.account_email_local_persistence,
    )
    val accountEmailUnknownFailure = stringResource(
        R.string.account_email_unknown_failure,
    )

    var settingsVisible by rememberSaveable {
        mutableStateOf(false)
    }

    var openAccountDialogOnNextMainMenu by rememberSaveable {
        mutableStateOf(false)
    }

    val onlineTraceClientSessionId = remember {
        "android-${UUID.randomUUID()}"
    }

    val onlineTraceOutbox = remember(
        context.applicationContext,
    ) {
        PersistentOnlineTraceOutbox(
            directory = File(
                context.applicationContext.filesDir,
                "online-trace-outbox.jsonl",
            ),
        )
    }

    val onlineTraceSink = remember(
        onlineTraceOutbox,
    ) {
        CompositeOnlineTraceSink(
            onlineTraceOutbox,
            AndroidLogcatOnlineTraceSink(),
        )
    }

    val onlineTraceLogger = remember(
        onlineTraceClientSessionId,
        onlineTraceSink,
    ) {
        OnlineTraceLogger(
            sink = onlineTraceSink,
            clientSessionId = onlineTraceClientSessionId,
        )
    }

    val onlineSessionCredentialRepository = remember(
        context.applicationContext,
    ) {
        OnlineSessionCredentialRepository(
            store = SharedPreferencesOnlineSessionCredentialStore(
                context = context.applicationContext,
            ),
        )
    }

    val googleIdentityApiClient = remember(
        onlineAppConfig.backendConfig,
    ) {
        if (
            onlineAppConfig.backendConfig.mode ==
            OnlineBackendMode.REMOTE
        ) {
            KtorRemoteOnlineApiClient(
                config = onlineAppConfig.backendConfig,
            )
        } else {
            null
        }
    }

    val onlineEmailAccountManager = remember(
        googleIdentityApiClient,
        onlineSessionCredentialRepository,
    ) {
        val emailIdentityRepository = googleIdentityApiClient?.let {
                apiClient ->
            OnlineEmailIdentityRepository(
                apiClient = apiClient,
                sessionCredentialRepository =
                    onlineSessionCredentialRepository,
            )
        }

        OnlineEmailAccountManager(
            available =
                BuildConfig.DEBUG &&
                    googleIdentityApiClient != null,
            apiClient = googleIdentityApiClient,
            emailIdentityRepository = emailIdentityRepository,
            sessionCredentialRepository =
                onlineSessionCredentialRepository,
        )
    }

    val onlineGoogleAccountManager = remember(
        context,
        googleSignInConfig,
        googleIdentityApiClient,
        onlineSessionCredentialRepository,
    ) {
        val googleIdentityRepository = googleIdentityApiClient?.let {
                apiClient ->
            OnlineGoogleIdentityRepository(
                apiClient = apiClient,
                sessionCredentialRepository =
                    onlineSessionCredentialRepository,
            )
        }

        val tokenProvider = if (
            googleSignInConfig.isConfigured &&
            googleIdentityRepository != null
        ) {
            AndroidGoogleIdTokenProvider(
                activityContext = context,
                config = googleSignInConfig,
            )
        } else {
            null
        }

        OnlineGoogleAccountManager(
            available =
                googleSignInConfig.isConfigured &&
                    googleIdentityRepository != null,
            googleIdTokenProvider = tokenProvider,
            googleIdentityRepository = googleIdentityRepository,
            sessionCredentialRepository =
                onlineSessionCredentialRepository,
        )
    }

    val onlineRankedQueueRemoteClient = remember(
        googleIdentityApiClient,
        onlineSessionCredentialRepository,
    ) {
        googleIdentityApiClient?.let { apiClient ->
            OnlineRankedQueueRemoteClient(
                remoteApiClient = apiClient,
                sessionCredentialRepository =
                    onlineSessionCredentialRepository,
            )
        }
    }

    val onlinePublicRankingRemoteClient = remember(
        googleIdentityApiClient,
        onlineSessionCredentialRepository,
    ) {
        googleIdentityApiClient?.let { apiClient ->
            OnlinePublicRankingRemoteClient(
                remoteApiClient = apiClient,
                sessionCredentialRepository =
                    onlineSessionCredentialRepository,
            )
        }
    }

    val onlineParticipationBindingRepository = remember(
        context.applicationContext,
    ) {
        OnlineParticipationBindingRepository(
            store = SharedPreferencesOnlineParticipationBindingStore(
                context = context.applicationContext,
            ),
        )
    }

    val onlineRoomRepository = remember(
        onlineAppConfig.backendConfig,
        onlineTraceLogger,
        onlineSessionCredentialRepository,
        onlineParticipationBindingRepository,
    ) {
        OnlineRepositoryFactory.create(
            config = onlineAppConfig.backendConfig,
            traceLogger = onlineTraceLogger,
            sessionCredentialRepository =
                onlineSessionCredentialRepository,
            onlineParticipationBindingRepository =
                onlineParticipationBindingRepository,
        )
    }

    val sessionCoordinator = remember(
        onlineParticipationBindingRepository,
        onlineSessionCredentialRepository,
        onlineRoomRepository,
    ) {
        LocalDominoSessionCoordinator(
            onlineParticipationBindingRepository =
                onlineParticipationBindingRepository,
            onlineSessionCredentialRepository =
                onlineSessionCredentialRepository,
            onlineRoomRepository = onlineRoomRepository,
        )
    }

    val onlineTraceBatchUploader = remember(
        onlineRoomRepository,
        onlineTraceOutbox,
    ) {
        OnlineTraceBatchUploader(
            repository = onlineRoomRepository,
            traceOutbox = onlineTraceOutbox,
        )
    }

    val onlineTracePendingEntryVersion by onlineTraceOutbox
        .pendingEntryVersion
        .collectAsState()

    val onlineMatchSnapshot by onlineRoomRepository.matchSnapshot.collectAsState()

    LaunchedEffect(
        onlineTracePendingEntryVersion,
        onlineMatchSnapshot?.roomId,
        onlineMatchSnapshot?.matchId,
    ) {
        onlineTraceOutbox.retryPendingPersistence()

        val snapshot = onlineMatchSnapshot ?: return@LaunchedEffect

        onlineTraceBatchUploader.flushPendingEntries(
            roomId = snapshot.roomId,
            matchId = snapshot.matchId,
        )
    }

    val onlinePlayerIdentityStore = remember(
        context.applicationContext,
    ) {
        SharedPreferencesOnlinePlayerIdentityStore(
            context = context.applicationContext,
        )
    }

    var onlinePlayerIdentity by remember(
        onlinePlayerIdentityStore,
    ) {
        mutableStateOf(
            onlinePlayerIdentityStore.getOrCreate(),
        )
    }

    val onlineAccountProfileUiCoordinator = remember(
        googleIdentityApiClient,
        onlineSessionCredentialRepository,
        onlinePlayerIdentityStore,
        accountProfileStrings,
    ) {
        googleIdentityApiClient?.let { apiClient ->
            OnlineAccountProfileUiCoordinator(
                client = OnlineAccountProfileRemoteClient(
                    remoteApiClient = apiClient,
                    sessionCredentialRepository =
                        onlineSessionCredentialRepository,
                ),
                identityStore = onlinePlayerIdentityStore,
                strings = accountProfileStrings,
            )
        }
    }

    val onlineDebugOptions = onlineAppConfig.debugOptions

    val sessionState by sessionCoordinator.state.collectAsState()

    val menuCoroutineScope = rememberCoroutineScope()

    var pendingOnlineMatchResumeInProgress by remember {
        mutableStateOf(false)
    }

    var pendingOnlineMatchResumeFeedbackMessage by remember {
        mutableStateOf<String?>(null)
    }

    var onlineGoogleAccountActionInProgress by remember {
        mutableStateOf(false)
    }

    var onlineGoogleAccountFeedbackMessage by remember {
        mutableStateOf<String?>(null)
    }

    var onlineEmailAddress by remember {
        mutableStateOf("")
    }

    var onlineEmailCode by remember {
        mutableStateOf("")
    }

    var onlineEmailIntent by remember {
        mutableStateOf<OnlineEmailAccountIntent?>(null)
    }

    var onlineEmailCodeRequested by remember {
        mutableStateOf(false)
    }

    var onlineEmailActionInProgress by remember {
        mutableStateOf(false)
    }

    var onlineEmailFeedbackMessage by remember {
        mutableStateOf<String?>(null)
    }

    var onlineAccountProfileUiState by remember {
        mutableStateOf<OnlineAccountProfileUiState>(
            OnlineAccountProfileUiState.NotAvailable,
        )
    }

    val onlineGoogleAccountStatus =
        if (onlineEmailAccountManager.isAvailable) {
            onlineEmailAccountManager.currentStatus()
        } else {
            onlineGoogleAccountManager.currentStatus()
        }

    val onlineAccountConnected =
        onlineGoogleAccountStatus ==
            OnlineGoogleAccountStatus.CONNECTED

    fun requestOnlineAccountProfile() {
        val coordinator = onlineAccountProfileUiCoordinator

        if (
            onlineGoogleAccountStatus !=
                OnlineGoogleAccountStatus.CONNECTED ||
            coordinator == null
        ) {
            onlineAccountProfileUiState =
                OnlineAccountProfileUiState.NotAvailable
            return
        }

        onlineAccountProfileUiState =
            OnlineAccountProfileUiState.Loading

        menuCoroutineScope.launch {
            val outcome = coordinator.load(
                fallbackIdentity = onlinePlayerIdentity,
            )

            onlineAccountProfileUiState = outcome.state

            outcome.synchronizedIdentity?.let {
                    synchronizedIdentity ->
                onlinePlayerIdentity = synchronizedIdentity
            }
        }
    }

    fun connectOnlineGoogleAccount(
        onConnected: () -> Unit = {},
    ) {
        if (onlineGoogleAccountActionInProgress) {
            return
        }

        onlineGoogleAccountActionInProgress = true
        onlineGoogleAccountFeedbackMessage = null

        menuCoroutineScope.launch {
            try {
                when (
                    val result =
                        onlineGoogleAccountManager.connect()
                ) {
                    is OnlineGoogleAccountActionResult.Success -> {
                        onlineGoogleAccountFeedbackMessage =
                            accountConnectedSuccess

                        val coordinator =
                            onlineAccountProfileUiCoordinator

                        if (coordinator != null) {
                            onlineAccountProfileUiState =
                                OnlineAccountProfileUiState.Loading

                            val outcome = coordinator.load(
                                fallbackIdentity =
                                    onlinePlayerIdentity,
                            )

                            onlineAccountProfileUiState =
                                outcome.state

                            outcome.synchronizedIdentity?.let {
                                    synchronizedIdentity ->
                                onlinePlayerIdentity =
                                    synchronizedIdentity
                            }
                        }

                        onConnected()
                    }

                    OnlineGoogleAccountActionResult.Cancelled -> {
                        onlineGoogleAccountFeedbackMessage = null
                    }

                    is OnlineGoogleAccountActionResult.Failure -> {
                        onlineGoogleAccountFeedbackMessage =
                            result.message
                    }
                }
            } finally {
                onlineGoogleAccountActionInProgress = false
            }
        }
    }

    fun onlineEmailFailureMessage(
        reason: OnlineEmailAccountFailureReason,
    ): String {
        return when (reason) {
            OnlineEmailAccountFailureReason.INVALID_EMAIL ->
                accountEmailInvalidAddress
            OnlineEmailAccountFailureReason.INVALID_CODE ->
                accountEmailInvalidCode
            OnlineEmailAccountFailureReason.ACCOUNT_NOT_FOUND ->
                accountEmailAccountNotFound
            OnlineEmailAccountFailureReason.IDENTITY_CONFLICT ->
                accountEmailIdentityConflict
            OnlineEmailAccountFailureReason.RATE_LIMITED ->
                accountEmailRateLimited
            OnlineEmailAccountFailureReason.SERVICE_UNAVAILABLE ->
                accountEmailServiceUnavailable
            OnlineEmailAccountFailureReason.SESSION_CONFLICT ->
                accountEmailSessionConflict
            OnlineEmailAccountFailureReason.SESSION_EXPIRED ->
                accountEmailSessionExpired
            OnlineEmailAccountFailureReason.LOCAL_PERSISTENCE ->
                accountEmailLocalPersistence
            OnlineEmailAccountFailureReason.UNKNOWN ->
                accountEmailUnknownFailure
        }
    }

    fun resetOnlineEmailFlow(
        clearAddress: Boolean,
    ) {
        if (clearAddress) {
            onlineEmailAddress = ""
        }
        onlineEmailCode = ""
        onlineEmailIntent = null
        onlineEmailCodeRequested = false
        onlineEmailFeedbackMessage = null
    }

    fun requestOnlineEmailCode(
        intent: OnlineEmailAccountIntent,
    ) {
        if (onlineEmailActionInProgress) {
            return
        }

        onlineEmailActionInProgress = true
        onlineEmailFeedbackMessage = null

        menuCoroutineScope.launch {
            try {
                when (
                    val result = onlineEmailAccountManager.requestCode(
                        rawEmail = onlineEmailAddress,
                        intent = intent,
                    )
                ) {
                    is OnlineEmailAccountActionResult.CodeRequested -> {
                        onlineEmailAddress = result.email
                        onlineEmailIntent = result.intent
                        onlineEmailCode = ""
                        onlineEmailCodeRequested = true
                        onlineEmailFeedbackMessage =
                            accountEmailCodeSent
                    }

                    is OnlineEmailAccountActionResult.Failure -> {
                        onlineEmailFeedbackMessage =
                            onlineEmailFailureMessage(result.reason)
                    }

                    is OnlineEmailAccountActionResult.Success -> Unit
                }
            } finally {
                onlineEmailActionInProgress = false
            }
        }
    }

    fun confirmOnlineEmailCode() {
        val intent = onlineEmailIntent ?: return
        if (onlineEmailActionInProgress) {
            return
        }

        onlineEmailActionInProgress = true
        onlineEmailFeedbackMessage = null

        menuCoroutineScope.launch {
            try {
                when (
                    val result = onlineEmailAccountManager.submitCode(
                        rawEmail = onlineEmailAddress,
                        rawCode = onlineEmailCode,
                        intent = intent,
                    )
                ) {
                    is OnlineEmailAccountActionResult.Success -> {
                        onlineEmailFeedbackMessage =
                            if (
                                result.intent ==
                                    OnlineEmailAccountIntent.LINK &&
                                onlineGoogleAccountStatus ==
                                    OnlineGoogleAccountStatus.CONNECTED
                            ) {
                                accountEmailLinkedSuccess
                            } else {
                                accountConnectedSuccess
                            }

                        onlineEmailCode = ""
                        onlineEmailIntent = null
                        onlineEmailCodeRequested = false

                        val coordinator =
                            onlineAccountProfileUiCoordinator

                        if (coordinator != null) {
                            onlineAccountProfileUiState =
                                OnlineAccountProfileUiState.Loading

                            val outcome = coordinator.load(
                                fallbackIdentity =
                                    onlinePlayerIdentity,
                            )

                            onlineAccountProfileUiState =
                                outcome.state

                            outcome.synchronizedIdentity?.let {
                                    synchronizedIdentity ->
                                onlinePlayerIdentity =
                                    synchronizedIdentity
                            }
                        }
                    }

                    is OnlineEmailAccountActionResult.Failure -> {
                        onlineEmailFeedbackMessage =
                            onlineEmailFailureMessage(result.reason)

                        if (result.requiresNewCode) {
                            onlineEmailCode = ""
                            onlineEmailIntent = null
                            onlineEmailCodeRequested = false
                        }
                    }

                    is OnlineEmailAccountActionResult.CodeRequested -> Unit
                }
            } finally {
                onlineEmailActionInProgress = false
            }
        }
    }

    if (settingsVisible) {
        SettingsScreen(
            currentSelection =
                AndroidAppLanguageManager.currentSelection(
                    context = context,
                ),
            onSelectionChange = { selection ->
                AndroidAppLanguageManager.applySelection(
                    context = context,
                    selection = selection,
                )
            },
            onBackClick = {
                settingsVisible = false
            },
        )

        return
    }

    when (val state = sessionState) {
        is DominoSessionState.MainMenu -> {
            MainMenuScreen(
                pendingOnlineParticipation =
                    state.pendingOnlineParticipation,
                pendingOnlineParticipationInspection =
                    state.pendingOnlineParticipationInspection,
                pendingOnlineParticipationSessionRejection =
                    state.pendingOnlineParticipationSessionRejection,
                pendingOnlineMatchResumeInProgress =
                    pendingOnlineMatchResumeInProgress,
                pendingOnlineMatchResumeFeedbackMessage =
                    pendingOnlineMatchResumeFeedbackMessage,
                onlineGoogleAccountStatus =
                    onlineGoogleAccountStatus,
                onlineGoogleAccountActionInProgress =
                    onlineGoogleAccountActionInProgress,
                onlineGoogleAccountFeedbackMessage =
                    onlineGoogleAccountFeedbackMessage,
                onlineGoogleAvailable =
                    googleSignInConfig.isConfigured &&
                        googleIdentityApiClient != null,
                onlineEmailAvailable =
                    onlineEmailAccountManager.isAvailable,
                onlineEmailAddress = onlineEmailAddress,
                onlineEmailCode = onlineEmailCode,
                onlineEmailIntent = onlineEmailIntent,
                onlineEmailCodeRequested =
                    onlineEmailCodeRequested,
                onlineEmailActionInProgress =
                    onlineEmailActionInProgress,
                onlineEmailFeedbackMessage =
                    onlineEmailFeedbackMessage,
                onlineAccountDisplayName =
                    onlinePlayerIdentity.displayName,
                onlineAccountTableName =
                    onlinePlayerIdentity.tableName,
                onlineAccountProfileUiState =
                    onlineAccountProfileUiState,
                openAccountDialogOnEnter =
                    openAccountDialogOnNextMainMenu,
                onAccountDialogOpenRequestConsumed = {
                    openAccountDialogOnNextMainMenu = false
                },
                onAccountDialogOpened = {
                    requestOnlineAccountProfile()
                },
                onAccountProfilePublicDisplayNameChange = {
                        publicDisplayName ->
                    val editor =
                        onlineAccountProfileUiState as?
                            OnlineAccountProfileUiState.Editing

                    if (editor != null) {
                        onlineAccountProfileUiState =
                            editor.withPublicDisplayName(
                                value = publicDisplayName,
                            )
                    }
                },
                onAccountProfileTableNameChange = { tableName ->
                    val editor =
                        onlineAccountProfileUiState as?
                            OnlineAccountProfileUiState.Editing

                    if (editor != null) {
                        onlineAccountProfileUiState =
                            editor.withTableName(
                                value = tableName,
                            )
                    }
                },
                onAccountProfileSaveClick = {
                    val editor =
                        onlineAccountProfileUiState as?
                            OnlineAccountProfileUiState.Editing
                    val coordinator =
                        onlineAccountProfileUiCoordinator

                    if (
                        editor != null &&
                        coordinator != null &&
                        editor.saveEnabled
                    ) {
                        onlineAccountProfileUiState =
                            editor.copy(
                                actionInProgress = true,
                                feedbackMessage = null,
                            )

                        menuCoroutineScope.launch {
                            val outcome =
                                coordinator.save(editor)

                            onlineAccountProfileUiState =
                                outcome.state

                            outcome.synchronizedIdentity?.let {
                                    synchronizedIdentity ->
                                onlinePlayerIdentity =
                                    synchronizedIdentity
                            }
                        }
                    }
                },
                onAccountProfileRetryClick = {
                    requestOnlineAccountProfile()
                },
                onEmailAddressChange = { value ->
                    onlineEmailAddress = value.take(254)
                    onlineEmailFeedbackMessage = null
                },
                onEmailCodeChange = { value ->
                    onlineEmailCode = value
                        .filter { character ->
                            character.isDigit()
                        }
                        .take(6)
                    onlineEmailFeedbackMessage = null
                },
                onEmailStartLinkClick = {
                    requestOnlineEmailCode(
                        OnlineEmailAccountIntent.LINK,
                    )
                },
                onEmailStartRecoverClick = {
                    requestOnlineEmailCode(
                        OnlineEmailAccountIntent.RECOVER,
                    )
                },
                onEmailConfirmCodeClick = {
                    confirmOnlineEmailCode()
                },
                onEmailResetClick = {
                    resetOnlineEmailFlow(
                        clearAddress = true,
                    )
                },
                onAccountDialogDismissed = {
                    resetOnlineEmailFlow(
                        clearAddress = true,
                    )
                },
                onPlayClick = {
                    if (
                        !pendingOnlineMatchResumeInProgress &&
                        state.pendingOnlineParticipationInspection
                                !is OnlinePendingParticipationInspectionState
                        .InProgress
                    ) {
                        sessionCoordinator.dispatch(
                            DominoSessionCommand.OpenPlayModeSelection,
                        )
                    }
                },
                onRankingClick = {
                    if (!pendingOnlineMatchResumeInProgress) {
                        sessionCoordinator.dispatch(
                            DominoSessionCommand.OpenPublicRanking,
                        )
                    }
                },
                onSettingsClick = {
                    settingsVisible = true
                },
                onInspectPendingOnlineParticipationClick = {
                    if (!pendingOnlineMatchResumeInProgress) {
                        menuCoroutineScope.launch {
                            sessionCoordinator
                                .inspectPendingOnlineParticipation()
                        }
                    }
                },
                onDiscardRejectedPendingOnlineParticipationClick = {
                    pendingOnlineMatchResumeFeedbackMessage = null

                    sessionCoordinator
                        .discardRemoteSessionRejectedPendingOnlineParticipation()
                },
                onResumePendingOnlineMatchClick = {
                    val pendingParticipation =
                        state.pendingOnlineParticipation

                    val completedInspection =
                        state.pendingOnlineParticipationInspection
                                as? OnlinePendingParticipationInspectionState
                        .Completed

                    val hasRecoverableInspection =
                        completedInspection?.result is
                                OnlinePendingParticipationRemoteInspection
                                .Recoverable

                    val hasRemoteSessionRejection =
                        state.pendingOnlineParticipationSessionRejection is
                                OnlinePendingParticipationSessionRejection
                                .RemoteSessionRejected

                    if (
                        !pendingOnlineMatchResumeInProgress &&
                        pendingParticipation is
                                OnlinePendingParticipationLocalResolution
                                .ReadyForRemoteReconciliation &&
                        hasRecoverableInspection &&
                        !hasRemoteSessionRejection
                    ) {
                        pendingOnlineMatchResumeInProgress = true
                        pendingOnlineMatchResumeFeedbackMessage = null

                        menuCoroutineScope.launch {
                            var createdMatchCoordinator:
                                    OnlineDominoMatchCoordinator? = null

                            try {
                                when (
                                    val preparation =
                                        onlineRoomRepository
                                            .preparePendingParticipationMatchResume(
                                                binding =
                                                    pendingParticipation.binding,
                                            )
                                ) {
                                    OnlinePendingParticipationMatchResumePreparation
                                        .RemoteSessionRejected -> {
                                        sessionCoordinator
                                            .recordPendingOnlineParticipationRemoteSessionRejected(
                                                binding =
                                                    pendingParticipation.binding,
                                            )

                                        pendingOnlineMatchResumeFeedbackMessage =
                                            resumeParticipationSessionRejected
                                    }

                                    is OnlinePendingParticipationMatchResumePreparation
                                        .NotAttempted -> {
                                        sessionCoordinator
                                            .invalidatePendingOnlineParticipationRemoteConfirmation(
                                                binding =
                                                    pendingParticipation.binding,
                                            )

                                        pendingOnlineMatchResumeFeedbackMessage =
                                            resumeParticipationDeviceFailed
                                    }

                                    is OnlinePendingParticipationMatchResumePreparation
                                        .NoLongerRecoverable -> {
                                        sessionCoordinator
                                            .discardNoLongerRecoverablePendingOnlineParticipation(
                                                binding =
                                                    pendingParticipation.binding,
                                            )

                                        pendingOnlineMatchResumeFeedbackMessage =
                                            resumeParticipationExpired
                                    }

                                    is OnlinePendingParticipationMatchResumePreparation
                                        .TemporarilyUnavailable -> {
                                        pendingOnlineMatchResumeFeedbackMessage =
                                            resumeParticipationRetry
                                    }

                                    is OnlinePendingParticipationMatchResumePreparation
                                        .WaitingForPlayers -> {
                                        when (
                                            val activation =
                                                onlineRoomRepository
                                                    .activatePendingParticipationRoomResume(
                                                        preparation = preparation,
                                                    )
                                        ) {
                                            OnlinePendingParticipationMatchResumeActivation
                                                .Activated -> {
                                                sessionCoordinator.dispatch(
                                                    DominoSessionCommand
                                                        .OpenResumedOnlineRoom(
                                                            binding =
                                                                preparation.binding,
                                                        ),
                                                )
                                            }

                                            is OnlinePendingParticipationMatchResumeActivation
                                                .NotAttempted -> {
                                                sessionCoordinator
                                                    .invalidatePendingOnlineParticipationRemoteConfirmation(
                                                        binding =
                                                            preparation.binding,
                                                    )

                                                pendingOnlineMatchResumeFeedbackMessage =
                                                    reopenRoomDeviceFailed
                                            }

                                            is OnlinePendingParticipationMatchResumeActivation
                                                .TemporarilyUnavailable -> {
                                                pendingOnlineMatchResumeFeedbackMessage =
                                                    reopenRoomRetry
                                            }
                                        }
                                    }

                                    is OnlinePendingParticipationMatchResumePreparation
                                        .Ready -> {
                                        val matchCoordinator =
                                            OnlineDominoMatchCoordinator(
                                                repository = onlineRoomRepository,
                                                roomId =
                                                    preparation.roomSnapshot.roomId,
                                                matchId =
                                                    preparation.matchSnapshot.matchId,
                                                localPlayerId =
                                                    preparation.binding.playerId,
                                                localPlayerIndex =
                                                    preparation.binding.localSeatIndex,
                                                initialSnapshot =
                                                    preparation.matchSnapshot,
                                                traceLogger = onlineTraceLogger,
                                            )

                                        createdMatchCoordinator = matchCoordinator

                                        when (
                                            val activation =
                                                onlineRoomRepository
                                                    .activatePendingParticipationMatchResume(
                                                        preparation = preparation,
                                                    )
                                        ) {
                                            OnlinePendingParticipationMatchResumeActivation
                                                .Activated -> {
                                                sessionCoordinator.dispatch(
                                                    DominoSessionCommand.StartOnlineMatch(
                                                        matchCoordinator = matchCoordinator,
                                                    ),
                                                )

                                                createdMatchCoordinator = null
                                            }

                                            is OnlinePendingParticipationMatchResumeActivation
                                                .NotAttempted -> {
                                                sessionCoordinator
                                                    .invalidatePendingOnlineParticipationRemoteConfirmation(
                                                        binding =
                                                            preparation.binding,
                                                    )

                                                pendingOnlineMatchResumeFeedbackMessage =
                                                    resumeMatchDeviceFailed
                                            }

                                            is OnlinePendingParticipationMatchResumeActivation
                                                .TemporarilyUnavailable -> {
                                                pendingOnlineMatchResumeFeedbackMessage =
                                                    resumeMatchRetry
                                            }
                                        }
                                    }
                                }
                            } finally {
                                createdMatchCoordinator?.dispose()

                                pendingOnlineMatchResumeInProgress = false
                            }
                        }
                    }
                },
                onConnectGoogleAccountClick = {
                    connectOnlineGoogleAccount()
                },
            )
        }

        DominoSessionState.PublicRanking -> {
            val rankingClient = onlinePublicRankingRemoteClient

            if (rankingClient == null) {
                MenuPlaceholderScreen(
                    title = stringResource(
                        R.string.ranking_title,
                    ),
                    description = stringResource(
                        R.string.ranking_unavailable_environment,
                    ),
                    onBackClick = {
                        sessionCoordinator.dispatch(
                            DominoSessionCommand.BackToMainMenu,
                        )
                    },
                )
            } else {
                OnlinePublicRankingRoute(
                    rankingClient = rankingClient,
                    onBackClick = {
                        sessionCoordinator.dispatch(
                            DominoSessionCommand.BackToMainMenu,
                        )
                    },
                )
            }
        }

        DominoSessionState.PlayModeSelection -> {
            PlayModeScreen(
                onBackClick = {
                    sessionCoordinator.dispatch(
                        DominoSessionCommand.BackToMainMenu,
                    )
                },
                onlineActionInProgress =
                    onlineGoogleAccountActionInProgress,
                onlineFeedbackMessage =
                    onlineGoogleAccountFeedbackMessage,
                onRankedGameClick = {
                    if (onlineAccountConnected) {
                        sessionCoordinator.dispatch(
                            DominoSessionCommand
                                .OpenOnlineRankedQueue,
                        )
                    } else {
                        connectOnlineGoogleAccount(
                            onConnected = {
                                sessionCoordinator.dispatch(
                                    DominoSessionCommand
                                        .OpenOnlineRankedQueue,
                                )
                            },
                        )
                    }
                },
                onLocalGameClick = {
                    sessionCoordinator.dispatch(
                        DominoSessionCommand.StartLocalMatch,
                    )
                },
                onCreateOnlineRoomClick = {
                    sessionCoordinator.dispatch(
                        DominoSessionCommand.OpenOnlineCreateRoom,
                    )
                },
                onJoinOnlineRoomClick = {
                    sessionCoordinator.dispatch(
                        DominoSessionCommand.OpenOnlineJoinRoom,
                    )
                },
            )
        }

        is DominoSessionState.LocalMatch -> {
            DominoGameRoute(
                matchCoordinator = state.matchCoordinator,
                onBackToMenuClick = {
                    sessionCoordinator.dispatch(
                        DominoSessionCommand.BackToPlayModeSelection,
                    )
                },
            )
        }

        DominoSessionState.OnlineRankedQueue -> {
            val queueClient = onlineRankedQueueRemoteClient

            if (queueClient == null) {
                MenuPlaceholderScreen(
                        title = stringResource(
                            R.string.ranked_match_title,
                        ),
                        description = stringResource(
                            R.string.ranked_queue_unavailable,
                        ),
                        onBackClick = {
                            sessionCoordinator.dispatch(
                                DominoSessionCommand
                                    .BackToPlayModeSelection,
                            )
                        },
                    )
            } else {
                OnlineRankedQueueRoute(
                    queueClient = queueClient,
                    roomRepository = onlineRoomRepository,
                    playerName = onlinePlayerIdentity.tableName,
                    onStartOnlineMatch = { activation ->
                        val matchCoordinator =
                            OnlineDominoMatchCoordinator(
                                repository = onlineRoomRepository,
                                roomId = activation.roomId,
                                matchId = activation.matchId,
                                localPlayerId = activation.playerId,
                                localPlayerIndex =
                                    activation.localSeatIndex,
                                initialSnapshot =
                                    activation.initialSnapshot,
                                traceLogger = onlineTraceLogger,
                                onMatchFinished = {
                                    onlinePublicRankingRemoteClient
                                        ?.invalidateCurrentRankingCache()
                                },
                            )

                        sessionCoordinator.dispatch(
                            DominoSessionCommand.StartOnlineMatch(
                                matchCoordinator = matchCoordinator,
                            ),
                        )
                    },
                    onAccountAccessClick = {
                        openAccountDialogOnNextMainMenu = true
                        sessionCoordinator.dispatch(
                            DominoSessionCommand.BackToMainMenu,
                        )
                    },
                    onBackClick = {
                        sessionCoordinator.dispatch(
                            DominoSessionCommand
                                .BackToPlayModeSelection,
                        )
                    },
                )
            }
        }

        DominoSessionState.OnlineCreateRoom -> {
            OnlineCreateRoomRoute(
                roomRepository = onlineRoomRepository,
                localPlayerIdentity = onlinePlayerIdentity,
                debugOptions = onlineDebugOptions,
                traceLogger = onlineTraceLogger,
                onStartOnlineMatch = { matchCoordinator ->
                    sessionCoordinator.dispatch(
                        DominoSessionCommand.StartOnlineMatch(
                            matchCoordinator = matchCoordinator,
                        )
                    )
                },
                onBackClick = {
                    sessionCoordinator.dispatch(
                        DominoSessionCommand.BackToPlayModeSelection,
                    )
                },
            )
        }

        is DominoSessionState.OnlineResumedRoom -> {
            OnlineCreateRoomRoute(
                roomRepository = onlineRoomRepository,
                localPlayerIdentity = onlinePlayerIdentity,
                resumedParticipationBinding = state.binding,
                debugOptions = onlineDebugOptions,
                traceLogger = onlineTraceLogger,
                onStartOnlineMatch = { matchCoordinator ->
                    sessionCoordinator.dispatch(
                        DominoSessionCommand.StartOnlineMatch(
                            matchCoordinator = matchCoordinator,
                        )
                    )
                },
                onBackClick = {
                    sessionCoordinator.dispatch(
                        DominoSessionCommand.BackToMainMenu,
                    )
                },
            )
        }

        DominoSessionState.OnlineJoinRoom -> {
            OnlineJoinRoomRoute(
                roomRepository = onlineRoomRepository,
                localPlayerIdentity = onlinePlayerIdentity,
                debugOptions = onlineDebugOptions,
                traceLogger = onlineTraceLogger,
                onStartOnlineMatch = { matchCoordinator ->
                    sessionCoordinator.dispatch(
                        DominoSessionCommand.StartOnlineMatch(
                            matchCoordinator = matchCoordinator,
                        )
                    )
                },
                onBackClick = {
                    sessionCoordinator.dispatch(
                        DominoSessionCommand.BackToPlayModeSelection,
                    )
                },
            )
        }

        is DominoSessionState.OnlineMatch -> {
            val activeSessionInvalidation by
                state.matchCoordinator
                    .activeSessionInvalidation
                    .collectAsState()

            val activeResourceLoss by
                state.matchCoordinator
                    .activeResourceLoss
                    .collectAsState()

            val activeParticipationAuthorizationLoss by
                state.matchCoordinator
                    .activeParticipationAuthorizationLoss
                    .collectAsState()

            LaunchedEffect(
                state.matchCoordinator,
                activeSessionInvalidation,
            ) {
                val invalidatedBinding =
                    activeSessionInvalidation
                        ?: return@LaunchedEffect

                val rejectionRecorded =
                    sessionCoordinator
                        .returnActiveOnlineMatchToMainMenuAfterRemoteSessionRejected(
                            binding = invalidatedBinding,
                        )

                pendingOnlineMatchResumeFeedbackMessage =
                    if (rejectionRecorded) {
                        resumeParticipationSessionRejected
                    } else {
                        null
                    }
            }

            LaunchedEffect(
                state.matchCoordinator,
                activeResourceLoss,
            ) {
                val resourceLoss =
                    activeResourceLoss
                        ?: return@LaunchedEffect

                sessionCoordinator
                    .returnActiveOnlineMatchToMainMenuAfterRemoteResourceLoss(
                        binding = resourceLoss.binding,
                    )

                pendingOnlineMatchResumeFeedbackMessage =
                    activeMatchNoLongerAvailable
            }

            LaunchedEffect(
                state.matchCoordinator,
                activeParticipationAuthorizationLoss,
            ) {
                val authorizationLoss =
                    activeParticipationAuthorizationLoss
                        ?: return@LaunchedEffect

                sessionCoordinator
                    .returnActiveOnlineMatchToMainMenuAfterRemoteParticipationAuthorizationLoss(
                        binding = authorizationLoss.binding,
                    )

                pendingOnlineMatchResumeFeedbackMessage =
                    activeMatchParticipationNotAuthorized
            }

            DominoGameRoute(
                matchCoordinator = state.matchCoordinator,
                onBackToMenuClick = {
                    sessionCoordinator.dispatch(
                        DominoSessionCommand.BackToPlayModeSelection,
                    )
                },
                onlineUiTraceReporter = state.matchCoordinator,
            )
        }
    }
}
