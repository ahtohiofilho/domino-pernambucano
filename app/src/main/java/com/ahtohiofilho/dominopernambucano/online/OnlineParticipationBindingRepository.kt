package com.ahtohiofilho.dominopernambucano.online

class OnlineParticipationBindingRepository(
    private val store: OnlineParticipationBindingStore,
) {
    fun getValidBindingOrNull(): OnlineParticipationBinding? {
        val storedBinding = store.read() ?: return null

        if (!storedBinding.isValidForPersistence()) {
            store.clear()
            return null
        }

        return storedBinding
    }

    fun save(
        binding: OnlineParticipationBinding,
    ) {
        require(binding.roomId.isNotBlank()) {
            "O vínculo de participação precisa ter roomId."
        }

        require(binding.playerId.isNotBlank()) {
            "O vínculo de participação precisa ter playerId."
        }

        require(
            binding.matchId == null ||
                    binding.matchId.isNotBlank(),
        ) {
            "O vínculo de participação não pode ter matchId vazio."
        }

        require(binding.localSeatIndex in 0..3) {
            "O vínculo de participação precisa ter assento entre 0 e 3."
        }

        store.write(
            binding = binding,
        )
    }

    fun clear() {
        store.clear()
    }
}

private fun OnlineParticipationBinding.isValidForPersistence(): Boolean {
    return roomId.isNotBlank() &&
            playerId.isNotBlank() &&
            (matchId == null || matchId.isNotBlank()) &&
            localSeatIndex in 0..3
}