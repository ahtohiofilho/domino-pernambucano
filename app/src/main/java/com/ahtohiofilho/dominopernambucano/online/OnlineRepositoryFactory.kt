package com.ahtohiofilho.dominopernambucano.online

object OnlineRepositoryFactory {
    fun create(
        config: OnlineBackendConfig = OnlineBackendConfig.Fake,
    ): OnlineRoomRepository {
        return when (config.mode) {
            OnlineBackendMode.FAKE -> {
                FakeOnlineRoomRepository()
            }

            OnlineBackendMode.REMOTE -> {
                RemoteOnlineRoomRepository(
                    config = config,
                )
            }
        }
    }
}