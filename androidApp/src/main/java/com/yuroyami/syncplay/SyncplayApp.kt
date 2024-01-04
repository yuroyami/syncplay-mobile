package com.yuroyami.syncplay

import android.app.Application
import com.yuroyami.syncplay.datastore.DataStoreKeys
import com.yuroyami.syncplay.datastore.datastore
import com.yuroyami.syncplay.utils.dataStore
import org.conscrypt.Conscrypt
import java.security.Security

class SyncplayApp: Application() {

    override fun onCreate() {
        super.onCreate()

        Security.insertProviderAt(Conscrypt.newProvider(), 1)

        //Initializing datastore
        datastore = dataStore(applicationContext, DataStoreKeys.SYNCPLAY_PREFS)
    }
}