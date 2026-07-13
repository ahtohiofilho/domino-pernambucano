package com.ahtohiofilho.dominopernambucano.online

import kotlinx.serialization.Serializable

@Serializable
enum class OnlineParticipantTypeDto {
    HUMAN,
    APPLICATION,
}