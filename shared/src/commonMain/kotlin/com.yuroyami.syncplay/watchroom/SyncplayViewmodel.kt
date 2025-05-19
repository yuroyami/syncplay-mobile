package com.yuroyami.syncplay.watchroom

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SyncplayViewmodel: ViewModel() {







    var snack = SnackbarHostState()
    fun snackIt(string: String, abruptly: Boolean = true) {
        viewModelScope.launch(Dispatchers.Main) {
            if (abruptly) {
                snack.currentSnackbarData?.dismiss()
            }
            snack.showSnackbar(
                message = string,
                duration = SnackbarDuration.Short
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
    }
}