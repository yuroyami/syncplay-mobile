package app.utils

import android.content.Intent
import android.os.Build
import android.view.View
import android.widget.ImageButton
import androidx.appcompat.view.ContextThemeWrapper
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.isVisible
import app.R
import app.controllers.activity.RoomActivity
import app.protocol.JsonSender
import app.utils.ExoPlayerUtils.audioSelect
import app.utils.ExoPlayerUtils.subtitleSelect
import app.utils.RoomUtils.sendMessage
import app.utils.UIUtils.applyUISettings
import app.utils.UIUtils.displayInfo
import app.utils.UIUtils.hideKb
import app.utils.UIUtils.insertPopup
import app.utils.UIUtils.replenishUsers
import app.utils.UIUtils.showPopup
import app.wrappers.Constants
import app.wrappers.Constants.POPUP.POPUP_INROOM_SETTINGS
import app.wrappers.Constants.POPUP.POPUP_MESSAGE_HISTORY
import com.google.android.exoplayer2.ui.AspectRatioFrameLayout

/** Our RoomActivity contains a LOT of click listeners, why not just bundle them all here ? */
object ListenerUtils {

    fun RoomActivity.initListeners() {
        /************************
         * Adding a  Video File *
         ************************/
        hudBinding.syncplayAddfile.setOnClickListener {
            val ctx = ContextThemeWrapper(this, R.style.MenuStyle)

            val popup = PopupMenu(ctx, hudBinding.syncplayAddfile)

            val offlineItem = popup.menu.add(0, 0, 0, getString(R.string.room_addmedia_offline))

            val onlineItem = popup.menu.add(0, 1, 1, getString(R.string.room_addmedia_online))

            popup.setOnMenuItemClickListener {
                when (it) {
                    offlineItem -> {
                        val intent1 = Intent()
                        intent1.type = "video/*"
                        intent1.action = Intent.ACTION_OPEN_DOCUMENT
                        val intentWrapper = Intent.createChooser(intent1, "Select a video with")
                        videoPickResult.launch(intentWrapper)
                    }

                    onlineItem -> {
                        showPopup(urlPopup, true)
                    }
                }
                return@setOnMenuItemClickListener true
            }
            popup.show()
        }

        /** Clicking on the player hides the keyboard, and loses focus from the message box **/
        binding.vidplayer.setOnClickListener {
            hideKb()
            binding.syncplayINPUTBox.clearFocus()
        }


        /**************************
         * Message Focus Listener *
         **************************/
        binding.syncplayINPUTBox.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                binding.syncplayINPUT.clearAnimation()
                binding.syncplayINPUT.alpha = 1f
                binding.syncplaySEND.clearAnimation()
                binding.syncplaySEND.alpha = 1f
                binding.syncplayINPUTBox.clearAnimation()
                binding.syncplayINPUTBox.alpha = 1f
            }
        }

        /*****************
         * Send Messages *
         *****************/
        binding.syncplaySEND.setOnClickListener {
            val msg: String = binding.syncplayINPUTBox.text.toString()
                .also {
                    it.replace("\\", "")
                    if (it.length > 150) it.substring(0, 149)
                }
            if (msg.trim().isNotEmpty()) {
                sendMessage(msg)
                binding.syncplayINPUT.isErrorEnabled = false
            } else {
                binding.syncplayINPUT.isErrorEnabled = true
                binding.syncplayINPUT.error =
                    getString(R.string.room_empty_message_error)
            }
        }

        /*******************
         * Subtitles track *
         *******************/
        hudBinding.exoSubtitle.setOnClickListener {
            if (myExoPlayer?.mediaItemCount != 0) {
                subtitleSelect(it as ImageButton)
            }
        }

        /***************
         * Audio Track *
         ***************/
        hudBinding.exoAudioTrack.setOnClickListener {
            if (myExoPlayer?.mediaItemCount != 0) {
                audioSelect(it as ImageButton)
            }
        }

        /***************
         * Lock Screen *
         ***************/
        hudBinding.syncplayLock.setOnClickListener {
            if (lockedScreen) {
                lockedScreen = false
            } else {
                lockedScreen = true
                runOnUiThread {
                    binding.syncplayVisiblitydelegate.visibility = View.GONE
                    binding.vidplayer.controllerHideOnTouch = false
                    binding.vidplayer.controllerAutoShow = false
                    binding.vidplayer.hideController()
                    binding.syncplayerLockoverlay.visibility = View.VISIBLE
                    binding.syncplayerLockoverlay.isFocusable = true
                    binding.syncplayerLockoverlay.isClickable = true
                    binding.syncplayerUnlock.visibility = View.VISIBLE
                    binding.syncplayerUnlock.isFocusable = true
                    binding.syncplayerUnlock.isClickable = true
                }
            }
        }

        binding.syncplayerUnlock.setOnClickListener {
            lockedScreen = false
            runOnUiThread {
                binding.vidplayer.controllerHideOnTouch = true
                binding.vidplayer.showController()
                binding.vidplayer.controllerAutoShow = true
                binding.syncplayerLockoverlay.visibility = View.GONE
                binding.syncplayerLockoverlay.isFocusable = false
                binding.syncplayerLockoverlay.isClickable = false
                binding.syncplayerUnlock.visibility = View.GONE
                binding.syncplayerUnlock.isFocusable = false
                binding.syncplayerUnlock.isClickable = false
            }
        }

        binding.syncplayerLockoverlay.setOnClickListener {
            binding.syncplayerUnlock.also {
                it.alpha = if (it.alpha == 0.35f) 0.05f else 0.35f
            }
        }

        /*****************
         * OverFlow Menu *
         *****************/
        hudBinding.syncplayMore.setOnClickListener { _ ->
            val ctx = ContextThemeWrapper(this, R.style.MenuStyle)
            val popup = PopupMenu(ctx, hudBinding.syncplayAddfile)

            val loadsubItem = popup.menu.add(0, 0, 0, getString(R.string.room_overflow_sub))

            val cutoutItem = popup.menu.add(0, 1, 1, getString(R.string.room_overflow_cutout))
            cutoutItem.isCheckable = true
            cutoutItem.isChecked = cutOutMode
            cutoutItem.isEnabled = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)

            val seekbuttonsItem = popup.menu.add(0, 2, 2, getString(R.string.room_overflow_ff))
            seekbuttonsItem.isCheckable = true
            val ffwdButton = hudBinding.exoFfwd
            val rwndButton = hudBinding.exoRew
            seekbuttonsItem.isChecked = seekButtonEnable != false

            val messagesItem =
                popup.menu.add(0, 3, 3, getString(R.string.room_overflow_msghistory))

            val uiItem = popup.menu.add(0, 4, 4, getString(R.string.room_overflow_settings))

            //val adjustItem = popup.menu.add(0,5,5,"Change Username or Room")

            popup.setOnMenuItemClickListener {
                when (it) {
                    loadsubItem -> {
                        val intent2 = Intent()
                        intent2.type = "*/*"
                        intent2.action = Intent.ACTION_OPEN_DOCUMENT
                        subtitlePickResult.launch(intent2)
                    }

                    cutoutItem -> {
                        cutOutMode = !cutOutMode
                        MiscUtils.cutoutMode(cutOutMode, window)
                        binding.vidplayer.performClick()
                        binding.vidplayer.performClick() /* Double click to apply cut-out */
                    }

                    seekbuttonsItem -> {
                        if (seekButtonEnable == true || seekButtonEnable == null) {
                            seekButtonEnable = false
                            ffwdButton.visibility = View.GONE
                            rwndButton.visibility = View.GONE
                        } else {
                            seekButtonEnable = true
                            ffwdButton.visibility = View.VISIBLE
                            rwndButton.visibility = View.VISIBLE
                        }
                    }

                    messagesItem -> {
                        insertPopup(POPUP_MESSAGE_HISTORY)
                    }

                    uiItem -> {
                        insertPopup(POPUP_INROOM_SETTINGS)
                    }
                }
                return@setOnMenuItemClickListener true
            }

            popup.show()
        }

        /********************
         * Room Information *
         ********************/
        binding.syncplayOverviewcheckbox.setOnCheckedChangeListener { _, checked ->
            binding.syncplayOverviewCard.isVisible = checked
            if (checked) {
                replenishUsers(binding.syncplayOverview)
            }
        }

        /****************
         * Ready Button *
         ****************/
        binding.syncplayReady.setOnCheckedChangeListener { _, b ->
            p.ready = b
            p.sendPacket(JsonSender.sendReadiness(b, true))
        }

        /*******************
         * Change Fit Mode *
         *******************/
        hudBinding.syncplayScreen.setOnClickListener {
            val currentresolution = binding.vidplayer.resizeMode
            val resolutions = mutableMapOf<Int, String>()
            resolutions[AspectRatioFrameLayout.RESIZE_MODE_FIT] =
                getString(R.string.room_scaling_fit_screen)
            resolutions[AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH] =
                getString(R.string.room_scaling_fixed_width)
            resolutions[AspectRatioFrameLayout.RESIZE_MODE_FIXED_HEIGHT] =
                getString(R.string.room_scaling_fixed_height)
            resolutions[AspectRatioFrameLayout.RESIZE_MODE_FILL] =
                getString(R.string.room_scaling_fill_screen)
            resolutions[AspectRatioFrameLayout.RESIZE_MODE_ZOOM] =
                getString(R.string.room_scaling_zoom)

            val nextRes = currentresolution + 1
            if (nextRes == 5) {
                binding.vidplayer.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                displayInfo(resolutions[0]!!)
            } else {
                binding.vidplayer.resizeMode = nextRes
                displayInfo(resolutions[nextRes]!!)
            }

            binding.vidplayer.performClick()
            binding.vidplayer.performClick()
        }

        /** Shared Playlist */
        hudBinding.syncplaySharedPlaylist.setOnClickListener {
            insertPopup(Constants.POPUP.POPUP_SHARED_PLAYLIST)
        }

        /** Pseudo Popup dismissal **/
        binding.pseudoPopupDismisser.setOnClickListener {
            binding.pseudoPopupParent.visibility = View.GONE
            when (activePseudoPopup) {
                Constants.POPUP.POPUP_INROOM_SETTINGS -> applyUISettings()
                else -> {}
            }

            activePseudoPopup = null
        }
    }
}