# Otli Delivery App

Android delivery marketplace for Nagarote, Nicaragua (academic project). Four roles:
customer, merchant, courier, admin. Kotlin + Jetpack Compose on Firebase Auth and
Cloud Firestore (Spark plan, rules-only server authority), MapLibre for maps.

Planning artifacts live in `openspec/changes/otli-mvp/` (proposal, specs, design, tasks).

## Repository layout

| Path | What it holds |
|---|---|
| `app/` | Android app (single `:app` module, package `com.otli.app`) |
| `backend/` | Firestore rules, rules tests (Vitest), emulator config, seed script |
| `backend/contracts/order-transitions.json` | Order-transition contract shared by Kotlin and rules tests |
| `openspec/` | Specs, design and task plan (SDD) |

## Prerequisites

| Tool | Version | Why |
|---|---|---|
| JDK | 17 or 21 (JDK 21 is used for the Firebase emulators; the Android build targets a 17 toolchain) | Gradle, Android build, Firestore emulator |
| Node.js | 22 or newer | Backend tooling (`firebase-admin` 14 requires Node 22+) |
| Android SDK | platform 37 (auto-downloaded by Gradle) and `ANDROID_HOME` set | Building the app |
| Android emulator or device | any API 26+ | Only for instrumented tests and manual demos |

`firebase-tools` is a `backend` dev dependency; no global install is needed.

## First-time setup

1. Create `local.properties` in the repo root (it is gitignored) and point it at your SDK.
   On Windows use the escaped drive form `sdk.dir=C\:/Users/<you>/AppData/Local/Android/Sdk`
   (plain `C:\...` is rejected by Android lint).
2. Optional emulator wiring in the same file (defaults shown):
   `otli.useEmulator=true` and `otli.emulatorHost=10.0.2.2` (use your LAN IP on a physical device).
3. Install backend dependencies: `npm --prefix backend install`.

## The four test commands

Run from the repository root (on Windows PowerShell use `.\gradlew.bat`).

| Purpose | Command |
|---|---|
| Build the debug APK | `./gradlew assembleDebug` |
| Android JVM unit tests | `./gradlew testDebugUnitTest` |
| Firestore rules tests (starts the emulators itself) | `npm --prefix backend test` |
| Everything: unit tests + lint + rules tests | `./gradlew verifyAll` |

Extra commands:

- One rules test file: `npm --prefix backend test -- users` (simple file filters only).
- One Kotlin test class: `./gradlew testDebugUnitTest --tests "com.otli.app.PlaceholderTest"`.
- Backend typecheck: `npm --prefix backend run typecheck`.
- Instrumented adapter tests (needs a running Android emulator or device): `npm --prefix backend run test:android`.

## Running the emulators and seed data

1. Start the emulators: `npm --prefix backend run emulators` (Auth 9099, Firestore 8080, UI 4000).
2. In another terminal, load demo accounts. The seed script refuses to run unless both emulator
   host variables are set, so it can never touch a real project:
   `FIRESTORE_EMULATOR_HOST=127.0.0.1:8080 FIREBASE_AUTH_EMULATOR_HOST=127.0.0.1:9099 npm --prefix backend run seed`.
   It is idempotent (fixed UIDs) and currently creates the `seed-admin` account
   (`admin@otli.test` / `otli-demo-123`); later slices extend it.

The emulator project id is `demo-otli`; no real Firebase project is needed for development.

## Firebase project status

No real Firebase project exists yet. The `google-services` Gradle plugin is deliberately not applied
and no `google-services.json` is committed. Debug builds are designed to reach the emulators through
explicit `FirebaseOptions` (project `demo-otli`, placeholder app id and API key) wired in task 1.3.1.
The real project, `google-services.json`, rules deployment and production seed are task 6.5 and each
step needs explicit user authorization.
