package com.yuroyami.syncplay.utils

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.MemScope
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.refTo
import kotlinx.cinterop.usePinned
import platform.posix.*
import platform.darwin.*

@OptIn(ExperimentalForeignApi::class)
fun pingIcmpIOS(host: String, packet: Int): Int? {
    return null
}
