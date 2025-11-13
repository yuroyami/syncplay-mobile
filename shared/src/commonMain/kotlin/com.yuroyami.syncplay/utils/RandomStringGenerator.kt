package com.yuroyami.syncplay.utils

/**
 * Utility for generating random strings for various Syncplay security purposes.
 * This adheres to the original Syncplay implementation.
 *
 * Provides methods for creating room passwords and server salts with
 * specific formatting requirements.
 */
object RandomStringGenerator {

    /**
     * Generates a random room password in the format "XX-###-###".
     *
     * Format breakdown:
     * - 2 uppercase letters
     * - Hyphen separator
     * - 3 digits
     * - Hyphen separator
     * - 3 digits
     *
     * Example output: "AB-123-456"
     *
     * @return A formatted random password string
     */
    fun generateRoomPassword(): String {
        val parts = listOf(
            getRandomLetters(2),
            getRandomNumbers(3),
            getRandomNumbers(3)
        )
        return parts.joinToString("-")
    }

    /**
     * Generates a random 10-character alphabetic salt for server operations.
     *
     * Used for cryptographic operations and session security.
     *
     * Example output: "XKCDWQBPZM"
     *
     * @return A 10-character uppercase letter string
     */
    fun generateServerSalt(): String {
        return getRandomLetters(10)
    }

    /**
     * Generates a random string of uppercase letters.
     *
     * @param quantity The number of letters to generate
     * @return A string of random uppercase letters (A-Z)
     */
    private fun getRandomLetters(quantity: Int): String {
        val letters = ('A'..'Z')
        return (1..quantity)
            .map { letters.random() }
            .joinToString("")
    }

    /**
     * Generates a random string of digits.
     *
     * @param quantity The number of digits to generate
     * @return A string of random digits (0-9)
     */
    private fun getRandomNumbers(quantity: Int): String {
        val digits = ('0'..'9')
        return (1..quantity)
            .map { digits.random() }
            .joinToString("")
    }
}