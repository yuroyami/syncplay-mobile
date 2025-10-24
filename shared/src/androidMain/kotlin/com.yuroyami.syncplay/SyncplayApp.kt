package com.yuroyami.syncplay

import android.app.Application
import android.content.Context
import android.os.StrictMode
import com.yuroyami.syncplay.managers.datastore.DataStoreKeys
import com.yuroyami.syncplay.managers.datastore.datastore
import com.yuroyami.syncplay.utils.contextObtainer
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

        contextObtainer = ::returnAppContext

        //if (BuildConfig.DEBUG) enableStrictMode()
    }

    private fun returnAppContext(): Context {
        return applicationContext
    }

    private fun enableStrictMode() {
        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy.Builder()
                .detectAll() // Detect everything
                .penaltyLog() // Log violations to Logcat
                .penaltyFlashScreen() // Flash screen on violation (visual indicator)
                //.penaltyDeath() // Crash on violation (use carefully!)
                .build()
        )

        StrictMode.setVmPolicy(
            StrictMode.VmPolicy.Builder()
                .detectAll() // Detect all VM violations
                .penaltyLog() // Log violations
                .penaltyDeath() // Crash on violation (use carefully!)
                .build()
        )
    }
}