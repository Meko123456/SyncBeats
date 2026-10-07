package io.github.meko123456.syncbeats.core.model

import kotlin.test.Test
import kotlin.test.assertEquals

class TextCapTest {

    private val notes = "🎶" // 🎶, two chars

    @Test
    fun text_within_the_cap_is_untouched() {
        assertEquals("Swimming Pools", "Swimming Pools".capped(40))
        assertEquals("Mix $notes", "Mix $notes".capped(6))
        assertEquals("", "".capped(40))
    }

    @Test
    fun a_long_text_is_cut_at_the_cap() {
        assertEquals("T".repeat(40), "T".repeat(120).capped(40))
    }

    @Test
    fun an_emoji_across_the_cap_is_left_out_whole_not_kept_in_half() {
        assertEquals("T".repeat(39), ("T".repeat(39) + notes + " live").capped(40))
        assertEquals("T".repeat(38) + notes, ("T".repeat(38) + notes + notes).capped(40), "one that fits stays")
    }
}
