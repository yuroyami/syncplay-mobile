package com.yuroyami.syncplay.utils

object RandomStringGenerator {
    fun generateRoomPassword(): String {
        val parts = listOf(
            getRandomLetters(2),
            getRandomNumbers(3),
            getRandomNumbers(3)
        )
        return parts.joinToString("-")
    }

    fun generateServerSalt(): String {
        return getRandomLetters(10)
    }

    private fun getRandomLetters(quantity: Int): String {
        val letters = ('A'..'Z')
        return (1..quantity)
            .map { letters.random() }
            .joinToString("")
    }

    private fun getRandomNumbers(quantity: Int): String {
        val digits = ('0'..'9')
        return (1..quantity)
            .map { digits.random() }
            .joinToString("")
    }
}
