package com.ahtohiofilho.dominopernambucano.online

import com.ahtohiofilho.dominopernambucano.online.observability.OnlineTraceLogger

object OnlineRepositoryFactory {
    fun create(
        config: OnlineBackendConfig = OnlineBackendConfig.Fake,
        traceLogger: OnlineTraceLogger = OnlineTraceLogger(),
        anonymousSessionRepository: OnlineAnonymousSessionRepository? = null,
        sessionCredentialRepository:
            OnlineSessionCredentialRepository? = null,
        onlineParticipationBindingRepository:
            OnlineParticipationBindingRepository? = null,
    ): OnlineRoomRepository {
        return when (config.mode) {
            OnlineBackendMode.FAKE -> {
                FakeOnlineRoomRepository()
            }

            OnlineBackendMode.REMOTE -> {
                RemoteOnlineRoomRepository(
                    config = config,
                    pollingPolicy = OnlineRemotePollingPolicy.Default,
                    traceLogger = traceLogger,
                    anonymousSessionRepository = anonymousSessionRepository,
                    sessionCredentialRepository =
                        sessionCredentialRepository,
                    onlineParticipationBindingRepository =
                        onlineParticipationBindingRepository,
                )
            }
        }
    }
}
