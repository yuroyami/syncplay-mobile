#pragma once

#import <VLCKit/VLCKit.h>
#import <VLCKit/vlc/vlc.h>
#import <VLCKit/VLCLibVLCBridging.h>

typedef NS_ENUM(NSInteger, SyncplayVlcInputState) {
    SyncplayVlcInputStateInactive,
    SyncplayVlcInputStateOpening,
    SyncplayVlcInputStateReady,
    SyncplayVlcInputStateUnseekable,
    SyncplayVlcInputStateFailed,
};

/** Reads the native input state, not VLCKit's state property, which updates asynchronously. */
static inline SyncplayVlcInputState SyncplayVlcReadInputState(VLCMediaPlayer *player) {
    if (player == nil || player.media == nil) return SyncplayVlcInputStateInactive;
    libvlc_media_player_t *nativePlayer = (libvlc_media_player_t *)player.libVLCMediaPlayer;
    if (nativePlayer == NULL) return SyncplayVlcInputStateInactive;
    libvlc_state_t state = libvlc_media_player_get_state(nativePlayer);
    if (state == libvlc_Error) return SyncplayVlcInputStateFailed;
    if (libvlc_media_player_is_seekable(nativePlayer)) return SyncplayVlcInputStateReady;
    if (state == libvlc_Opening) return SyncplayVlcInputStateOpening;
    if (state == libvlc_Playing || state == libvlc_Paused) return SyncplayVlcInputStateUnseekable;
    return SyncplayVlcInputStateInactive;
}

/**
 * The native media length in microseconds, or -1 without media. libVLC returns 0, not the -1 that
 * its header promises, while no input is open. Convert with vlcMillisFromMicros.
 */
static inline int64_t SyncplayVlcLengthUs(VLCMediaPlayer *player) {
    if (player == nil || player.media == nil) return -1;
    libvlc_media_player_t *nativePlayer = (libvlc_media_player_t *)player.libVLCMediaPlayer;
    return nativePlayer == NULL ? -1 : libvlc_media_player_get_length(nativePlayer);
}

static inline int SyncplayVlcCurrentState(VLCMediaPlayer *player) {
    if (player == nil || player.media == nil) return libvlc_NothingSpecial;
    libvlc_media_player_t *nativePlayer = (libvlc_media_player_t *)player.libVLCMediaPlayer;
    return nativePlayer == NULL ? libvlc_NothingSpecial : libvlc_media_player_get_state(nativePlayer);
}

/** Delayed wrapper notifications may describe a state the native player has already left. */
static inline bool SyncplayVlcStateMatches(VLCMediaPlayer *player, VLCMediaPlayerState eventState) {
    int state = SyncplayVlcCurrentState(player);
    switch (eventState) {
        case VLCMediaPlayerStateNothingSpecial: return state == libvlc_NothingSpecial;
        case VLCMediaPlayerStateOpening: return state == libvlc_Opening;
        case VLCMediaPlayerStatePlaying: return state == libvlc_Playing;
        case VLCMediaPlayerStatePaused: return state == libvlc_Paused;
        case VLCMediaPlayerStateStopped: return state == libvlc_Stopped;
        case VLCMediaPlayerStateStopping: return state == libvlc_Stopping;
        case VLCMediaPlayerStateError: return state == libvlc_Error;
    }
    return false;
}

/**
 * Compares native descriptors, because VLCKit may replace its Objective-C wrapper for the same
 * media. libVLC returns the media of the input that is open, so after a media change this stays
 * false until the old input has stopped. The call holds a reference, released here.
 */
static inline bool SyncplayVlcHasCurrentMedia(VLCMediaPlayer *player, VLCMedia *media) {
    if (player == nil || media == nil) return false;
    libvlc_media_player_t *nativePlayer = (libvlc_media_player_t *)player.libVLCMediaPlayer;
    if (nativePlayer == NULL) return false;
    libvlc_media_t *current = libvlc_media_player_get_media(nativePlayer);
    bool matches = current != NULL && current == (libvlc_media_t *)media.libVLCMediaDescriptor;
    if (current != NULL) libvlc_media_release(current);
    return matches;
}

/**
 * The native playback time in microseconds, or -1 when there is no running input. VLCKit's
 * `time` property reads a notification and interpolation cache, which can stop updating while
 * playback continues. So read the native player instead, on the main thread and outside libVLC
 * callbacks. The handle is borrowed through VLCKit's bridging header, and the wrapper owns it, so
 * never release it. Convert with vlcMillisFromMicros.
 */
static inline int64_t SyncplayVlcTimeUs(VLCMediaPlayer *player) {
    if (player == nil || player.media == nil) return -1;
    libvlc_media_player_t *nativePlayer = (libvlc_media_player_t *)player.libVLCMediaPlayer;
    if (nativePlayer == NULL) return -1;
    int64_t time = libvlc_media_player_get_time(nativePlayer);
    // libVLC returns zero after its native input is gone. Read the native state after the clock,
    // so a stop that races this read cannot publish a false rewind. VLCKit's cached state can
    // still say Playing until its main-thread event arrives.
    switch (libvlc_media_player_get_state(nativePlayer)) {
        case libvlc_NothingSpecial:
        case libvlc_Stopped:
        case libvlc_Stopping:
        case libvlc_Error:
            return -1;
        default:
            return time; // Zero is valid for a live input, including a paused seek to zero.
    }
}
