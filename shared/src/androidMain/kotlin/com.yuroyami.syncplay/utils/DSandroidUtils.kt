package com.yuroyami.syncplay.utils

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.yuroyami.syncplay.logic.datastore.createDataStore

fun dataStore(context: Context, fileName: String): DataStore<Preferences> =
    createDataStore(
        producePath = { context.filesDir.resolve(fileName).absolutePath }
    )