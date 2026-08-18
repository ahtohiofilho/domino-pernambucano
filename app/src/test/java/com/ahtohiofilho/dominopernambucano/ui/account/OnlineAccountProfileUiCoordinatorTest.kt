package com.ahtohiofilho.dominopernambucano.ui.account

import com.ahtohiofilho.dominopernambucano.online.OnlineAccountProfileClient
import com.ahtohiofilho.dominopernambucano.online.OnlineAccountProfileClientResult
import com.ahtohiofilho.dominopernambucano.online.OnlineAccountProfileFailureKind
import com.ahtohiofilho.dominopernambucano.online.OnlineAccountProfileResponseDto
import com.ahtohiofilho.dominopernambucano.online.OnlinePlayerIdentity
import com.ahtohiofilho.dominopernambucano.online.OnlinePlayerIdentityStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private fun profileSuccess(
    publicDisplayName: String,
    tableName: String,
): OnlineAccountProfileClientResult.Success {
    return OnlineAccountProfileClientResult.Success(
        profile = OnlineAccountProfileResponseDto(
            publicDisplayName = publicDisplayName,
            tableName = tableName,
            updatedAtEpochMillis = 2_000L,
        ),
    )
}

class OnlineAccountProfileUiCoordinatorTest {
    private val profileStrings = OnlineAccountProfileStrings(
        nameRequired = "Complete seu nome público e escolha um nome curto.",
        profileSaved = "Perfil salvo. O ranking e a mesa usarão estes nomes.",
        reviewData = "Revise os dados do perfil.",
        sessionUnavailable =
            "Sua sessão online não está disponível. Reconecte a conta.",
        connectToEditProfile =
            "Conecte uma conta para editar o perfil público.",
        profileMissing = "O perfil da conta ainda não foi criado.",
        reviewNames = "Revise o nome público e o nome curto.",
        rateLimited =
            "Muitas tentativas em pouco tempo. Aguarde e tente novamente.",
        loadFailed =
            "Não foi possível carregar o perfil agora. Tente novamente.",
        invalidResponse =
            "O servidor retornou uma resposta de perfil inválida.",
        operationFailed =
            "Não foi possível concluir a operação de perfil.",
        tableCodeRequired =
            "Use exatamente 3 caracteres no nome curto.",
        tableCodeMigrationRequired =
            "Revise a sugestão de 3 caracteres para o nome curto.",
    )

    @Test
    fun missing_server_profile_opens_editor_with_local_identity() =
        runBlocking {
            val client = FakeProfileClient(
                fetchResult =
                    OnlineAccountProfileClientResult.Failure(
                        kind =
                            OnlineAccountProfileFailureKind
                                .NOT_ESTABLISHED,
                        retryable = false,
                    ),
            )
            val coordinator = OnlineAccountProfileUiCoordinator(
                client = client,
                identityStore = FakeIdentityStore(),
                strings = profileStrings,
            )

            val outcome = coordinator.load(
                fallbackIdentity = identity(),
            )
            val state =
                outcome.state as OnlineAccountProfileUiState.Editing

            assertNull(outcome.synchronizedIdentity)
            assertEquals("Antônio Filho", state.publicDisplayName)
            assertEquals("AFI", state.tableName)
            assertFalse(state.established)
            assertEquals(1, client.fetchCount)
        }

    @Test
    fun missing_profile_replaces_legacy_default_with_short_name_suggestion() =
        runBlocking {
            val client = FakeProfileClient(
                fetchResult =
                    OnlineAccountProfileClientResult.Failure(
                        kind =
                            OnlineAccountProfileFailureKind
                                .NOT_ESTABLISHED,
                        retryable = false,
                    ),
            )
            val identityStore = FakeIdentityStore()
            val coordinator = OnlineAccountProfileUiCoordinator(
                client = client,
                identityStore = identityStore,
                strings = profileStrings,
            )

            val outcome = coordinator.load(
                fallbackIdentity = OnlinePlayerIdentity(
                    playerId = "player-1",
                    displayName = "Jogador",
                    tableName = "JOGADOR",
                ),
            )
            val state =
                outcome.state as OnlineAccountProfileUiState.Editing

            assertEquals("Jogador", state.publicDisplayName)
            assertEquals("JOG", state.tableName)
            assertEquals(
                "JOG",
                outcome.synchronizedIdentity?.tableName,
            )
            assertFalse(state.saveEnabled)
        }

    @Test
    fun successful_load_synchronizes_authoritative_profile_locally() =
        runBlocking {
            val events = mutableListOf<String>()
            val client = FakeProfileClient(
                fetchResult = profileSuccess(
                    publicDisplayName = "Maria Silva",
                    tableName = "MS1",
                ),
                events = events,
            )
            val identityStore = FakeIdentityStore(events)
            val coordinator = OnlineAccountProfileUiCoordinator(
                client = client,
                identityStore = identityStore,
                strings = profileStrings,
            )

            val outcome = coordinator.load(
                fallbackIdentity = identity(),
            )
            val state =
                outcome.state as OnlineAccountProfileUiState.Editing

            assertEquals(
                listOf(
                    "server-fetch",
                    "local-display",
                    "local-table",
                ),
                events,
            )
            assertEquals("Maria Silva", state.publicDisplayName)
            assertEquals("MS1", state.tableName)
            assertEquals(
                "Maria Silva",
                outcome.synchronizedIdentity?.displayName,
            )
            assertEquals(
                "MS1",
                outcome.synchronizedIdentity?.tableName,
            )
        }

    @Test
    fun successful_save_updates_server_before_local_identity() =
        runBlocking {
            val events = mutableListOf<String>()
            val client = FakeProfileClient(
                updateResult = profileSuccess(
                    publicDisplayName = "Antônio Filho",
                    tableName = "AFI",
                ),
                events = events,
            )
            val identityStore = FakeIdentityStore(events)
            val coordinator = OnlineAccountProfileUiCoordinator(
                client = client,
                identityStore = identityStore,
                strings = profileStrings,
            )

            val outcome = coordinator.save(
                editor = OnlineAccountProfileUiState.Editing(
                    publicDisplayName = "Antônio Filho",
                    tableName = "afi",
                    established = false,
                    validationFallbackMessage =
                        profileStrings.reviewData,
                    tableCodeValidationMessage =
                        profileStrings.tableCodeRequired,
                ),
            )

            assertEquals(
                listOf(
                    "server-update",
                    "local-display",
                    "local-table",
                ),
                events,
            )
            assertEquals(
                "Antônio Filho",
                outcome.synchronizedIdentity?.displayName,
            )
            assertEquals(
                "AFI",
                outcome.synchronizedIdentity?.tableName,
            )
            assertTrue(outcome.state.established)
        }

    @Test
    fun failed_save_preserves_local_identity() =
        runBlocking {
            val identityStore = FakeIdentityStore()
            val client = FakeProfileClient(
                updateResult =
                    OnlineAccountProfileClientResult.Failure(
                        kind =
                            OnlineAccountProfileFailureKind
                                .UNAVAILABLE,
                        retryable = true,
                    ),
            )
            val coordinator = OnlineAccountProfileUiCoordinator(
                client = client,
                identityStore = identityStore,
                strings = profileStrings,
            )

            val outcome = coordinator.save(
                editor = OnlineAccountProfileUiState.Editing(
                    publicDisplayName = "Antônio Filho",
                    tableName = "AFI",
                    established = true,
                    validationFallbackMessage =
                        profileStrings.reviewData,
                    tableCodeValidationMessage =
                        profileStrings.tableCodeRequired,
                ),
            )

            assertNull(outcome.synchronizedIdentity)
            assertEquals(0, identityStore.displayUpdateCount)
            assertEquals(0, identityStore.tableUpdateCount)
        }

    @Test
    fun invalid_editor_does_not_call_server_or_local_store() =
        runBlocking {
            val client = FakeProfileClient()
            val identityStore = FakeIdentityStore()
            val coordinator = OnlineAccountProfileUiCoordinator(
                client = client,
                identityStore = identityStore,
                strings = profileStrings,
            )

            val outcome = coordinator.save(
                editor = OnlineAccountProfileUiState.Editing(
                    publicDisplayName = "Antônio",
                    tableName = "A F!",
                    established = false,
                    validationFallbackMessage =
                        profileStrings.reviewData,
                    tableCodeValidationMessage =
                        profileStrings.tableCodeRequired,
                ),
            )

            assertEquals(0, client.updateCount)
            assertEquals(0, identityStore.displayUpdateCount)
            assertEquals(0, identityStore.tableUpdateCount)
            assertFalse(outcome.state.saveEnabled)
        }

    @Test
    fun editor_enforces_public_name_length_and_table_code_without_truncation() {
        val initial = OnlineAccountProfileUiState.Editing(
            publicDisplayName = "",
            tableName = "",
            established = false,
            validationFallbackMessage = profileStrings.reviewData,
            tableCodeValidationMessage =
                profileStrings.tableCodeRequired,
        )

        val state = initial
            .withPublicDisplayName("A".repeat(80))
            .withTableName("a")
            .withTableName("ab")
            .withTableName("ab1")
            .withTableName("ab12")

        assertEquals(60, state.publicDisplayName.length)
        assertEquals("AB1", state.tableName)
    }

    @Test
    fun legacy_table_name_is_converted_to_three_character_suggestion() =
        runBlocking {
            val client = FakeProfileClient(
                fetchResult = profileSuccess(
                    publicDisplayName = "Antônio Filho",
                    tableName = "ANTÔNIO",
                ),
            )
            val coordinator = OnlineAccountProfileUiCoordinator(
                client = client,
                identityStore = FakeIdentityStore(),
                strings = profileStrings,
            )

            val outcome = coordinator.load(
                fallbackIdentity = identity(),
            )
            val state =
                outcome.state as OnlineAccountProfileUiState.Editing

            assertEquals("ANT", state.tableName)
            assertTrue(state.saveEnabled)
            assertNull(state.validationMessage)
            assertEquals(
                profileStrings.tableCodeMigrationRequired,
                state.feedbackMessage,
            )
            assertEquals(
                "ANT",
                outcome.synchronizedIdentity?.tableName,
            )
        }

    @Test
    fun editor_filters_normalizes_and_caps_short_name_to_three_characters() {
        val initial = OnlineAccountProfileUiState.Editing(
            publicDisplayName = "Antônio Filho",
            tableName = "",
            established = true,
            validationFallbackMessage = profileStrings.reviewData,
            tableCodeValidationMessage =
                profileStrings.tableCodeRequired,
        )

        val valid = initial.withTableName("á-f_i 2026")

        assertEquals("AFI", valid.tableName)
        assertTrue(valid.saveEnabled)
    }

    private fun identity(): OnlinePlayerIdentity {
        return OnlinePlayerIdentity(
            playerId = "player-1",
            displayName = "Antônio Filho",
            tableName = "AFI",
        )
    }

    private class FakeProfileClient(
        private val fetchResult: OnlineAccountProfileClientResult =
            profileSuccess(
                publicDisplayName = "Antônio Filho",
                tableName = "AFI",
            ),
        private val updateResult: OnlineAccountProfileClientResult =
            profileSuccess(
                publicDisplayName = "Antônio Filho",
                tableName = "AFI",
            ),
        private val events: MutableList<String> =
            mutableListOf(),
    ) : OnlineAccountProfileClient {
        var fetchCount = 0
        var updateCount = 0

        override suspend fun fetch():
            OnlineAccountProfileClientResult {
            fetchCount += 1
            events += "server-fetch"
            return fetchResult
        }

        override suspend fun update(
            publicDisplayName: String,
            tableName: String?,
        ): OnlineAccountProfileClientResult {
            updateCount += 1
            events += "server-update"
            return updateResult
        }
    }

    private class FakeIdentityStore(
        private val events: MutableList<String> =
            mutableListOf(),
    ) : OnlinePlayerIdentityStore {
        private var current = OnlinePlayerIdentity(
            playerId = "player-1",
            displayName = "Jogador",
            tableName = "JOGADOR",
        )

        var displayUpdateCount = 0
        var tableUpdateCount = 0

        override fun getOrCreate(): OnlinePlayerIdentity {
            return current
        }

        override fun updateDisplayName(
            displayName: String,
        ): OnlinePlayerIdentity {
            displayUpdateCount += 1
            events += "local-display"
            current = current.copy(
                displayName = displayName,
            )
            return current
        }

        override fun updateTableName(
            tableName: String,
        ): OnlinePlayerIdentity {
            tableUpdateCount += 1
            events += "local-table"
            current = current.copy(
                tableName = tableName,
            )
            return current
        }

        override fun resetTableNameToGenerated():
            OnlinePlayerIdentity {
            return current
        }
    }
}
