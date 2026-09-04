package app.preferences

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
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
val datastoreScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)


/** True once the first read off disk has landed, so nothing has to block waiting for it. */
private val preferencesLoaded = CompletableDeferred<Unit>()

private var cachedStateFlow: StateFlow<Preferences>? = null

/**
 * Hot [StateFlow] of all preferences, collected once and shared for the whole process.
 *
 * The first read comes off disk, and the first caller pays for it. That caller used to be the
 * main thread during startup, which is why [warmPreferences] exists: it does the same read on a
 * background thread before the first frame, so by the time anything on the main thread asks, the
 * answer is already here.
 */
val datastoreStateFlow: StateFlow<Preferences>
    get() = cachedStateFlow ?: datastore.data.stateIn(
        scope = datastoreScope,
        started = SharingStarted.Eagerly,
        initialValue = runBlocking { datastore.data.first() },
    ).also {
        cachedStateFlow = it
        preferencesLoaded.complete(Unit)
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
    cachedStateFlow = null
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
 * Builds the preference [DataStore] at [producePath]. No corruption handler, no migrations.
 */
fun createDataStore(
    producePath: () -> String,
): DataStore<Preferences> = PreferenceDataStoreFactory.createWithPath(
    corruptionHandler = null,
    migrations = emptyList(),
    produceFile = { producePath().toPath() },
)