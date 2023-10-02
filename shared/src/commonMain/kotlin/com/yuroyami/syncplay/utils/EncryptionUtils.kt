package com.yuroyami.syncplay.utils

import io.ktor.utils.io.charsets.Charsets
import io.ktor.utils.io.core.toByteArray
import org.kotlincrypto.hash.md.MD5

/** Syncplay servers accept passwords in the form of MD5 hashes digested in hexadecimal
 * TODO: Replace with Okio  **/
fun md5(str: String): ByteArray {
    return MD5().digest(str.toByteArray(Charsets.UTF_8))
}

/** Hex Digester for hashers **/
fun ByteArray.toHex(): String {
    //return joinToString(separator = "") { byte -> "%02x".format(byte) }

    val hexChars = "0123456789ABCDEF".toCharArray()
    val result = CharArray(size * 2)
    var index = 0
    for (byte in this) {
        val value = byte.toInt() and 0xFF
        result[index++] = hexChars[value shr 4]
        result[index++] = hexChars[value and 0x0F]
    }
    return result.concatToString()
}