package com.ahtohiofilho.dominopernambucano.advertising

import android.content.SharedPreferences
import com.ahtohiofilho.dominopernambucano.ui.game.PostMatchRecoveryPlayer
import com.ahtohiofilho.dominopernambucano.ui.game.PostMatchRecoverySnapshot
import com.ahtohiofilho.dominopernambucano.ui.game.isPostMatchRecoveryActive
import org.json.JSONArray
import org.json.JSONObject

/**
 * Keeps only an authentic final-result receipt until its fixed deadline.
 * The actual online/offline match lifecycle is never prolonged.
 */
internal class PostMatchRecoveryStore(
    private val preferences: SharedPreferences,
    private val nowEpochMillis: () -> Long = System::currentTimeMillis,
) {
    fun save(receipt: PostMatchRecoverySnapshot) {
        require(receipt.recordedAtEpochMillis > 0L)

        val players = JSONArray()
        receipt.players.forEach { player ->
            players.put(
                JSONObject()
                    .put("seat", player.seatIndex)
                    .put("name", player.name)
                    .put("points", player.pointsScored)
                    .put("touches", player.touchesGiven)
                    .put("automatic", player.automaticRounds),
            )
        }
        val balances = JSONArray()
        receipt.teamBalanceDeltaByTeam.forEach(balances::put)

        val json = JSONObject()
            .put("version", 1)
            .put("time", receipt.recordedAtEpochMillis)
            .put("ranked", receipt.isRanked)
            .put("won", receipt.localTeamWon ?: JSONObject.NULL)
            .put("localScore", receipt.localScore)
            .put("opponentScore", receipt.opponentScore)
            .put("rounds", receipt.completedRounds)
            .put("players", players)
            .put("balances", balances)
            .put("mvp", receipt.mvpSeatIndex ?: JSONObject.NULL)

        // Synchronous commit closes the close/reopen race on Android.
        preferences.edit().putString(PendingReceiptKey, json.toString()).commit()
    }

    fun readValid(): PostMatchRecoverySnapshot? {
        val raw = preferences.getString(PendingReceiptKey, null) ?: return null
        val snapshot = runCatching { decode(JSONObject(raw)) }.getOrNull()
        if (
            snapshot == null ||
            !isPostMatchRecoveryActive(
                recordedAtEpochMillis = snapshot.recordedAtEpochMillis,
                nowEpochMillis = nowEpochMillis(),
            )
        ) {
            clear()
            return null
        }
        return snapshot
    }

    fun clear() {
        preferences.edit().remove(PendingReceiptKey).commit()
    }

    private fun decode(json: JSONObject): PostMatchRecoverySnapshot {
        require(json.getInt("version") == 1)
        val playerArray = json.getJSONArray("players")
        require(playerArray.length() == 4)
        val players = (0 until playerArray.length()).map { position ->
            val item = playerArray.getJSONObject(position)
            PostMatchRecoveryPlayer(
                seatIndex = item.getInt("seat"),
                name = item.getString("name").take(64),
                pointsScored = item.getInt("points"),
                touchesGiven = item.getInt("touches"),
                automaticRounds = item.getInt("automatic"),
            )
        }
        require(players.map { it.seatIndex }.sorted() == (0..3).toList())
        val balanceArray = json.getJSONArray("balances")
        require(balanceArray.length() == 2)

        val snapshot = PostMatchRecoverySnapshot(
            recordedAtEpochMillis = json.getLong("time"),
            isRanked = json.getBoolean("ranked"),
            localTeamWon = if (json.isNull("won")) null else json.getBoolean("won"),
            localScore = json.getInt("localScore"),
            opponentScore = json.getInt("opponentScore"),
            completedRounds = json.getInt("rounds"),
            players = players,
            teamBalanceDeltaByTeam = (0 until 2).map(balanceArray::getInt),
            mvpSeatIndex = if (json.isNull("mvp")) null else json.getInt("mvp"),
        )
        require(snapshot.localScore >= 0 && snapshot.opponentScore >= 0)
        require(snapshot.completedRounds >= 0)
        require(snapshot.mvpSeatIndex == null || snapshot.mvpSeatIndex in 0..3)
        return snapshot
    }

    private companion object {
        const val PendingReceiptKey = "pending_post_match_receipt_v35"
    }
}
