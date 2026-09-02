package com.example.myapplicationmusicsharing.data

import com.example.myapplicationmusicsharing.Constants

/**
 * Room codes: generating them, and judging what a human typed.
 *
 * Pulled out of [FirebaseRepository] and the lobby screen so both agree on one set of rules, and
 * so the rules can be tested without a Firebase project.
 *
 * ## Why a typo cannot be auto-corrected
 *
 * The alphabet excludes `0`, `O`, `1`, `I` and `L` so a code can be read aloud without confusion.
 * That is a deliberate trade: it costs a little entropy, and — unlike Crockford's Base32, where
 * only the letters are dropped so a typed `O` can only ever have meant `0` — this alphabet drops
 * *both* halves of each look-alike pair. There is therefore nothing to correct a typed `O` to.
 * The honest response is to point at the character, which is what [problemWith] does, rather than
 * silently querying for a room that cannot exist.
 */
object RoomCode {

    const val LENGTH = Constants.ROOM_CODE_LENGTH
    const val ALPHABET = Constants.ROOM_CODE_ALPHABET

    /** The pairs the alphabet deliberately avoids; typing one is always a mistake. */
    const val LOOKALIKES = "0O1IL"

    /** What is wrong with what the user typed, or null when nothing is. */
    sealed interface Problem {
        data object Empty : Problem
        data class WrongLength(val actual: Int) : Problem
        data class Lookalike(val character: Char) : Problem
        data class NotInAlphabet(val character: Char) : Problem
    }

    /** Generates a code from a source of randomness the caller controls, so tests are stable. */
    fun generate(nextInt: (bound: Int) -> Int): String =
        buildString { repeat(LENGTH) { append(ALPHABET[nextInt(ALPHABET.length)]) } }

    /**
     * Cleans up what was typed or pasted: upper-cases it and drops the separators people add when
     * reading a code out ("abc 234", "ABC-234"). Everything else is kept, so [problemWith] can
     * name the character that is actually wrong instead of quietly deleting it.
     */
    fun normalise(input: String): String = buildString {
        for (character in input.trim().uppercase()) {
            when (character) {
                ' ', '\u00A0', '-', '\u2013', '_' -> Unit
                else -> append(character)
            }
        }
    }

    /**
     * Judges a normalised code. Characters are checked before length, because "OOOOOO" is six
     * characters long and telling the user about the length teaches them nothing.
     */
    fun problemWith(input: String): Problem? {
        val code = normalise(input)
        if (code.isEmpty()) return Problem.Empty
        for (character in code) {
            if (character in LOOKALIKES) return Problem.Lookalike(character)
            if (character !in ALPHABET) return Problem.NotInAlphabet(character)
        }
        if (code.length != LENGTH) return Problem.WrongLength(code.length)
        return null
    }

    fun isValid(input: String): Boolean = problemWith(input) == null

    /** The sentence to show. Lives here so the wording is covered by the same tests as the rules. */
    fun describe(problem: Problem): String = when (problem) {
        Problem.Empty -> "Enter the $LENGTH-character room code."
        is Problem.WrongLength ->
            "Room codes are $LENGTH characters — that one has ${problem.actual}."
        is Problem.Lookalike ->
            "Room codes never use O, 0, I, 1 or L, because they are too easy to mix up when read " +
                "out. Check the \"${problem.character}\"."
        is Problem.NotInAlphabet -> "\"${problem.character}\" is not part of a room code."
    }
}
