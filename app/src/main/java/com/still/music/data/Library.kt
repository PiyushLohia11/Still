package com.still.music.data

import androidx.room.*
import com.still.music.provider.Track
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.ln
import kotlin.random.Random

@Entity(tableName = "tracks")
data class StoredTrack(@PrimaryKey val id: String, val title: String, val artist: String,
    val artwork: String?, val durationMs: Long, val unavailableReason: String?) {
    fun track() = Track(id, title, artist, artwork, durationMs, unavailableReason)
    companion object { fun from(track: Track) = with(track) { StoredTrack(id, title, artist, artwork, durationMs, unavailableReason) } }
}
@Entity(tableName = "favourites", foreignKeys = [ForeignKey(entity = StoredTrack::class,
    parentColumns = ["id"], childColumns = ["trackId"], onDelete = ForeignKey.CASCADE)])
data class Favourite(@PrimaryKey val trackId: String, val addedAt: Long = System.currentTimeMillis())
@Entity(tableName = "recent", foreignKeys = [ForeignKey(entity = StoredTrack::class,
    parentColumns = ["id"], childColumns = ["trackId"], onDelete = ForeignKey.CASCADE)])
data class Recent(@PrimaryKey val trackId: String, val playedAt: Long = System.currentTimeMillis())
@Entity(tableName = "playlists")
data class Playlist(@PrimaryKey(autoGenerate = true) val id: Long = 0, val name: String)
@Entity(tableName = "playlist_entries", primaryKeys = ["playlistId", "trackId"],
    indices = [Index("trackId")], foreignKeys = [
        ForeignKey(entity = Playlist::class, parentColumns = ["id"], childColumns = ["playlistId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = StoredTrack::class, parentColumns = ["id"], childColumns = ["trackId"], onDelete = ForeignKey.CASCADE)])
data class PlaylistEntry(val playlistId: Long, val trackId: String, val position: Int)

@Dao
abstract class LibraryDao {
    @Upsert abstract suspend fun upsert(track: StoredTrack)
    @Insert(onConflict = OnConflictStrategy.IGNORE) abstract suspend fun favourite(value: Favourite)
    @Query("DELETE FROM favourites WHERE trackId = :id") abstract suspend fun unfavourite(id: String)
    @Query("SELECT EXISTS(SELECT 1 FROM favourites WHERE trackId = :id)") abstract suspend fun isFavourite(id: String): Boolean
    @Query("SELECT t.* FROM tracks t INNER JOIN favourites f ON t.id = f.trackId ORDER BY f.addedAt DESC") abstract fun favourites(): Flow<List<StoredTrack>>
    @Query("SELECT t.* FROM tracks t INNER JOIN recent r ON t.id = r.trackId ORDER BY r.playedAt DESC LIMIT 100") abstract fun recents(): Flow<List<StoredTrack>>
    @Query("SELECT t.* FROM tracks t INNER JOIN recent r ON t.id = r.trackId ORDER BY r.playedAt DESC LIMIT 100")
    abstract suspend fun recentMixTracks(): List<StoredTrack>
    @Query("SELECT t.* FROM tracks t INNER JOIN favourites f ON t.id = f.trackId")
    abstract suspend fun favouriteMixTracks(): List<StoredTrack>
    @Transaction open suspend fun mixHistory(): Pair<List<StoredTrack>, List<StoredTrack>> =
        recentMixTracks() to favouriteMixTracks()
    @Upsert abstract suspend fun recent(value: Recent)
    @Query("DELETE FROM recent WHERE trackId NOT IN (SELECT trackId FROM recent ORDER BY playedAt DESC LIMIT 100)") abstract suspend fun trimRecents()
    @Query("SELECT * FROM playlists ORDER BY id DESC") abstract fun playlists(): Flow<List<Playlist>>
    @Insert abstract suspend fun create(playlist: Playlist): Long
    @Query("DELETE FROM playlists WHERE id = :id") abstract suspend fun deletePlaylist(id: Long)
    @Query("UPDATE playlists SET name = :name WHERE id = :id") abstract suspend fun renamePlaylist(id: Long, name: String)
    @Query("SELECT t.* FROM tracks t INNER JOIN playlist_entries p ON t.id = p.trackId WHERE p.playlistId = :id ORDER BY p.position") abstract fun playlistTracks(id: Long): Flow<List<StoredTrack>>
    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM playlist_entries WHERE playlistId = :id") abstract suspend fun nextPosition(id: Long): Int
    @Insert(onConflict = OnConflictStrategy.IGNORE) abstract suspend fun add(entry: PlaylistEntry)
    @Query("DELETE FROM playlist_entries WHERE playlistId = :playlist AND trackId = :track") abstract suspend fun remove(playlist: Long, track: String)
    @Transaction open suspend fun toggleFavourite(track: Track) {
        upsert(StoredTrack.from(track))
        if (isFavourite(track.id)) unfavourite(track.id) else favourite(Favourite(track.id))
    }
    @Transaction open suspend fun record(track: Track) {
        upsert(StoredTrack.from(track)); recent(Recent(track.id)); trimRecents()
    }
    @Transaction open suspend fun addToPlaylist(playlist: Long, track: Track) {
        upsert(StoredTrack.from(track))
        add(PlaylistEntry(playlist, track.id, nextPosition(playlist)))
    }
    @Transaction open suspend fun importBatch(playlist: Long, tracks: List<Pair<Int, Track>>) {
        tracks.forEach { (position, track) ->
            upsert(StoredTrack.from(track))
            add(PlaylistEntry(playlist, track.id, position))
        }
    }
}
@Database(entities = [StoredTrack::class, Favourite::class, Recent::class, Playlist::class, PlaylistEntry::class], version = 1, exportSchema = true)
abstract class StillDatabase : RoomDatabase() { abstract fun library(): LibraryDao }
class LibraryRepository(private val dao: LibraryDao) {
    val favourites = dao.favourites().map { rows -> rows.map { it.track() } }
    val recents = dao.recents().map { rows -> rows.map { it.track() } }
    val playlists = dao.playlists()
    fun tracks(playlist: Long) = dao.playlistTracks(playlist).map { rows -> rows.map { it.track() } }
    suspend fun toggle(track: Track) = dao.toggleFavourite(track)
    suspend fun record(track: Track) = dao.record(track)
    suspend fun generateSmartMix(): List<Track> = withContext(Dispatchers.Default) {
        val (history, saved) = dao.mixHistory()
        val recentTracks = history.map { it.track() }
        val favourites = saved.map { it.track() }.filter(Track::playable)
        val artistCounts = recentTracks.filter { it.artist.isNotBlank() }
            .groupingBy { it.artist.trim().lowercase(java.util.Locale.ROOT) }.eachCount()
        val favouriteIds = favourites.mapTo(hashSetOf(), Track::id)
        val candidates = (favourites + recentTracks).filter(Track::playable).distinctBy(Track::id)
        // Reserve five places for exploration, even when one artist dominates history.
        val randomFavourites = favourites.shuffled().take(5)
        val reservedIds = randomFavourites.mapTo(hashSetOf(), Track::id)
        fun weight(track: Track): Double {
            val affinity = artistCounts[track.artist.trim().lowercase(java.util.Locale.ROOT)] ?: 0
            return 1.0 + affinity * 2.0 + if (track.id in favouriteIds) 3.0 else 0.0
        }
        // Exponential keys give a weighted shuffle without replacement.
        fun ranked(tracks: List<Track>) = tracks.map { track ->
            track to -ln(1.0 - Random.nextDouble()) / weight(track)
        }.sortedBy { it.second }.map { it.first }
        val preferred = ranked(candidates.filterNot { it.id in reservedIds })
            .take(25 - randomFavourites.size)
        ranked(preferred + randomFavourites)
    }
    suspend fun create(name: String): Long {
        require(name.isNotBlank()) { "Enter a playlist name." }
        return dao.create(Playlist(name = name.trim().take(100)))
    }
    suspend fun rename(id: Long, name: String) {
        require(name.isNotBlank()) { "Enter a playlist name." }
        dao.renamePlaylist(id, name.trim().take(100))
    }
    suspend fun delete(id: Long) = dao.deletePlaylist(id)
    suspend fun add(id: Long, track: Track) = dao.addToPlaylist(id, track)
    suspend fun importBatch(id: Long, tracks: List<Pair<Int, Track>>) = dao.importBatch(id, tracks)
    suspend fun remove(id: Long, track: String) = dao.remove(id, track)
}
