package com.ahtohiofilho.dominopernambucano.online

/*
 * Cabeçalho transitório de homologação.
 *
 * O servidor resolve esse valor por uma porta própria, permitindo substituir o
 * transporte por um principal validado por JWT sem reabrir o contrato de salas,
 * ações ou snapshots.
 */
object OnlineRemoteHeaders {
    const val DEVELOPMENT_PLAYER_ID = "X-Domino-Development-Player-Id"
    const val SYNTHETIC_PROVISIONING_SECRET =
        "X-Domino-Synthetic-Provisioning-Secret"
    const val CLIENT_VERSION_CODE =
        "X-Domino-Client-Version-Code"
}
