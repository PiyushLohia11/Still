package com.still.music

import com.still.music.playback.PlaybackResolver
import com.still.music.provider.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PlaybackResolverTest {
    @get:Rule val folder = TemporaryFolder()
    private class Provider : MusicProvider {
        override val id = "test"
        var calls = 0
        override suspend fun search(query: String, offset: Int) = emptyList<Track>()
        override suspend fun trackDetails(qualifiedId: String) = Track(qualifiedId, "", "")
        override suspend fun resolvePlayback(qualifiedId: String): PlaybackAccess {
            calls++
            return PlaybackAccess("https://example.com/audio")
        }
    }
    @Test fun rangeReadsReuseStreamButExpiryAndFailureInvalidateIt() = runBlocking {
        val provider = Provider()
        var now = 0L
        val resolver = PlaybackResolver(ProviderRegistry(provider), { null }, { now })
        resolver.resolve("test:1"); resolver.resolve("test:1")
        assertEquals(1, provider.calls)
        now = 120001
        resolver.resolve("test:1")
        assertEquals(2, provider.calls)
        resolver.invalidate("test:1"); resolver.resolve("test:1")
        assertEquals(3, provider.calls)
    }
    @Test fun downloadedAudioNeedsNoProviderOrNetwork() = runBlocking {
        val file = folder.newFile().apply { writeBytes(byteArrayOf(1)) }
        val resolver = PlaybackResolver(ProviderRegistry(), { file })
        assertTrue(resolver.resolve("youtube:abcdefghijk").url.startsWith("file:"))
    }
}
