package com.still.music

import com.still.music.provider.artworkUrls
import com.still.music.provider.bestArtwork
import com.still.music.provider.playerArtworkUrls
import com.still.music.ui.posterColor
import org.junit.Assert.*
import org.junit.Test
import org.schabi.newpipe.extractor.Image

class ArtworkTest {
    @Test fun selectsLargeThumbnailRegardlessOfListOrder() {
        val small = Image("small", 120, 120, Image.ResolutionLevel.LOW)
        val large = Image("large", 1200, 1200, Image.ResolutionLevel.HIGH)
        val medium = Image("medium", 480, 480, Image.ResolutionLevel.MEDIUM)
        assertEquals("large", bestArtwork(listOf(small, large, medium)))
        assertEquals("large", bestArtwork(listOf(medium, large, small)))
        assertNull(bestArtwork(emptyList()))
    }

    @Test fun upgradesSavedMusicArtworkAndKeepsOriginalFallback() {
        val original = "https://lh3.googleusercontent.com/album=w120-h120-l90-rj"
        assertEquals(listOf("https://lh3.googleusercontent.com/album=w1200-h1200", original),
            playerArtworkUrls(original))
        assertEquals("https://lh3.ggpht.com/album=w1200-h1200",
            playerArtworkUrls("https://lh3.ggpht.com/album=s60-c").first())
    }

    @Test fun videoArtworkHasOrderedFallbacks() {
        val original = "https://i.ytimg.com/vi/dQw4w9WgXcQ/default.jpg"
        val urls = playerArtworkUrls(original)
        assertTrue(urls[0].endsWith("/maxresdefault.jpg"))
        assertTrue(urls[1].endsWith("/sddefault.jpg"))
        assertTrue(urls[2].endsWith("/hqdefault.jpg"))
        assertEquals(original, urls.last())
        assertEquals(urls.size, urls.distinct().size)
    }

    @Test fun preservesUnknownAndLocalArtwork() {
        for (url in listOf("https://example.com/album=w120-h120", "file:///saved/art.jpg", "not a URI")) {
            assertEquals(listOf(url), playerArtworkUrls(url))
        }
        assertTrue(playerArtworkUrls(null).isEmpty())
    }

    @Test fun listArtworkIsUpgradedWithoutCorruptingSignedQuery() {
        val original = "https://lh5.googleusercontent.com/album=w60-h60?token=a=b"
        assertEquals("https://lh5.googleusercontent.com/album=w480-h480?token=a=b",
            artworkUrls(original).first())
        assertEquals(original, artworkUrls(original).last())
        assertEquals("https://lh3.ggpht.com/album=w480-h480",
            artworkUrls("https://lh3.ggpht.com/album").first())
    }

    @Test fun missingAndWebpArtworkRecoverUsingVideoId() {
        val id = "dQw4w9WgXcQ"
        assertEquals("https://i.ytimg.com/vi/" + id + "/maxresdefault.jpg",
            artworkUrls(null, trackId = "youtube:" + id).first())
        assertTrue(artworkUrls(null, trackId = "youtube:invalid").isEmpty())
        val original = "https://i.ytimg.com/vi_webp/" + id + "/default.webp?signature=old"
        assertTrue(artworkUrls(original).first().endsWith("/maxresdefault.jpg"))
        assertEquals(original, artworkUrls(original).last())
    }

    @Test fun posterHueSurvivesBlackBordersAndWhiteText() {
        val blue = 0xFF2060D0.toInt()
        val pixels = IntArray(400) { 0xFF000000.toInt() } +
            IntArray(400) { 0xFFFFFFFF.toInt() } + IntArray(200) { blue }
        assertEquals(blue, posterColor(pixels))
        assertEquals(0xFFD04020.toInt(), posterColor(IntArray(200) { 0xFFD04020.toInt() }))
    }

    @Test fun missingOrTransparentArtUsesNeutralFallback() {
        assertEquals(0xFF414953.toInt(), posterColor(intArrayOf()))
        assertEquals(0xFF414953.toInt(), posterColor(intArrayOf(0x00FF0000, 0xFFFFFFFF.toInt())))
        assertEquals(0xFF808080.toInt(), posterColor(intArrayOf(0xFF808080.toInt())))
    }
}