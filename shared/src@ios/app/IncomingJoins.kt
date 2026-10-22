package app

import app.home.InviteLink
import app.home.JoinConfig
import app.home.PendingJoin
import app.utils.loggy
import kotlinx.serialization.json.Json

/*
 * iOS delivers an opened link and a Quick Action to the app's scene, not to the app delegate.
 * The Swift entry point (iOSApp.swift) receives both and hands them over here. This file holds
 * nothing else, so a call from Swift does not start any other Kotlin setup.
 */

/** An invite link that iOS opened the app with. False when it is not a join link. */
fun onIncomingLink(url: String): Boolean {
    val parsed = InviteLink.parse(url) ?: return false
    PendingJoin.post(parsed)
    return true
}

/** A Quick Action from the Home Screen. Its [type] holds the join details as JSON. */
fun onQuickAction(type: String) {
    // Log only the arrival: the type string is the whole join config, both passwords included.
    loggy("Quick Action shortcut received")
    runCatching { Json.decodeFromString<JoinConfig>(type) }.getOrNull()?.let(PendingJoin::post)
}
