package com.still.music

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.still.music.data.*
import com.still.music.provider.Track
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PersistenceTest {
    @Test fun importRetryPreservesOrderAndBatchDuplicatesDoNotCreateExtraRows() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, StillDatabase::class.java).build()
        try {
            val repo = LibraryRepository(db.library())
            val id = repo.create("Import")
            val tracks = List(3) { Track("import:$it", "Song $it", "Artist") }
            repo.importBatch(id, listOf(0 to tracks[0], 2 to tracks[2]))
            repo.importBatch(id, listOf(1 to tracks[1], 2 to tracks[2]))
            assertEquals(tracks, repo.tracks(id).first())
        } finally { db.close() }
    }
    @Test fun favouritesPlaylistAndRecentsSurviveReopen() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "test-${UUID.randomUUID()}.db"
        fun open() = Room.databaseBuilder(context, StillDatabase::class.java, name).build()
        val track = Track("audius:p", "Persistent title", "Artist")
        var db = open()
        try {
            var repo = LibraryRepository(db.library())
            repo.toggle(track)
            val playlist = repo.create("Evening")
            repo.add(playlist, track); repo.add(playlist, track)
            repo.record(track)
            db.close(); db = open(); repo = LibraryRepository(db.library())
            assertEquals(listOf(track), repo.favourites.first())
            assertEquals(listOf(track), repo.tracks(playlist).first())
            assertEquals(listOf(track), repo.recents.first())
            repo.delete(playlist)
            assertTrue(repo.tracks(playlist).first().isEmpty())
            assertEquals(listOf(track), repo.favourites.first())
            repo.toggle(track)
            assertTrue(repo.favourites.first().isEmpty())
        } finally { db.close(); context.deleteDatabase(name) }
    }
}
