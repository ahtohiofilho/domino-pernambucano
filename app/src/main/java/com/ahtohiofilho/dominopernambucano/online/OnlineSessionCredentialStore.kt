package com.ahtohiofilho.dominopernambucano.online

interface OnlineSessionCredentialStore {
    fun read(): OnlineSessionCredential?

    /**
     * Persiste todos os campos da credencial em uma única transação síncrona.
     * Retorna false quando o armazenamento não confirma a gravação.
     */
    fun write(
        credential: OnlineSessionCredential,
    ): Boolean

    fun clear(): Boolean
}
