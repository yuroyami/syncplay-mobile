package com.yuroyami.syncplay

import android.app.Application
import com.yuroyami.syncplay.settings.DataStoreKeys
import com.yuroyami.syncplay.settings.datastore
import com.yuroyami.syncplay.utils.dataStore
import org.conscrypt.Conscrypt
import java.security.Security

class SyncplayApp: Application() {

    override fun onCreate() {
        super.onCreate()

        /* TLS (mainly TLSv1.3) support, via Conscrypt */
        Security.insertProviderAt(Conscrypt.newProvider(), 1)

        //Initializing datastore
        datastore = dataStore(applicationContext, DataStoreKeys.SYNCPLAY_PREFS)
    }
}