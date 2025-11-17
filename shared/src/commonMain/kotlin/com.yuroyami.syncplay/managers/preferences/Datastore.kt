package com.yuroyami.syncplay.managers.preferences

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
 * Global DataStore instance for application preferences.
 *
 * This lateinit variable must be initialized using [createDataStore] before any
 * preference operations are performed. Accessing this before initialization
 * will throw [UninitializedPropertyAccessException].
 */
lateinit var datastore: DataStore<Preferences>


/**
 * Application-lifetime coroutine scope for DataStore operations.
 *
 * This scope lives for the entire app process and is never cancelled,
 * making it perfect for singletons like DataStore that need to persist
 * throughout the app lifecycle.
 *
 * Uses SupervisorJob so failures in one coroutine don't cancel others.
 */
val datastoreScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)


/** The actual HOT FLOW that emits data... we collect from it only once here (lazily) */
val datastoreStateFlow: StateFlow<Preferences> by lazy {
    datastore.data.stateIn(
        scope = datastoreScope,
        started = SharingStarted.Eagerly,
        initialValue = runBlocking { datastore.data.first() }
    )
}

/**
 * Creates a [DataStore] instance with the specified file path.
 *
 * This factory function initializes the Preference DataStore with the given path.
 * The DataStore is created without corruption handling or migrations by default.
 *
 * @param producePath Lambda that provides the file path for storing preferences
 * @return Configured [DataStore] instance ready for use
 */
fun createDataStore(
    producePath: () -> String,
): DataStore<Preferences> = PreferenceDataStoreFactory.createWithPath(
    corruptionHandler = null,
    migrations = emptyList(),
    produceFile = { producePath().toPath() },
)