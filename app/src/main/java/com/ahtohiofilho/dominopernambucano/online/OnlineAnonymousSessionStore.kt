package com.ahtohiofilho.dominopernambucano.online

interface OnlineAnonymousSessionStore {
    fun read(): OnlineAnonymousSessionDto?

    fun write(
        session: OnlineAnonymousSessionDto,
    )

    fun clear()
}