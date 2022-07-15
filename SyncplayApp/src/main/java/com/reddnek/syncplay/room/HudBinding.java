package com.chromaticnoob.syncplay.room;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.viewbinding.ViewBinding;
import androidx.viewbinding.ViewBindings;

import com.chromaticnoob.syncplay.R;

/**
 * Android Studio auto-generates such classes for any layout file you have if you turn on ViewBinding
 * However, it really fails to deliver sometimes. We have a view in the layout that becomes null as
 * soon as we instantiate the RoomActivity, so binding our HUD (Player's Controller) layout with
 * ViewBinding ends up throwing NullPointerException. I had to edit the generated class (this one)
 * in order to get rid of that problem.
 */
public final class HudBinding implements ViewBinding {
    @NonNull
    public final ImageButton syncplaySharedPlaylist;

    @NonNull
    public final ImageButton exoAudioTrack;

    @NonNull
    public final TextView exoDuration;

    @NonNull
    public final ImageButton exoFfwd;

    @NonNull
    public final ImageButton exoPause;

    @NonNull
    public final ImageButton exoPlay;

    @NonNull
    public final TextView exoPosition;

    public final View exoProgressPlaceholder;

    @NonNull
    public final ImageButton exoRepeatToggle;

    @NonNull
    public final ImageButton exoRew;

    @NonNull
    public final ImageButton exoSubtitle;

    @NonNull
    public final ImageButton syncplayAddfile;

    @NonNull
    public final ImageButton syncplayLock;

    @NonNull
    public final ImageButton syncplayMore;

    @NonNull
    public final ImageButton syncplayScreen;
    @NonNull
    public final LinearLayout vidplayerhud;
    @NonNull
    private final LinearLayout rootView;

    private HudBinding(@NonNull LinearLayout rootView, @NonNull ImageButton exoAudioTrack,
                       @NonNull TextView exoDuration, @NonNull ImageButton exoFfwd, @NonNull ImageButton exoPause,
                       @NonNull ImageButton exoPlay, @NonNull TextView exoPosition,
                       View exoProgressPlaceholder, @NonNull ImageButton exoRepeatToggle,
                       @NonNull ImageButton exoRew, @NonNull ImageButton exoSubtitle,
                       @NonNull ImageButton syncplayAddfile, @NonNull ImageButton syncplayLock,
                       @NonNull ImageButton syncplayMore, @NonNull ImageButton syncplayScreen,
                       @NonNull ImageButton syncplaySharedPlaylist, @NonNull LinearLayout vidplayerhud) {
        this.rootView = rootView;
        this.exoAudioTrack = exoAudioTrack;
        this.exoDuration = exoDuration;
        this.exoFfwd = exoFfwd;
        this.exoPause = exoPause;
        this.exoPlay = exoPlay;
        this.exoPosition = exoPosition;
        this.exoProgressPlaceholder = exoProgressPlaceholder;
        this.exoRepeatToggle = exoRepeatToggle;
        this.exoRew = exoRew;
        this.exoSubtitle = exoSubtitle;
        this.syncplayAddfile = syncplayAddfile;
        this.syncplayLock = syncplayLock;
        this.syncplayMore = syncplayMore;
        this.syncplayScreen = syncplayScreen;
        this.syncplaySharedPlaylist = syncplaySharedPlaylist;
        this.vidplayerhud = vidplayerhud;
    }

    @NonNull
    public static HudBinding bind(@NonNull View rootView) {
        // The body of this method is generated in a way you would not otherwise write.
        // This is done to optimize the compiled bytecode for size and performance.
        int id;
        missingId:
        {
            id = com.google.android.exoplayer2.R.id.exo_audio_track;
            ImageButton exoAudioTrack = ViewBindings.findChildViewById(rootView, id);
            if (exoAudioTrack == null) {
                break missingId;
            }

            id = com.google.android.exoplayer2.R.id.exo_duration;
            TextView exoDuration = ViewBindings.findChildViewById(rootView, id);
            if (exoDuration == null) {
                break missingId;
            }

            id = com.google.android.exoplayer2.R.id.exo_ffwd;
            ImageButton exoFfwd = ViewBindings.findChildViewById(rootView, id);
            if (exoFfwd == null) {
                break missingId;
            }

            id = com.google.android.exoplayer2.R.id.exo_pause;
            ImageButton exoPause = ViewBindings.findChildViewById(rootView, id);
            if (exoPause == null) {
                break missingId;
            }

            id = com.google.android.exoplayer2.R.id.exo_play;
            ImageButton exoPlay = ViewBindings.findChildViewById(rootView, id);
            if (exoPlay == null) {
                break missingId;
            }

            id = com.google.android.exoplayer2.R.id.exo_position;
            TextView exoPosition = ViewBindings.findChildViewById(rootView, id);
            if (exoPosition == null) {
                break missingId;
            }

            id = com.google.android.exoplayer2.R.id.exo_progress_placeholder;
            View exoProgressPlaceholder = ViewBindings.findChildViewById(rootView, id);

            id = com.google.android.exoplayer2.R.id.exo_repeat_toggle;
            ImageButton exoRepeatToggle = ViewBindings.findChildViewById(rootView, id);
            if (exoRepeatToggle == null) {
                break missingId;
            }

            id = com.google.android.exoplayer2.R.id.exo_rew;
            ImageButton exoRew = ViewBindings.findChildViewById(rootView, id);
            if (exoRew == null) {
                break missingId;
            }

            id = com.google.android.exoplayer2.R.id.exo_subtitle;
            ImageButton exoSubtitle = ViewBindings.findChildViewById(rootView, id);
            if (exoSubtitle == null) {
                break missingId;
            }

            id = R.id.syncplay_addfile;
            ImageButton syncplayAddfile = ViewBindings.findChildViewById(rootView, id);
            if (syncplayAddfile == null) {
                break missingId;
            }

            id = R.id.syncplay_lock;
            ImageButton syncplayLock = ViewBindings.findChildViewById(rootView, id);
            if (syncplayLock == null) {
                break missingId;
            }

            id = R.id.syncplay_more;
            ImageButton syncplayMore = ViewBindings.findChildViewById(rootView, id);
            if (syncplayMore == null) {
                break missingId;
            }

            id = R.id.syncplay_screen;
            ImageButton syncplayScreen = ViewBindings.findChildViewById(rootView, id);
            if (syncplayScreen == null) {
                break missingId;
            }

            id = R.id.syncplay_shared_playlist;
            ImageButton syncplaySharedPlaylist = ViewBindings.findChildViewById(rootView, id);
            if (syncplayScreen == null) {
                break missingId;
            }

            LinearLayout vidplayerhud = (LinearLayout) rootView;

            return new HudBinding((LinearLayout) rootView, exoAudioTrack, exoDuration, exoFfwd, exoPause,
                    exoPlay, exoPosition, exoProgressPlaceholder, exoRepeatToggle, exoRew, exoSubtitle,
                    syncplayAddfile, syncplayLock, syncplayMore, syncplayScreen, syncplaySharedPlaylist,
                    vidplayerhud);
        }
        String missingId = rootView.getResources().getResourceName(id);
        throw new NullPointerException("Missing required view with ID: ".concat(missingId));
    }

    @NonNull
    public static HudBinding inflate(@NonNull LayoutInflater inflater) {
        return inflate(inflater, null, false);
    }

    @NonNull
    public static HudBinding inflate(@NonNull LayoutInflater inflater, @Nullable ViewGroup parent,
                                     boolean attachToParent) {
        View root = inflater.inflate(R.layout.hud, parent, false);
        if (attachToParent) {
            parent.addView(root);
        }
        return bind(root);
    }

    @Override
    @NonNull
    public LinearLayout getRoot() {
        return rootView;
    }
}
