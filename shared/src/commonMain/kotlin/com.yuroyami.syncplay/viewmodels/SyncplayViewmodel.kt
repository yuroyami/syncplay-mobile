package com.yuroyami.syncplay.viewmodels

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import com.yuroyami.syncplay.managers.ThemeManager
import com.yuroyami.syncplay.ui.screens.Screen
import com.yuroyami.syncplay.utils.WeakRef

/**
 * Application-level ViewModel for managing global Syncplay state.
 *
 * Maintains app-wide settings, theme configuration, and tracks high-level
 * user interaction state that persists across screen navigation.
 */
class SyncplayViewmodel : ViewModel() {

    /** The navigation backstack */
    val backstack = mutableStateListOf<Screen>(Screen.Home)

    /**
     * Whether the shared playlist feature is enabled.
     *
     * When disabled, all shared playlist functionality should be hidden/disabled
     * throughout the UI, and this state should be communicated to the server.
     *
     * TODO: Advertise this to server, disable shared-playlist-related functionality everywhere when this is off
     */
    val isSharedPlaylistEnabled = mutableStateOf(true)

    /**
     * Manages application theme and appearance settings.
     * Lazily initialized when first accessed.
     */
    val themeManager: ThemeManager by lazy { ThemeManager(this) }

    /**
     * Tracks whether the user has entered a room at least once during this app session.
     *
     * Used for UI/UX decisions like adjusting first-time behavior.
     */
    var hasEnteredRoomOnce = false

    /** Weak References for our child viewmodels so we can access them from SyncplayActivity
     * but also not prevent them from getting garbage-collected when they're finalized (invalidated and cleared)
     */
    var homeWeakRef: WeakRef<HomeViewmodel>? = null
    var roomWeakRef: WeakRef<RoomViewmodel>? = null
}