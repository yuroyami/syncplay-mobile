package app.protocol.replay

import app.protocol.SessionTap
import app.protocol.sync.SyncContext
import app.protocol.sync.SyncOutcome
import app.protocol.sync.SyncState
import app.protocol.wire.PlaystateData
import java.io.File

/**
 * Keeps one client's session in order: every line in and out, redacted, and every sync decision.
 * The decoded form of each line from the server is taken when it is recorded, so the replay can
 * tell when the decoder changes its mind.
 */
class SessionRecorder(private val redactor: Redactor) : SessionTap {

    private val records = mutableListOf<SessionRecord>()

    override fun line(inbound: Boolean, line: String) {
        val clean = redactor.redact(line)
        val record = if (inbound) {
            SessionRecord.LineIn(clean, runCatching { SessionCodec.decodedForm(clean) }.getOrElse { "undecodable: ${it::class.simpleName}" })
        } else {
            SessionRecord.LineOut(clean)
        }
        synchronized(records) { records += record }
    }

    override fun decision(playstate: PlaystateData?, before: SyncState, ctx: SyncContext, outcome: SyncOutcome) {
        val setBy = playstate?.setBy?.let { redactor.name("user", it) }
        synchronized(records) { records += SessionRecord.Decision(playstate?.copy(setBy = setBy), before, ctx, outcome) }
    }

    /** How many decisions so far, for a recording that waits until something was decided. */
    val decisions: Int get() = synchronized(records) { records.count { it is SessionRecord.Decision } }

    fun write(file: File) {
        file.parentFile.mkdirs()
        val text = synchronized(records) { records.joinToString("\n", postfix = "\n") { SessionCodec.encode(it) } }
        file.writeText(text)
    }
}
