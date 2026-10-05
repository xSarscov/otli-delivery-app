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
   `otli.useEmulator=true` and `otli.emulatorHost=10.0.2.2`. `10.0.2.2` is how the Android emulator
   reaches the PC; a physical phone over USB uses `127.0.0.1` together with `adb reverse` (see
   "Instrumented tests on a device"). Any `otli.*` value can also be passed as `-Potli.emulatorHost=...`
   on the Gradle command line, which takes precedence over `local.properties`.
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

## Instrumented tests on a device

`npm --prefix backend run test:android` starts the Auth and Firestore emulators, loads the seed
data (so adapter tests can sign in as `merchant1@otli.test`, password `otli-demo-123`), and runs
`connectedDebugAndroidTest` against the attached device:

- Exactly one device must be attached, or set `ANDROID_SERIAL` to choose one.
- For a physical device the script runs `adb reverse tcp:8080 tcp:8080` and `tcp:9099 tcp:9099`,
  builds with `-Potli.emulatorHost=127.0.0.1`, and removes the reverse mappings afterwards. For an
  Android emulator (`emulator-*` serial) nothing is reversed and the default `10.0.2.2` is used.
- Debug builds allow cleartext HTTP to 127.0.0.1, 10.0.2.2 and localhost only
  (generated at build time into the debug variant only, from `otli.emulatorHost`; see `buildSrc`), because the Auth emulator has no TLS.
  Release builds are unaffected.
- Some vendors block installs over USB. On Xiaomi/HyperOS enable "Install via USB" (and "USB debugging
  (Security settings)") in Developer options and accept the prompt on the phone; otherwise the run
  fails with `INSTALL_FAILED_USER_RESTRICTED`. The script exits non-zero in that case, because Gradle
  itself would still report BUILD SUCCESSFUL.
- Gradle uninstalls the app and test APKs when the run ends.

## Running the emulators and seed data

1. Start the emulators: `npm --prefix backend run emulators` (Auth 9099, Firestore 8080, UI 4000).
2. In another terminal, load demo accounts. The seed script refuses to run unless both emulator
   host variables are set, so it can never touch a real project:
   `FIRESTORE_EMULATOR_HOST=127.0.0.1:8080 FIREBASE_AUTH_EMULATOR_HOST=127.0.0.1:9099 npm --prefix backend run seed`.
   It is idempotent (fixed UIDs) and creates the Admin, a customer, two merchants, two couriers, one
   pending merchant, one pending courier, the catalogs and the flat fee (`admin@otli.test` /
   `otli-demo-123`; every account is listed in `docs/demo-script.md`).

The emulator project id is `demo-otli`; no real Firebase project is needed for development.

## Testing on a phone over Wi-Fi

Use this to try the app on a physical phone without USB or `adb reverse` during the test.

1. Put the phone and the PC on the same Wi-Fi, and set the PC network profile to **Private**.
2. Start the emulators bound to all interfaces: `npm --prefix backend run emulators:lan`
   (uses `backend/firebase.lan.json`). Allow the Windows firewall prompt for Java and Node on
   private networks.
3. Seed from the PC, which still reaches the emulators on localhost (see the seed command above).
4. Build with the PC LAN IP: `./gradlew assembleDebug -Potli.emulatorHost=<PC LAN IP>`. The host must
   be an IPv4 address or a simple hostname; the debug build then allows cleartext to exactly that
   host plus the default emulator hosts. Release builds never allow cleartext.
5. Install the APK over USB once, unplug, and test.

Security: while the LAN emulators run, any device on that Wi-Fi can reach Auth and Firestore
(no authentication in front of the emulators). Use trusted networks only and stop the emulators
afterwards.

## Demo

`docs/demo-script.md` is the manual end-to-end checklist for the academic demo: all four roles, the
Admin fee, approvals, release and cancellation, the claim race on two phones and the live map. It lists
the seeded accounts (password `otli-demo-123`, Admin `admin@otli.test`) and points back to the USB and
Wi-Fi setups above. The seed script creates one pending merchant and one pending courier on purpose,
so the Admin approval step works on a fresh emulator.

## Firebase project status

No real Firebase project exists yet. The `google-services` Gradle plugin is deliberately not applied
and no `google-services.json` is committed. Debug builds are designed to reach the emulators through
explicit `FirebaseOptions` (project `demo-otli`, placeholder app id and API key) in `core/di/OtliFirebase.kt`.
The real project, `google-services.json`, rules deployment and production seed are task 6.5 and each
step needs explicit user authorization.
