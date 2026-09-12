package com.still.music.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.still.music.provider.Track
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.*
import java.io.IOException

private val Context.preferences by preferencesDataStore("still")
data class QueueSnapshot(val tracks: List<Track> = emptyList(), val index: Int = 0, val positionMs: Long = 0,
    val shuffle: Boolean = false, val repeat: Int = 0, val shuffleOrder: List<Int> = emptyList())
object SnapshotCodec {
    fun encode(value: QueueSnapshot): String = buildJsonObject {
        put("index", value.index); put("position", value.positionMs); put("shuffle", value.shuffle); put("repeat", value.repeat)
        put("order", JsonArray(value.shuffleOrder.map { JsonPrimitive(it) }))
        put("tracks", buildJsonArray {
            value.tracks.forEach { track -> add(buildJsonObject {
                put("id", track.id); put("title", track.title); put("artist", track.artist)
                track.artwork?.let { put("artwork", it) }
                put("duration", track.durationMs)
                track.unavailableReason?.let { put("unavailable", it) }
            }) }
        })
    }.toString()
    fun decode(text: String?): QueueSnapshot = runCatching {
        if (text == null) return QueueSnapshot()
        val obj = Json.parseToJsonElement(text).jsonObject
        val tracks = obj.getValue("tracks").jsonArray.map {
            val row = it.jsonObject
            Track(row.getValue("id").jsonPrimitive.content, row.getValue("title").jsonPrimitive.content,
                row.getValue("artist").jsonPrimitive.content, row["artwork"]?.jsonPrimitive?.contentOrNull,
                row["duration"]?.jsonPrimitive?.longOrNull ?: 0, row["unavailable"]?.jsonPrimitive?.contentOrNull)
        }.take(300)
        val order = obj["order"]?.jsonArray?.map { it.jsonPrimitive.int }.orEmpty()
        QueueSnapshot(tracks,
            (obj["index"]?.jsonPrimitive?.intOrNull ?: 0).coerceIn(0, (tracks.size - 1).coerceAtLeast(0)),
            (obj["position"]?.jsonPrimitive?.longOrNull ?: 0).coerceAtLeast(0),
            obj["shuffle"]?.jsonPrimitive?.booleanOrNull ?: false,
            (obj["repeat"]?.jsonPrimitive?.intOrNull ?: 0).takeIf { it in 0..2 } ?: 0,
            order.takeIf { it.sorted() == tracks.indices.toList() } ?: tracks.indices.toList())
    }.getOrDefault(QueueSnapshot())
}
class Preferences(context: Context) {
    private val store = context.preferences
    private val queue = stringPreferencesKey("queue_v1")
    private val preload = booleanPreferencesKey("preload")
    private val safeData = store.data.catch { if (it is IOException) emit(emptyPreferences()) else throw it }
    val snapshots = safeData.map { SnapshotCodec.decode(it[queue]) }
    val preloading = safeData.map { it[preload] ?: true }
    suspend fun save(snapshot: QueueSnapshot) { store.edit { it[queue] = SnapshotCodec.encode(snapshot) } }
    suspend fun preload(enabled: Boolean) { store.edit { it[preload] = enabled } }
}
