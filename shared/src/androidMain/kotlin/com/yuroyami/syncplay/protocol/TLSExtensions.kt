package com.yuroyami.syncplay.protocol

import io.ktor.network.tls.TLSConfigBuilder


actual fun TLSConfigBuilder.configureKtorTLS() {
    this.serverName = "Syncplay"

}
