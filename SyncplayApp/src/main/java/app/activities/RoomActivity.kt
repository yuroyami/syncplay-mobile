package app.activities

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import app.protocol.SyncplayProtocol
import app.utils.ExoUtils.buildExo
import app.wrappers.Constants
import com.google.android.exoplayer2.ExoPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class RoomActivity : ComponentActivity() {

    /* This will initialize our protocol the first time it is needed */
    lateinit var p: SyncplayProtocol

    /*-- Declaring ExoPlayer variable --*/
    var myExoPlayer: ExoPlayer? = null

    /**********************************************************************************************
     *                                  LIFECYCLE METHODS
     *********************************************************************************************/
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        /** Pref No.1 : Should the READY button be initially clicked ? **/
//        binding.syncplayReady.isChecked = sp.getBoolean("ready_firsthand", true).also {
//            p.ready = it /* Telling our protocol about our readiness */
//        }

        /** Fetching the TLS setting (whether the user wanna connect via TLS) */
        val tls = false /* sp.getBoolean("tls", false) */

        /** Now, let's connect to the server, everything should be ready **/
        if (p.channel == null) {
            /* If user has TLS on, we check for TLS from the server (Opportunistic TLS) */
            if (tls) {
                p.syncplayBroadcaster?.onTLSCheck()
                p.tls = Constants.TLS.TLS_ASK
            }
            p.connect()
        }

        /** Showing starter hint if it's not permanently disabled */
//        val dontShowHint = sp.getBoolean("dont_show_starter_hint", false)
//        if (!dontShowHint) {
//            showPopup(StarterHintPopup(this), true)
//        }


//        /** Attaching tooltip longclick listeners to all the buttons using an extensive method */
//        for (child in hudBinding.buttonRowOne.children) {
//            if (child is ImageButton) {
//                child.attachTooltip(child.contentDescription.toString())
//            }
//        }
    }

    override fun onStart() {
        super.onStart()
        lifecycleScope.launch(Dispatchers.Main) {
            /** We initialize ExoPlayer components here, right after onStart() and not onCreate() **/
            myExoPlayer = buildExo()
        }
    }
}

