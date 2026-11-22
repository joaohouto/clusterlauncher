package com.joaohouto.clusterlauncher.player

import com.joaohouto.clusterlauncher.data.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class PlayerControllerShuffleTest {

    private fun sampleTracks(count: Int): List<Track> = (0 until count).map { i ->
        Track(
            id = i.toLong() + 1L,
            uri = "file:///music/track_$i.mp3",
            path = "/music/track_$i.mp3",
            title = "Track $i",
            artist = "Artist",
            album = "Album",
            durationMs = 180_000L,
            folderPath = "/music",
            trackNumber = i + 1
        )
    }

    @Test
    fun shuffleWithoutExplicitStartIndex_picksRandomTrack() {
        val tracks = sampleTracks(10)
        val pickedIndices = (1..20).map { seed ->
            PlayerController.resolveFolderStartIndex(
                allTracks = tracks,
                validTracks = tracks,
                startIndex = null,
                effectiveShuffle = true,
                random = Random(seed)
            )
        }.toSet()

        assertTrue("Expected multiple distinct start indices in shuffle mode, got $pickedIndices", pickedIndices.size > 1)
        assertTrue("Expected at least one non-zero start index in shuffle mode", pickedIndices.any { it != 0 })
    }

    @Test
    fun sequentialWithoutExplicitStartIndex_startsAtZero() {
        val tracks = sampleTracks(10)
        val idx = PlayerController.resolveFolderStartIndex(
            allTracks = tracks,
            validTracks = tracks,
            startIndex = null,
            effectiveShuffle = false,
            random = Random(42)
        )
        assertEquals(0, idx)
    }

    @Test
    fun explicitStartIndex_isRespectedEvenWhenShuffleEnabled() {
        val tracks = sampleTracks(10)
        val idx = PlayerController.resolveFolderStartIndex(
            allTracks = tracks,
            validTracks = tracks,
            startIndex = 4,
            effectiveShuffle = true,
            random = Random(42)
        )
        assertEquals(4, idx)
    }

    @Test
    fun explicitStartIndex_mapsToFilteredValidTracks() {
        val allTracks = sampleTracks(5)
        val validTracks = listOf(allTracks[0], allTracks[2], allTracks[4])
        val idx = PlayerController.resolveFolderStartIndex(
            allTracks = allTracks,
            validTracks = validTracks,
            startIndex = 4,
            effectiveShuffle = false
        )
        assertEquals(2, idx)
    }

    @Test
    fun emptyOrSingleTrackList_returnsZeroSafely() {
        assertEquals(
            0,
            PlayerController.resolveFolderStartIndex(
                allTracks = emptyList(),
                validTracks = emptyList(),
                startIndex = null,
                effectiveShuffle = true
            )
        )

        val single = sampleTracks(1)
        assertEquals(
            0,
            PlayerController.resolveFolderStartIndex(
                allTracks = single,
                validTracks = single,
                startIndex = null,
                effectiveShuffle = true
            )
        )
    }
}
