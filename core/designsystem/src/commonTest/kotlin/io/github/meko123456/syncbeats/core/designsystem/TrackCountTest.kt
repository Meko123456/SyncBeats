package io.github.meko123456.syncbeats.core.designsystem

import kotlin.test.Test
import kotlin.test.assertEquals

class TrackCountTest {

    @Test
    fun `one track is a track`() {
        // A playlist of one song read "1 tracks", on home and in the room.
        assertEquals("1 track", trackCount(1))
        assertEquals("0 tracks", trackCount(0))
        assertEquals("2 tracks", trackCount(2))
        assertEquals("150 tracks", trackCount(150))
    }
}
