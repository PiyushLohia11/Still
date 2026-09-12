package com.still.music.offline

import com.still.music.provider.Track
import kotlinx.serialization.json.*
import java.io.File
import java.io.IOException
import java.security.MessageDigest

/** Metadata and completed audio survive process death; partial files are never playable. */
class OfflineStore(private val root: File) {
    init { check(root.mkdirs() || root.isDirectory) { "Cannot open download storage." } }
    fun key(id: String): String = MessageDigest.getInstance("SHA-256").digest(id.toByteArray())
        .joinToString("") { "%02x".format(it) }
    fun audio(id: String) = File(root, key(id) + ".audio").takeIf { it.isFile && it.length() > 0 }
    fun partial(id: String) = File(root, key(id) + ".part")
    fun remember(track: Track, workId: String? = null) {
        val text = buildJsonObject {
            put("workId", workId)
            put("id", track.id); put("title", track.title); put("artist", track.artist)
            put("artwork", track.artwork); put("duration", track.durationMs)
        }.toString()
        val target = File(root, key(track.id) + ".json")
        val temporary = File(root, key(track.id) + ".json.tmp")
        temporary.writeText(text)
        if (!temporary.renameTo(target)) throw IOException("Cannot save download details.")
    }
    fun workId(id: String): String? = runCatching {
        Json.parseToJsonElement(File(root, key(id) + ".json").readText()).jsonObject["workId"]?.jsonPrimitive?.contentOrNull
    }.getOrNull()
    fun tracks(): List<Track> = root.listFiles().orEmpty().filter { it.extension == "json" }.mapNotNull { file ->
        runCatching {
            val row = Json.parseToJsonElement(file.readText()).jsonObject
            Track(row.getValue("id").jsonPrimitive.content, row.getValue("title").jsonPrimitive.content,
                row.getValue("artist").jsonPrimitive.content, row["artwork"]?.jsonPrimitive?.contentOrNull,
                row.getValue("duration").jsonPrimitive.long)
        }.getOrNull()
    }
    fun commit(id: String, expected: Long, received: Long) {
        val part = partial(id)
        if (received <= 0 || part.length() != received || (expected >= 0 && expected != received))
            throw IOException("Download was incomplete. Please retry.")
        if (!part.renameTo(File(root, key(id) + ".audio"))) throw IOException("Cannot finish download.")
    }
    fun remove(id: String) {
        listOf(".audio", ".part", ".json", ".json.tmp").forEach { suffix ->
            val file = File(root, key(id) + suffix)
            if (file.exists() && !file.delete()) throw IOException("Cannot remove download.")
        }
    }
}
