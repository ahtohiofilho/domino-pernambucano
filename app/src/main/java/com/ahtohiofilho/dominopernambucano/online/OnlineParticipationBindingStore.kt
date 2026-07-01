package com.ahtohiofilho.dominopernambucano.online

interface OnlineParticipationBindingStore {
    fun read(): OnlineParticipationBinding?

    fun write(
        binding: OnlineParticipationBinding,
    )

    fun clear()
}