package com.otli.app.auth.adapters.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue

/** Signed-out entry: sign-in by default, with a switch to registration and back. */
@Composable
fun SignedOutScreen() {
    var registering by rememberSaveable { mutableStateOf(false) }
    if (registering) {
        RegisterScreen(onNavigateToLogin = { registering = false })
    } else {
        LoginScreen(onNavigateToRegister = { registering = true })
    }
}
