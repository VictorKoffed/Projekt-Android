package com.victorkoffed.projektandroid.ui.viewmodel.brew // Eller .camera om du skapar en ny katalog

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.navigation.NavController
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class CameraViewModel @Inject constructor(
    // SavedStateHandle injiceras automatiskt av Hilt, men vi behöver den inte här
    // om vi bara skickar tillbaka resultatet till föregående skärm.
) : ViewModel() {

    /**
     * Sparar den tagna bildens URI i SavedStateHandle för den *föregående* skärmen
     * och navigerar sedan tillbaka.
     */
    fun saveImageUriAndReturn(uri: Uri, navController: NavController) {
        navController.previousBackStackEntry
            ?.savedStateHandle
            ?.set("captured_image_uri", uri.toString())
        navController.popBackStack()
    }

    // Du kan flytta CameraX-relaterad logik (t.ex. state för lensFacing,
    // hantering av imageCapture-instans) hit om du vill göra CameraScreen ännu tunnare.
    // För detta exempel låter vi den logiken vara kvar i CameraScreen.
}