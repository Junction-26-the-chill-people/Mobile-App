package com.junction.catnudge

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

internal data class StoredNudge(
    val request: NudgeRequest,
    val outsideTaps: Int = 0,
    val hasShown: Boolean = false,
    val needsReady: Boolean = request.waitForReadyScreen,
)

/** Small durable queue. Commit before exposing state so screen-off/process death cannot lose it. */
internal object NudgeStore {
    private const val FILE = "catnudge_state"
    private const val QUEUE = "queue_v1"
    private const val MONITOR = "monitor_enabled"
    private const val APP_GATE = "app_open_gate_enabled"
    private val lock = Any()

    private fun preferences(context: Context) = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun entries(context: Context): List<StoredNudge> = synchronized(lock) { read(context) }

    /** Duplicate IDs retain their original copy, destination, order, and interaction state. */
    fun add(context: Context, request: NudgeRequest): Boolean = synchronized(lock) {
        val queue = read(context)
        if (queue.any { it.request.id == request.id }) return@synchronized false
        write(context, queue + StoredNudge(request))
        true
    }

    fun update(context: Context, id: String, transform: (StoredNudge) -> StoredNudge) = synchronized(lock) {
        val queue = read(context)
        if (queue.any { it.request.id == id }) write(context, queue.map { if (it.request.id == id) transform(it) else it })
    }

    fun remove(context: Context, id: String): NudgeRequest? = synchronized(lock) {
        val queue = read(context)
        val removed = queue.firstOrNull { it.request.id == id } ?: return@synchronized null
        write(context, queue.filterNot { it.request.id == id })
        removed.request
    }

    fun clear(context: Context): List<NudgeRequest> = synchronized(lock) {
        val queue = read(context)
        write(context, emptyList())
        queue.map { it.request }
    }

    fun monitoringEnabled(context: Context): Boolean = preferences(context).getBoolean(MONITOR, false)
    fun setMonitoringEnabled(context: Context, value: Boolean) {
        check(preferences(context).edit().putBoolean(MONITOR, value).commit()) { "Could not persist monitoring setting" }
    }
    fun appOpenGateEnabled(context: Context): Boolean = preferences(context).getBoolean(APP_GATE, false)
    fun setAppOpenGateEnabled(context: Context, value: Boolean) {
        check(preferences(context).edit().putBoolean(APP_GATE, value).commit()) { "Could not persist screen readiness setting" }
    }

    private fun read(context: Context): List<StoredNudge> {
        val raw = preferences(context).getString(QUEUE, null) ?: return emptyList()
        return try {
            val root = JSONObject(raw)
            if (root.optInt("version") != 1) return emptyList()
            val entries = root.optJSONArray("entries") ?: return emptyList()
            buildList {
                val seen = mutableSetOf<String>()
                for (index in 0 until entries.length()) {
                    val entry = entries.optJSONObject(index) ?: continue
                    try {
                        val item = entry.getJSONObject("request")
                        val id = item.getString("id")
                        val destination = item.getString("destination")
                        if (id.isBlank() || destination.isBlank() || !seen.add(id)) continue
                        val copy = item.getJSONObject("copy")
                        val payloadObject = item.optJSONObject("payload") ?: JSONObject()
                        val payload = buildMap<String, String> {
                            val keys = payloadObject.keys()
                            while (keys.hasNext()) {
                                val key = keys.next()
                                put(key, payloadObject.getString(key))
                            }
                        }
                        val request = NudgeRequest(
                            id = id, destination = destination, payload = payload,
                            copy = NudgeCopy(copy.getString("invitation"), copy.getString("gentlePlea"),
                                copy.getString("finalPlea"), copy.optString("acceptLabel", "Yes")),
                            waitForReadyScreen = item.optBoolean("waitForReadyScreen", true),
                        )
                        add(StoredNudge(request, entry.optInt("taps", 0).coerceIn(0, 2),
                            entry.optBoolean("hasShown", false),
                            entry.optBoolean("needsReady", request.waitForReadyScreen)))
                    } catch (error: Exception) {
                        Log.w(CatNudge.TAG, "Ignoring an unreadable persisted invitation", error)
                    }
                }
            }
        } catch (error: Exception) {
            Log.e(CatNudge.TAG, "Could not read persisted invitations", error)
            emptyList()
        }
    }

    private fun write(context: Context, entries: List<StoredNudge>) {
        val array = JSONArray()
        entries.forEach { entry ->
            val request = entry.request
            val copy = JSONObject().put("invitation", request.copy.invitation)
                .put("gentlePlea", request.copy.gentlePlea).put("finalPlea", request.copy.finalPlea)
                .put("acceptLabel", request.copy.acceptLabel)
            val payload = JSONObject()
            request.payload.forEach { (key, value) -> payload.put(key, value) }
            val item = JSONObject().put("id", request.id).put("destination", request.destination)
                .put("payload", payload).put("copy", copy).put("waitForReadyScreen", request.waitForReadyScreen)
            array.put(JSONObject().put("request", item).put("taps", entry.outsideTaps.coerceIn(0, 2))
                .put("hasShown", entry.hasShown).put("needsReady", entry.needsReady))
        }
        val json = JSONObject().put("version", 1).put("entries", array).toString()
        check(preferences(context).edit().putString(QUEUE, json).commit()) { "Could not persist invitation queue" }
    }
}
