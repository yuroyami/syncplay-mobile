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
 * Global DataStore instance for application preferences. Must be assigned via [createDataStore]
 * before any preference access; reading it earlier throws [UninitializedPropertyAccessException].
 */
lateinit var datastore: DataStore<Preferences>


/**
 * Process-lifetime coroutine scope for DataStore. Never cancelled. Uses [SupervisorJob] so one
 * failed child doesn't tear down the others.
 */
val datastoreScope = CoroutineScope(SupervisorJob() + ioDispatcher)


/** True once the first read off disk has landed, so nothing has to block waiting for it. */
private val preferencesLoaded = CompletableDeferred<Unit>()

@Volatile
private var cachedStateFlow: StateFlow<Preferences>? = null

/** Guards the one-time build of [cachedStateFlow]. */
private val stateFlowLock = SynchronizedObject()

/**
 * Non-null when the settings on disk could not be used and the app is running on defaults.
 *
 * Set from two places: the corruption handler, which is the usual one, and the first read, for a
 * failure the handler does not cover. The store is read once, blocking, behind the splash, so
 * before this existed an unreadable file threw there on every launch, with nothing on screen to
 * say why and no way out but reinstalling.
 */
var preferencesLoadFailure: Throwable? = null
    internal set

/**
 * Hot [StateFlow] of all preferences, collected once and shared for the whole process.
 *
 * The first read comes off disk, and the first caller pays for it. That caller used to be the
 * main thread during startup, which is why [warmPreferences] exists: it does the same read on a
 * background thread before the first frame, so by the time anything on the main thread asks, the
 * answer is already here.
 */
val datastoreStateFlow: StateFlow<Preferences>
    get() = cachedStateFlow ?: synchronized(stateFlowLock) {
        // Double-checked under the lock: the warm-up thread and the first main-thread reader
        // arrive together on iOS and desktop, which have no splash to hold them apart. Without
        // this both built their own eagerly-collected flow, so the store was read off disk twice
        // and one of the two collectors leaked for the life of the process.
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
                    // The gate opens when the first real value lands instead. awaitPreferences()
                    // is still what holds the first frame, so nothing draws against defaults.
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
 * Reads the store on a background thread. Called once at startup, before anything composes.
 * The platform holds its splash until [awaitPreferences] returns, so no screen is ever drawn
 * against default values that are about to change.
 */
fun warmPreferences() {
    datastoreScope.launch { datastoreStateFlow }
}

/** True once [warmPreferences] has finished; the Android splash holds on this. */
val arePreferencesLoaded: Boolean get() = preferencesLoaded.isCompleted

/** Suspends until the store has been read at least once. */
suspend fun awaitPreferences() = preferencesLoaded.await()

/**
 * Drops the memoized flow so the next read builds against whatever [datastore] now points at.
 * Only tests install a second store in one process; without this the first one won a whole JVM.
 */
fun resetPreferencesForTesting() {
    synchronized(stateFlowLock) {
        cachedStateFlow = null
        preferencesLoadFailure = null
    }
}

/**
 * Composition-level preferences snapshot, provided once at the root composable ([app.AdamScreen])
 * and read by [app.preferences.watchPref] via [derivedStateOf], avoiding per-composable flow
 * collection. [staticCompositionLocalOf] is correct because the [State] reference never changes;
 * reads of [State.value] still recompose via the snapshot system.
 */
val LocalPrefsState = staticCompositionLocalOf<State<Preferences>> {
    mutableStateOf(datastoreStateFlow.value)
}

/**
 * Builds the preference [DataStore] at [producePath]. No migrations.
 *
 * A file the parser rejects is replaced by an empty store rather than thrown from. Losing
 * settings is bad; a permanent crash on launch is worse, and it is what the alternative gave.
 */
fun createDataStore(
    producePath: () -> String,
): DataStore<Preferences> = PreferenceDataStoreFactory.createWithPath(
    corruptionHandler = ReplaceFileCorruptionHandler { failure ->
        preferencesLoadFailure = failure
        emptyPreferences()
    },
    migrations = emptyList(),
    produceFile = { producePath().toPath() },
)
