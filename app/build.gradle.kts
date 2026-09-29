import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// AGP 9 ships built-in Kotlin support: do NOT apply org.jetbrains.kotlin.android.
// The google-services plugin is intentionally NOT applied until the real Firebase
// project exists (task 6.5.1); debug builds target the emulator via FirebaseOptions.

val localProps = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
// Precedence: -Potli.* Gradle property > local.properties > default. The Android emulator
// reaches the host at 10.0.2.2; a physical device over `adb reverse` reaches it at 127.0.0.1.
fun otliProp(name: String, default: String): String =
    (project.findProperty(name) as String?) ?: localProps.getProperty(name, default)

val useEmulator: String = otliProp("otli.useEmulator", "true")
val emulatorHost: String = otliProp("otli.emulatorHost", "10.0.2.2")

android {
    namespace = "com.otli.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.otli.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("boolean", "OTLI_USE_EMULATOR", useEmulator)
        buildConfigField("String", "OTLI_EMULATOR_HOST", "\"$emulatorHost\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Release builds never talk to the emulator.
            buildConfigField("boolean", "OTLI_USE_EMULATOR", "false")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(platform(libs.firebase.bom))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)

    // Pin coroutines explicitly: Firebase/AndroidX pull an older core transitively,
    // and the instrumented test APK must see the same version it was compiled against.
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)

    implementation(libs.maplibre.android)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.truth)
    testImplementation(libs.robolectric)

    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.truth)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
}
