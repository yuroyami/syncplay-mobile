package app.preferences

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
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


/** Hot [StateFlow] of all preferences, collected once and shared eagerly for the whole process. */
val datastoreStateFlow: StateFlow<Preferences> by lazy {
    datastore.data.stateIn(
        scope = datastoreScope,
        started = SharingStarted.Eagerly,
        initialValue = runBlocking { datastore.data.first() }
    )
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