package app.home

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.getAndUpdate

/**
 * A join that came from outside the join form: an invite link, a launcher shortcut, a Quick
 * Action, the desktop command line or the web address bar. Home joins it when it appears, and at
 * once when Home already shows. The join goes through the same checks as a join from the form.
 */
object PendingJoin {

    val waiting: StateFlow<JoinConfig?>
        field = MutableStateFlow(null)

    fun post(config: JoinConfig) {
        waiting.value = config
    }

    /** Takes the waiting join, so that it runs once. */
    fun take(): JoinConfig? = waiting.getAndUpdate { null }
}
