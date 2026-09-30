plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
}

// Workspace aggregate (Strict TDD gate): JVM unit tests + Android lint + Firestore rules tests.
// The npm invocation uses fixed arguments only; no user-supplied input reaches the process.
val backendRulesTest by tasks.registering(Exec::class) {
    group = "verification"
    description = "Runs the Firestore rules test suite against the local emulators."
    workingDir = rootDir
    val isWindows = System.getProperty("os.name").startsWith("Windows", ignoreCase = true)
    commandLine(if (isWindows) "npm.cmd" else "npm", "--prefix", "backend", "test")
}

tasks.register("verifyAll") {
    group = "verification"
    description = "Runs testDebugUnitTest, lintDebug and the backend rules tests."
    dependsOn(":app:testDebugUnitTest", ":app:lintDebug", backendRulesTest)
}
