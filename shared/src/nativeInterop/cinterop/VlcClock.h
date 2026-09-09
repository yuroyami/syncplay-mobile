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

/** Read native capability, not VLCKit's asynchronously updated state property. */
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

static inline int64_t SyncplayVlcNativeLengthMs(VLCMediaPlayer *player) {
    if (player == nil || player.media == nil) return -1;
    libvlc_media_player_t *nativePlayer = (libvlc_media_player_t *)player.libVLCMediaPlayer;
    return nativePlayer == NULL ? -1 : libvlc_media_player_get_length(nativePlayer);
}

static inline int64_t SyncplayVlcCurrentLengthMs(VLCMediaPlayer *player) {
    return SyncplayVlcNativeLengthMs(player);
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
        case VLCMediaPlayerStatePlaying: return state == libvlc_Playing;
        case VLCMediaPlayerStatePaused: return state == libvlc_Paused;
        case VLCMediaPlayerStateStopped: return state == libvlc_Stopped;
        case VLCMediaPlayerStateStopping: return state == libvlc_Stopping;
        case VLCMediaPlayerStateError: return state == libvlc_Error;
        case VLCMediaPlayerStateOpening: return state == libvlc_Opening;
        case VLCMediaPlayerStateBuffering: return state == libvlc_Buffering;
    }
    return false;
}

/** Compare native descriptors; VLCKit may replace its Objective-C wrapper for the same media. */
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
 * VLCKit 4.0.0a19's `time` property reads its notification/interpolation cache. That cache
 * can stop updating while playback continues. Read the native player instead, on Main and
 * outside libVLC callbacks. The wrapper owns the handle borrowed through its bridging header.
 *
 * Version-sensitive: this bundled libVLC API returns milliseconds. Recheck the headers and
 * bridge when upgrading VLCKit; newer VLC versions changed the native time units.
 */
static inline int64_t SyncplayVlcCurrentTimeMs(VLCMediaPlayer *player) {
    if (player == nil || player.media == nil) return -1;
    libvlc_media_player_t *nativePlayer = (libvlc_media_player_t *)player.libVLCMediaPlayer;
    if (nativePlayer == NULL) return -1;
    int64_t time = libvlc_media_player_get_time(nativePlayer);
    // This bundle returns zero after its native input is gone. Read the authoritative
    // state AFTER the clock so a stop racing this read cannot publish a fake rewind.
    // VLCKit's async cached state may still say Playing until its Main event arrives.
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
