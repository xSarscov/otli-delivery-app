package com.otli.app.core.di

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.otli.app.BuildConfig

/**
 * Single place that initializes Firebase and points it at the emulators when
 * `BuildConfig.OTLI_USE_EMULATOR` is set (see Environment Wiring in design.md).
 *
 * `useEmulator` must run before the first use of an instance and only once, so the
 * instances are memoized here; both the Hilt module and instrumented tests call this.
 */
object OtliFirebase {
    const val EMULATOR_PROJECT_ID = "demo-otli"
    const val AUTH_EMULATOR_PORT = 9099
    const val FIRESTORE_EMULATOR_PORT = 8080

    private var auth: FirebaseAuth? = null
    private var firestore: FirebaseFirestore? = null

    @Synchronized
    fun app(context: Context): FirebaseApp {
        FirebaseApp.getApps(context).firstOrNull()?.let { return it }
        return if (BuildConfig.OTLI_USE_EMULATOR) {
            // No google-services.json yet: placeholder options are enough for the emulators.
            val options = FirebaseOptions.Builder()
                .setProjectId(EMULATOR_PROJECT_ID)
                .setApplicationId("1:000000000000:android:0000000000000000")
                .setApiKey("demo-api-key")
                .build()
            FirebaseApp.initializeApp(context, options)
        } else {
            // Live project: options come from google-services.json once task 6.5.1 adds it.
            checkNotNull(FirebaseApp.initializeApp(context)) {
                "Firebase is not configured: add google-services.json (task 6.5.1)"
            }
        }
    }

    @Synchronized
    fun auth(context: Context): FirebaseAuth = auth ?: FirebaseAuth.getInstance(app(context)).also {
        if (BuildConfig.OTLI_USE_EMULATOR) it.useEmulator(BuildConfig.OTLI_EMULATOR_HOST, AUTH_EMULATOR_PORT)
        auth = it
    }

    @Synchronized
    fun firestore(context: Context): FirebaseFirestore =
        firestore ?: FirebaseFirestore.getInstance(app(context)).also {
            if (BuildConfig.OTLI_USE_EMULATOR) it.useEmulator(BuildConfig.OTLI_EMULATOR_HOST, FIRESTORE_EMULATOR_PORT)
            firestore = it
        }
}
