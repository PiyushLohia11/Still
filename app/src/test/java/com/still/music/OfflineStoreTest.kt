package com.still.music

import com.still.music.offline.OfflineStore
import com.still.music.provider.Track
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException

class OfflineStoreTest {
    @get:Rule val folder = TemporaryFolder()
    @Test fun incompleteDownloadIsNeverPlayableAndMetadataSurvivesReopen() {
        val root = folder.newFolder()
        val store = OfflineStore(root)
        val track = Track("youtube:abcdefghijk", "Example", "Artist", durationMs = 90000)
        store.remember(track)
        store.partial(track.id).writeBytes(byteArrayOf(1, 2, 3))
        assertNull(store.audio(track.id))
        assertThrows(IOException::class.java) { store.commit(track.id, 10, 3) }
        assertNull(store.audio(track.id))
        assertEquals(track, OfflineStore(root).tracks().single())
        store.commit(track.id, 3, 3)
        assertEquals(3L, OfflineStore(root).audio(track.id)!!.length())
        store.remove(track.id)
        assertNull(store.audio(track.id))
        assertTrue(store.tracks().isEmpty())
    }
    @Test fun untrustedIdentifiersCannotEscapeStorage() {
        val root = folder.newFolder()
        val store = OfflineStore(root)
        val id = "../../outside"
        assertEquals(root.canonicalFile, store.partial(id).parentFile!!.canonicalFile)
        assertEquals(64, store.key(id).length)
    }
}
