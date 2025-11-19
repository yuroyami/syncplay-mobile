package com.yuroyami.syncplay.utils

import platform.Foundation.NSURL

inline fun <T> NSURL.accessSecurely(block: NSURL.() -> T): T {
    return try {
        startAccessingSecurityScopedResource()
        block(this)
    } finally {
        stopAccessingSecurityScopedResource()
    }
}