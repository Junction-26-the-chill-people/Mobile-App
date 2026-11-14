package com.example.junction.sola.data

import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Matches the supplied AI Studio server. Bot credentials belong on that server, never in an APK.
 */
class TelegramGateway(private val base: String, private val debug: Boolean) {
    companion object {
        fun connectionStatus(json: JSONObject): String {
            val info =
                json.optJSONObject("botInfo") ?: return "Bridge online · bot status unavailable"
            val problem = info.optString("error")
            if (problem.contains("Conflict", true) || problem.contains("webhook", true))
                return "Bot ready · incoming paused (another bot instance)"
            if (problem.isNotBlank()) return "Bot connection: ${problem.take(180)}"
            if (!info.optBoolean("enabled")) return "Bridge online · bot token missing"
            return "Bot connected" +
                info
                    .optString("botUsername")
                    .takeIf { it.isNotBlank() }
                    ?.let { " · @$it" }
                    .orEmpty()
        }

        fun validBase(value: String, debug: Boolean): Boolean = runCatching {
            val uri = URI(value.trim())
            uri.host != null &&
                uri.userInfo == null &&
                uri.query == null &&
                uri.fragment == null &&
                (uri.scheme == "https" ||
                    (debug &&
                        uri.scheme == "http" &&
                        uri.host in setOf("127.0.0.1", "localhost", "10.0.2.2")))
        }
            .getOrDefault(false)
    }

    private suspend fun request(
        path: String,
        method: String = "GET",
        body: JSONObject? = null,
    ): JSONObject =
        withContext(Dispatchers.IO) {
            require(validBase(base, debug)) {
                "Use an HTTPS backend URL (debug builds also support localhost)."
            }
            val connection =
                URL(base.trim().trimEnd('/') + path).openConnection() as HttpURLConnection
            try {
                connection.requestMethod = method
                connection.connectTimeout = 10_000
                connection.readTimeout = 10_000
                connection.instanceFollowRedirects = false
                connection.setRequestProperty("Accept", "application/json")
                if (body != null) {
                    connection.doOutput = true
                    connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    connection.outputStream.use {
                        it.write(body.toString().toByteArray(Charsets.UTF_8))
                    }
                }
                val code = connection.responseCode
                val response =
                    (if (code in 200..299) connection.inputStream else connection.errorStream)
                        ?.bufferedReader()
                        ?.use { it.readText() }
                        .orEmpty()
                val json = runCatching { JSONObject(response) }.getOrElse { JSONObject() }
                check(code in 200..299) {
                    json.optString("error", "Telegram service returned HTTP $code.")
                }
                json
            } finally {
                connection.disconnect()
            }
        }

    suspend fun state() = request("/api/telegram/state")

    suspend fun profile(profile: SolaProfile) =
        request(
            "/api/telegram/profile",
            "POST",
            JSONObject()
                .put("name", profile.name)
                .put("telegramHandle", profile.telegram)
                .put("telegramUserId", profile.telegramId),
        )

    /**
     * Adding on the reference server sends an invite; call only after the owner's explicit Send
     * action.
     */
    suspend fun registerFriend(friend: SolaFriend): String {
        require(friend.telegramId.matches(Regex("-?[0-9]+"))) {
            "Add this friend's numeric Telegram User ID first."
        }
        val snapshot =
            request(
                "/api/telegram/friends",
                "POST",
                JSONObject()
                    .put("name", friend.name)
                    .put("telegramUserId", friend.telegramId)
                    .put("telegram", friend.telegram),
            )
        val rows = snapshot.optJSONArray("friends")
        return (0 until (rows?.length() ?: 0))
            .mapNotNull { rows?.optJSONObject(it) }
            .firstOrNull {
                it.optString("telegramChatId") == friend.telegramId ||
                    it.optString("telegramUserId") == friend.telegramId
            }
            ?.optString("id")
            ?.takeIf { it.isNotBlank() } ?: error("The server did not return the friend's ID.")
    }

    suspend fun sendButterfly(friendId: String): String {
        val json =
            request("/api/telegram/send-butterfly", "POST", JSONObject().put("friendId", friendId))
        val message = json.optJSONObject("message") ?: error("No delivery receipt was returned.")
        check(message.optString("deliveredVia") == "telegram_bot") {
            message.optString("deliveryError").ifBlank {
                "Saved to the server relay, but Telegram has not delivered it."
            }
        }
        return "Butterfly delivered via Telegram."
    }

    suspend fun dismiss(id: String) =
        request("/api/telegram/butterflies/${java.net.URLEncoder.encode(id, "UTF-8")}", "DELETE")

    fun butterflies(json: JSONObject): List<Butterfly> {
        val rows = json.optJSONArray("butterflies") ?: return emptyList()
        return (0 until rows.length())
            .mapNotNull { rows.optJSONObject(it) }
            .map {
                Butterfly(
                    it.getString("id"),
                    it.optString("friendName", "A friend"),
                    it.optString("message"),
                    runCatching {
                            java.text
                                .SimpleDateFormat(
                                    "yyyy-MM-dd'T'HH:mm:ss.SSSX",
                                    java.util.Locale.ROOT,
                                )
                                .parse(it.optString("receivedAt"))
                                ?.time ?: 0L
                        }
                        .getOrDefault(0L),
                )
            }
    }
}
