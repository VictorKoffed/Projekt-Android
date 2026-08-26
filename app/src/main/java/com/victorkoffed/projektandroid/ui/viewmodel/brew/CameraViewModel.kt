package com.victorkoffed.projektandroid.ui.viewmodel.brew

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.navigation.NavController
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * Manages camera capture workflows, propagating captured media URIs back through
 * the navigation back stack for consumption by preceding screens.
 */
@HiltViewModel
class CameraViewModel @Inject constructor(
) : ViewModel() {

    /**
     * Persists the captured image URI into the previous destination's SavedStateHandle
     * before popping the camera screen from the navigation stack.
     */
    fun saveImageUriAndReturn(uri: Uri, navController: NavController) {

        Log.d("CameraViewModel", "Försöker spara URI ($uri) till föregående skärms SavedStateHandle.")

        navController.previousBackStackEntry
            ?.savedStateHandle
            ?.set("captured_image_uri", uri.toString())
        navController.popBackStack()
    }
}