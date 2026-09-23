package app.preferences

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import app.utils.ioDispatcher
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.concurrent.Volatile
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import okio.Path.Companion.toPath

/**
 * The app's preference DataStore. Assign it with [createDataStore] before any preference access.
 * An earlier read throws [UninitializedPropertyAccessException].
 */
lateinit var datastore: DataStore<Preferences>


/**
 * The DataStore's coroutine scope. It lives as long as the process and is never cancelled.
 * [SupervisorJob] keeps one failed child from cancelling the others.
 */
val datastoreScope = CoroutineScope(SupervisorJob() + ioDispatcher)


/** Completes once the first read off disk has finished. */
private val preferencesLoaded = CompletableDeferred<Unit>()

@Volatile
private var cachedStateFlow: StateFlow<Preferences>? = null

/** Guards the one-time build of [cachedStateFlow]. */
private val stateFlowLock = SynchronizedObject()

/**
 * Non-null when the settings on disk could not be used and the app runs on defaults.
 *
 * Two places set it: the corruption handler (the usual case), and the first read, for a failure
 * that the handler does not cover. The first read blocks at startup. Without this fallback, an
 * unreadable file would throw there on every launch, with nothing on screen to say why and no
 * way out but a reinstall.
 */
var preferencesLoadFailure: Throwable? = null
    internal set

/**
 * A hot [StateFlow] of all preferences, collected once and shared for the whole process.
 *
 * The first read comes off disk, and the first caller waits for it. Without a warm-up, that
 * caller is the main thread during startup. So [warmPreferences] does the same read on a
 * background thread before the first frame, and the answer is usually ready when the main thread
 * asks.
 */
val datastoreStateFlow: StateFlow<Preferences>
    get() = cachedStateFlow ?: synchronized(stateFlowLock) {
        // Double-checked under the lock. On iOS and desktop, no splash screen holds the warm-up
        // thread and the first main-thread reader apart, so they can arrive together. Without the
        // lock, both would build their own eagerly collected flow: the store would be read off
        // disk twice, and one of the two collectors would leak for the life of the process.
        cachedStateFlow ?: run {
            // Null only in a browser, where blocking the one thread would freeze the page.
            val upfront = readBlockingOrNull {
                runCatching { datastore.data.first() }.getOrElse { failure ->
                    preferencesLoadFailure = failure
                    emptyPreferences()
                }
            }
            datastore.data.stateIn(
                scope = datastoreScope,
                started = SharingStarted.Eagerly,
                initialValue = upfront ?: emptyPreferences(),
            ).also {
                cachedStateFlow = it
                if (upfront != null) {
                    preferencesLoaded.complete(Unit)
                } else {
                    // Here preferencesLoaded completes when the first real value arrives. The web
                    // holds its first frame in awaitPreferences(), so nothing draws with defaults.
                    datastoreScope.launch {
                        runCatching { datastore.data.first() }
                            .onFailure { failure -> preferencesLoadFailure = failure }
                        preferencesLoaded.complete(Unit)
                    }
                }
            }
        }
    }

/**
 * Reads the store on a background thread. Each platform calls this once at startup, before
 * anything composes. No screen draws with default values that are about to change: Android holds
 * its splash screen until [arePreferencesLoaded], the web waits in [awaitPreferences], and iOS
 * and desktop block on the first read.
 */
fun warmPreferences() {
    datastoreScope.launch { datastoreStateFlow }
}

/** True once the store has been read once. The Android splash screen stays until then. */
val arePreferencesLoaded: Boolean get() = preferencesLoaded.isCompleted

/** Suspends until the store has been read at least once. */
suspend fun awaitPreferences() = preferencesLoaded.await()

/**
 * Drops the cached flow, so that the next read uses whatever [datastore] now points at. Only
 * tests install a second store in one process. Without this reset, the first store would stay
 * for the whole JVM run.
 */
fun resetPreferencesForTesting() {
    synchronized(stateFlowLock) {
        cachedStateFlow = null
        preferencesLoadFailure = null
    }
}

/**
 * The preferences snapshot for composition. The root composable ([app.AdamScreen]) provides it
 * once, and [app.preferences.watchPref] reads it through [derivedStateOf], so no composable has to
 * collect a flow of its own. [staticCompositionLocalOf] is correct because the [State] reference
 * never changes. Reads of [State.value] still recompose through the snapshot system.
 */
val LocalPrefsState = staticCompositionLocalOf<State<Preferences>> {
    mutableStateOf(datastoreStateFlow.value)
}

/**
 * Builds the preference [DataStore] at [producePath], with one migration: [ChatColorCleanup].
 *
 * A file that the parser rejects is replaced by an empty store, not thrown. Losing settings is
 * bad, but a crash on every launch is worse.
 */
fun createDataStore(
    producePath: () -> String,
): DataStore<Preferences> = PreferenceDataStoreFactory.createWithPath(
    corruptionHandler = ReplaceFileCorruptionHandler { failure ->
        preferencesLoadFailure = failure
        emptyPreferences()
    },
    migrations = listOf(ChatColorCleanup),
    produceFile = { producePath().toPath() },
)
