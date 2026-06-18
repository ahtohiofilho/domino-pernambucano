package com.ahtohiofilho.dominopernambucano.online

object OnlineRepositoryFactory {
    fun create(): OnlineRoomRepository {
        return FakeOnlineRoomRepository()
    }
}