package com.victorkoffed.projektandroid.ui.viewmodel.brew

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.navigation.NavController
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class CameraViewModel @Inject constructor(
) : ViewModel() {

    /**
     * Sparar den tagna bildens URI i SavedStateHandle för den *föregående* skärmen
     * och navigerar sedan tillbaka.
     */
    fun saveImageUriAndReturn(uri: Uri, navController: NavController) {

        Log.d("CameraViewModel", "Försöker spara URI ($uri) till föregående skärms SavedStateHandle.")

        navController.previousBackStackEntry
            ?.savedStateHandle
            ?.set("captured_image_uri", uri.toString())
        navController.popBackStack()
    }
}