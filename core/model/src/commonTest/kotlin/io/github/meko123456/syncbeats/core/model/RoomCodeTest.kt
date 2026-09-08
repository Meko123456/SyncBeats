package io.github.meko123456.syncbeats.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RoomCodeTest {

    @Test
    fun `a generated code is the right length and uses only the alphabet`() {
        var index = 0
        val code = RoomCode.generate { bound -> (index++) % bound }
        assertEquals(RoomCode.LENGTH, code.length)
        assertTrue(code.all { it in RoomCode.ALPHABET }, code)
    }

    @Test
    fun `the alphabet contains no character that can be misread aloud`() {
        // The whole reason the alphabet is short. If someone widens it later, this fails.
        for (character in RoomCode.LOOKALIKES) {
            assertFalse(character in RoomCode.ALPHABET, "the alphabet must not contain $character")
        }
    }

    @Test
    fun `generation only ever asks for an index inside the alphabet`() {
        // A generator that took a bound and ignored it would silently produce codes outside the
        // alphabet, which would then never match a real room.
        val bounds = mutableListOf<Int>()
        RoomCode.generate { bound -> bounds += bound; 0 }
        assertTrue(bounds.all { it == RoomCode.ALPHABET.length }, "bounds asked for: $bounds")
    }

    @Test
    fun `a valid code passes`() {
        assertNull(RoomCode.problemWith("ABC234"))
        assertTrue(RoomCode.isValid("ABC234"))
    }

    @Test
    fun `case and the separators people add when reading a code out are forgiven`() {
        assertEquals("ABC234", RoomCode.normalise("abc234"))
        assertEquals("ABC234", RoomCode.normalise("ABC-234"))
        assertEquals("ABC234", RoomCode.normalise("abc 234"))
        assertEquals("ABC234", RoomCode.normalise("  ABC_234  "))
        assertTrue(RoomCode.isValid("abc-234"))
    }

    @Test
    fun `a paste from a chat app survives its invisible characters`() {
        // A non-breaking space and an en dash look exactly like the keyboard ones.
        assertEquals("ABC234", RoomCode.normalise("ABC 234"))
        assertEquals("ABC234", RoomCode.normalise("ABC–234"))
    }

    @Test
    fun `a pasted code with a separator is no longer silently truncated`() {
        // The bug this replaced: uppercase().take(6) turned "ABC-234" into "ABC-23", which is six
        // characters, looked valid, and failed at the lookup as "no room found".
        val typed = RoomCode.normalise("ABC-234").take(RoomCode.LENGTH)
        assertEquals("ABC234", typed)
        assertTrue(RoomCode.isValid(typed))
    }

    @Test
    fun `a look-alike character is named rather than blamed on the room`() {
        // Six characters long, so the old length-only check let it through to Firebase, which
        // answered "no room found for OO0011" - blaming the room for a typo.
        val problem = RoomCode.problemWith("OO0011")
        assertTrue(problem is RoomCode.Problem.Lookalike, "was $problem")
        assertEquals('O', problem.character)
        assertTrue(RoomCode.describe(problem).contains("O"))
    }

    @Test
    fun `every look-alike is caught - not only the first one in the string`() {
        for (character in RoomCode.LOOKALIKES) {
            val problem = RoomCode.problemWith("ABC${character}23")
            assertTrue(problem is RoomCode.Problem.Lookalike, "$character gave $problem")
            assertEquals(character, problem.character)
        }
    }

    @Test
    fun `a character that is not part of a code at all is reported as such`() {
        val problem = RoomCode.problemWith("ABC2#4")
        assertTrue(problem is RoomCode.Problem.NotInAlphabet, "was $problem")
        assertEquals('#', problem.character)
    }

    @Test
    fun `the wrong length is reported with the length`() {
        val short = RoomCode.problemWith("ABC")
        assertTrue(short is RoomCode.Problem.WrongLength, "was $short")
        assertEquals(3, short.actual)
        assertTrue(RoomCode.describe(short).contains("3"))

        val long = RoomCode.problemWith("ABC2345")
        assertTrue(long is RoomCode.Problem.WrongLength, "was $long")
        assertEquals(7, long.actual)
    }

    @Test
    fun `nothing typed yet is not reported as a wrong length`() {
        assertEquals(RoomCode.Problem.Empty, RoomCode.problemWith(""))
        assertEquals(RoomCode.Problem.Empty, RoomCode.problemWith("   "))
        assertEquals(RoomCode.Problem.Empty, RoomCode.problemWith("--"))
    }

    @Test
    fun `characters are judged before length so the message is the useful one`() {
        // "OO" is both too short and full of look-alikes. Telling the user about the length
        // teaches them nothing about why their code will never work.
        assertTrue(RoomCode.problemWith("OO") is RoomCode.Problem.Lookalike)
    }

    @Test
    fun `every problem has a sentence and none of them are empty`() {
        val problems = listOf(
            RoomCode.Problem.Empty,
            RoomCode.Problem.WrongLength(3),
            RoomCode.Problem.Lookalike('O'),
            RoomCode.Problem.NotInAlphabet('#'),
        )
        for (problem in problems) {
            val message = RoomCode.describe(problem)
            assertTrue(message.isNotBlank(), "$problem had no message")
            assertTrue(message.trim().endsWith("."), "\"$message\" should read as a sentence")
        }
    }
}
