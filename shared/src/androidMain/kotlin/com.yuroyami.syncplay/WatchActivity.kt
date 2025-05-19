package com.yuroyami.syncplay

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.app.PendingIntent.FLAG_IMMUTABLE
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.graphics.drawable.Icon
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.RequiresApi
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.C.STREAM_TYPE_MUSIC
import com.yuroyami.syncplay.player.BasePlayer
import com.yuroyami.syncplay.player.BasePlayer.ENGINE
import com.yuroyami.syncplay.player.PlayerUtils.pausePlayback
import com.yuroyami.syncplay.player.PlayerUtils.playPlayback
import com.yuroyami.syncplay.player.exo.ExoPlayer
import com.yuroyami.syncplay.player.mpv.MpvPlayer
import com.yuroyami.syncplay.player.mpv.mpvRoomSettings
import com.yuroyami.syncplay.player.vlc.VlcPlayer
import com.yuroyami.syncplay.settings.DataStoreKeys
import com.yuroyami.syncplay.settings.DataStoreKeys.PREF_INROOM_PLAYER_SUBTITLE_SIZE
import com.yuroyami.syncplay.settings.SettingObtainerCallback
import com.yuroyami.syncplay.settings.obtainerCallback
import com.yuroyami.syncplay.settings.valueBlockingly
import com.yuroyami.syncplay.settings.valueSuspendingly
import com.yuroyami.syncplay.utils.UIUtils.cutoutMode
import com.yuroyami.syncplay.utils.UIUtils.hideSystemUI
import com.yuroyami.syncplay.utils.bindWatchdog
import com.yuroyami.syncplay.utils.changeLanguage
import com.yuroyami.syncplay.utils.defaultEngineAndroid
import com.yuroyami.syncplay.utils.loggy
import com.yuroyami.syncplay.screens.room.GestureCallback
import com.yuroyami.syncplay.screens.room.RoomCallback
import com.yuroyami.syncplay.screens.room.RoomUI
import com.yuroyami.syncplay.screens.room.gestureCallback
import com.yuroyami.syncplay.watchroom.isSoloMode
import com.yuroyami.syncplay.watchroom.viewmodel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Suppress("deprecation")
class WatchActivity : ComponentActivity() {


}