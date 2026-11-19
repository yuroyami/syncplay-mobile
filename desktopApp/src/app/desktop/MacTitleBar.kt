package app.desktop

import app.utils.loggy
import com.sun.jna.Callback
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.NativeLibrary
import com.sun.jna.Pointer

/**
 * Gives the macOS window a taller title bar with the window buttons centered in it, the way a
 * window with a unified toolbar looks. The app draws under the bar (see Main.kt), so the close,
 * minimize and full-screen buttons then line up with the top bar of each screen.
 *
 * Java has no setting for this, so it calls AppKit through JNA: it adds an empty toolbar to the
 * window with the given [title]. AppKit runs it on the main thread. A failure only logs.
 */
object MacTitleBar {

    /** objc_msgSend with fixed arguments. On Apple silicon a variadic call puts them in the wrong place. */
    private interface ObjC : Library {
        fun objc_getClass(name: String): Pointer?
        fun sel_registerName(name: String): Pointer
        fun objc_msgSend(receiver: Pointer?, selector: Pointer): Pointer?
        fun objc_msgSend(receiver: Pointer?, selector: Pointer, argument: Pointer?): Pointer?
        fun objc_msgSend(receiver: Pointer?, selector: Pointer, argument: Long): Pointer?
    }

    private interface Dispatch : Library {
        fun dispatch_async_f(queue: Pointer, context: Pointer?, work: Work)
    }

    fun interface Work : Callback {
        fun invoke(context: Pointer?)
    }

    /** NSWindowToolbarStyleUnified: a 52 point bar. */
    private const val UNIFIED = 3L

    /** NSTitlebarSeparatorStyleNone: no line under the bar. */
    private const val NO_SEPARATOR = 1L

    // JNA must keep the callback reachable until AppKit has run it.
    private var pending: Work? = null

    fun apply(title: String) {
        runCatching {
            val objc = Native.load("objc", ObjC::class.java)
            val dispatch = Native.load("System", Dispatch::class.java)
            val mainQueue = NativeLibrary.getInstance("System").getGlobalVariableAddress("_dispatch_main_q")
            val work = Work { runCatching { addToolbar(objc, title) }.onFailure { loggy("macOS title bar: ${it.message}") } }
            pending = work
            dispatch.dispatch_async_f(mainQueue, null, work)
        }.onFailure { loggy("macOS title bar: ${it.message}") }
    }

    private fun addToolbar(objc: ObjC, title: String) {
        fun sel(name: String) = objc.sel_registerName(name)
        fun string(text: String): Pointer? = objc.objc_msgSend(
            objc.objc_getClass("NSString"), sel("stringWithUTF8String:"),
            com.sun.jna.Memory((text.toByteArray().size + 1).toLong()).apply { setString(0, text, "UTF-8") },
        )

        val app = objc.objc_msgSend(objc.objc_getClass("NSApplication"), sel("sharedApplication"))
        val windows = objc.objc_msgSend(app, sel("windows"))
        val count = Pointer.nativeValue(objc.objc_msgSend(windows, sel("count")))
        val wanted = string(title)
        for (i in 0 until count) {
            val window = objc.objc_msgSend(windows, sel("objectAtIndex:"), i)
            val name = objc.objc_msgSend(window, sel("title"))
            if (Pointer.nativeValue(objc.objc_msgSend(name, sel("isEqualToString:"), wanted)) and 0xFF == 0L) continue
            if (objc.objc_msgSend(window, sel("toolbar")) != null) continue
            val toolbar = objc.objc_msgSend(
                objc.objc_msgSend(objc.objc_getClass("NSToolbar"), sel("alloc")),
                sel("initWithIdentifier:"), string("synkplay.titlebar"),
            )
            objc.objc_msgSend(window, sel("setToolbar:"), toolbar)
            objc.objc_msgSend(window, sel("setToolbarStyle:"), UNIFIED)
            objc.objc_msgSend(window, sel("setTitlebarSeparatorStyle:"), NO_SEPARATOR)
        }
        pending = null
    }
}
