# Design: otli-device-evidence

## Technical Approach

An evidence change, not a feature change, under a LIGHT regime (user decision 2026-10-13, supersedes the heavier per-artifact rules). The goal is that work really done never stays "pending" and that nothing marked done lacks backing. The agent drives the Xiaomi phone (`xkqwijtcyttsaahi`, model 2510DRA23L) with `adb` against local Firebase emulators. Each device check ends in a task state in `tasks.md` (`[ ]` not done, `[~]` available but not verified on the device, `[x]` done) and a `[x]` carries a one-sentence `Evidencia:` line (what, where, who, date). At the end of every device session the agent appends rows to the single log `docs/manual-tests.md` and writes one Engram note under `sdd/otli-device-evidence/apply-progress`. No screenshots are committed; small text dumps are optional. Group C adds one instrumented characterization test (`FirestorePhotoSourceTest`) and corrects four stale archived task lines. No runtime code, rules, or data model changes.

## Machine facts (2026-10-13)

| Item | Found |
|---|---|
| `platform-tools/adb.exe` | Present (`%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe`, not on PATH) |
| `emulator` SDK package | **Installed**, version 37.2.12; WHPX acceleration check OK |
| System image | `system-images/android-36.1/google_apis_playstore/x86_64` (Play services present, so `FusedLocationProviderClient` works) |
| AVD | `Medium_Phone_API_36.1` (1080x2400, 2 GB RAM, `hw.gps=yes`) |

The emulator is ready, so R1 and R3 are feasible with the Xiaomi phone plus the AVD; no install authorization is needed. R1/R3 fall back to USER-gap only if the AVD fails to start at apply time (spec R1/R3).

## Architecture Decisions

### ADR-E1: Run against local Firebase emulators, debug build, one APK for both devices
**Choice**: Fresh emulators started from `backend/`: `npx firebase emulators:start --only firestore,auth --project demo-otli`, then `npm --prefix backend run seed`. Build once with `./gradlew assembleDebug -Potli.emulatorHost=127.0.0.1` and install that APK on every device; tunnel ports per device with `adb -s <serial> reverse tcp:8080 tcp:8080` and `tcp:9099 tcp:9099` (works on the AVD as well, so one APK serves both actors).
**Alternatives**: `npm --prefix backend run emulators` (imports `./emulator-data`, which does not exist on this machine and persists state between sessions: rejected for determinism); production `otli-39efc` (real data and quotas; only with explicit authorization, per proposal); building the AVD with the default `10.0.2.2` host (two APKs, two installs, easy to mix up).
**Rationale**: Disposable demo data, no production risk, matches how `run-with-emulators.mjs` already tunnels a physical device. Photos live in Firestore (ADR-11): no Storage emulator is needed (the spec's "Storage and Firestore emulators" wording means Firestore and Auth).

### ADR-E2: Light artifacts: no screenshots in the repo, optional small text dumps
**Choice**: Screenshots are not required, never committed or uploaded, may be taken locally as an aid, and are never the only evidence. Small text artifacts (filtered `uiautomator dump` excerpt, `dumpsys`, logcat excerpt) are optional and may be committed under `openspec/changes/otli-device-evidence/evidence/` when they help; no sha256, size budget, settings record or naming scheme. A two-line guard is kept in the root `.gitignore` so a local image cannot be committed by accident:
```
openspec/changes/otli-device-evidence/evidence/**/*.png
openspec/changes/otli-device-evidence/evidence/**/*.jpg
```
**Alternatives**: per-artifact sha256/size/MIUI-settings record (rejected 2026-10-13 as too heavy for an academic project); committing compressed screenshots (rejected by the user); Engram-only evidence (the original problem: evidence that never reached `tasks.md`).
**Rationale**: The failure to fix is unrecorded or unbacked status, not missing forensic detail. A one-sentence `Evidencia:` line plus a log row is enough backing.

### ADR-E3: Task state plus `Evidencia:` line plus one manual-test log
**Choice**: One task per inventory row (36) in `tasks.md`. States: `[ ]` not done, `[~]` implemented/available but NOT yet verified on the device, `[x]` done. A device task is `[x]` only with an `Evidencia:` line of one sentence: what was seen, where (phone / PC emulator / production), who (agent via adb / user), date. Example: "Evidencia: el aviso del GPS aparece y se va al restaurar, probado por el usuario en el Xiaomi (emulador local), 2026-10-14." Separately, `docs/manual-tests.md` is the one manual-test log: a table `date | row IDs | what was tested | result | where | who | reference`, where reference is an Engram observation id, the path of a small text artifact, or "-". At the end of every device session the agent appends its rows and writes one Engram note under `sdd/otli-device-evidence/apply-progress`.
**Alternatives**: a per-change `evidence-log.md` (a second place to keep in sync, the drift this change exists to remove); a status table inside the Engram note only (not reviewable in git).
**Rationale**: `tasks.md` is the source of truth for status, the log is the chronological record of sessions; the `[~]` state stops available-but-unverified work from being either "pending" or falsely "done".

### ADR-E4: Already-evidenced rows are cited, O4 redone by the user
**Choice**: M1, M2, M3, M4, G1, G3, A2, AD1 close with their Engram id or recorded date; O4 is a fresh, dated user walkthrough of `docs/demo-script.md` sections 2-7; AD2 cites O4. R2 is a dated user confirmation or a USER-gap; the existing race tests are cited as support only.

### ADR-E5: `FirestorePhotoSourceTest` as a characterization test with a mutation check instead of RED
**Choice**: New `app/src/androidTest/java/com/otli/app/catalog/adapters/firestore/FirestorePhotoSourceTest.kt`, same shape as `FirestoreCatalogRepositoryTest` (`AndroidJUnit4`, `OtliFirebase.auth/firestore(context)`, `await { }` helper with `withTimeout(30_000)`, sign-in through `FirestoreAuthRepository.login(..., "otli-demo-123")`, `@After` cleanup and sign-out). Cases, each covering what the existing class does not:
1. `aCustomerReadsAMerchantsProductPhoto`: as `merchant1@otli.test`, `upsertProduct` with bytes; sign in as `customer1@otli.test`; `fetch(MERCHANT_1, productId)` equals the bytes (cross-role read, ADR-11 rule "any signed-in user"). Cleanup signs back in as the merchant and removes the product.
2. `theProfilePhotoIsReadUnderTheFixedProfileId`: `FirestoreMerchantRepository.updatePhoto(MERCHANT_2, bytes)`, then `fetch(MERCHANT_2, PhotoKey.PROFILE_PHOTO_ID)` equals the bytes. Leaves `photoVersion` bumped; acceptable because `test:android` starts fresh emulators.
3. `aPhotoThatDoesNotExistIsNull`: `fetch(MERCHANT_2, "no-such-photo")` is null.
4. `aSignedOutReaderIsRefused`: signed out, `runCatching { fetch(...) }.isFailure` (rules deny). The apply phase confirms the read rule in `backend/firestore.rules` first; if the rule allows unauthenticated reads, the case is dropped and recorded as a finding, not adjusted to pass.
**Strict TDD exception (honest)**: `FirestorePhotoSource` already exists and works (`FirestoreCatalogRepositoryTest.theStoredPhotoCanBeFetchedThroughThePhotoSource` already reads through it), so no meaningful RED is observable. Record it as a characterization test. Replacement proof: three transient mutants of `FirestorePhotoSource.fetch`, each run against the new class and observed failing, then reverted and never committed: (M1) `return null`; (M2) read `.document(CatalogDocuments.PROFILE_PHOTO_ID)` instead of `photoId` (kills case 1); (M3) read under `merchantId = "seed-merchant-1"` hard-coded (kills case 2). Note per mutant, briefly: the failing test names and that the revert left `git diff --stat` empty on `app/src/main`.
**Alternatives**: claim 2.4.1 is already covered by `FirestoreCatalogRepositoryTest` and skip the class (fails the proposal success criterion "8 classes"); invent a RED by writing against a renamed API (dishonest).
**Run**: full suite `ANDROID_SERIAL=xkqwijtcyttsaahi npm --prefix backend run test:android` (expect 8 classes, 65 tests). Mutant runs may target one class with the emulators already up and seeded: `./gradlew connectedDebugAndroidTest -Potli.emulatorHost=127.0.0.1 -Pandroid.testInstrumentationRunnerArguments.class=com.otli.app.catalog.adapters.firestore.FirestorePhotoSourceTest`.

### ADR-E6: Stale archived text fixed clause by clause, branch notes kept
**Choice**: In `openspec/changes/archive/2026-10-13-otli-mvp/tasks.md`, replace only the stale clause of each line, keeping the branch notes: 2.4.1 "`FirestorePhotoSource` (androidTest pending device GREEN)" becomes "`FirestorePhotoSource` (covered by `FirestoreCatalogRepositoryTest` and `FirestorePhotoSourceTest`, device GREEN <date>, otli-device-evidence O2)"; 3.4.1 "is compile-checked only, its device run is pending" becomes "passed on device 2026-10-13 (61/61 run)"; 4.3.1 "compile-checked only, device run pending" and 5.3.1 "compiled, device GREEN pending" likewise. The F.x "device check pending" notes are updated to point at the row that closed them (for example "device: otli-device-evidence L1, <date>") only after that row is DONE or FAILED.

## Check families: technique and artifact

`A="$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe"; D="-s xkqwijtcyttsaahi"` (Git Bash). Package `com.otli.app`.

| Family (rows) | Technique | Optional text artifact |
|---|---|---|
| Bounds, enabled state, labels (L1-L6, A3, G5, M5) | `"$A" $D shell uiautomator dump /sdcard/ui.xml && "$A" $D pull /sdcard/ui.xml <scratch>/<id>.xml`; excerpt with `rg -o '<node[^>]*(text="Sign out"\|resource-id="[^"]*")[^>]*>'` | short excerpt `.txt` may be committed. L1: first content node `bounds` top >= status bar height from `dumpsys window \| rg -i statusBars` |
| Dark mode (L2) | `"$A" $D shell cmd uimode night yes`, relaunch, dump; restore `night no` | dump shows `enabled="true"`; contrast judged by eye (say so in the `Evidencia:` line) |
| Notifications (N1-N3, FS2) | `dumpsys notification --noredact \| rg -A3 'pkg=com.otli.app'` before/after; N3: turn notifications off with `pm revoke com.otli.app android.permission.POST_NOTIFICATIONS` (or the MIUI per-app toggle), then confirm no new record and no `FATAL` in logcat; N2: `pm revoke com.otli.app android.permission.POST_NOTIFICATIONS` + `pm clear-permission-flags com.otli.app android.permission.POST_NOTIFICATIONS user-set user-fixed` to reset, then logcat + dump of the permission dialog | optional excerpt; channels `orders` and `tracking` |
| Runtime location permission (A1, G5) | `pm revoke com.otli.app android.permission.ACCESS_FINE_LOCATION` (+ clear flags), then go online; `pm grant` to restore | dump of the system dialog / courier map |
| Location services (G1, G2) | `settings put secure location_mode 0` then `3` (or `cmd location set-location-enabled false/true`); verify with `settings get secure location_mode` | before/after dumps |
| Foreground service (FS1-FS3) | `dumpsys activity services com.otli.app \| rg -A5 DeliveryTrackingService`; background with `input keyevent HOME`, screen off with `input keyevent 223`; `logcat -d -v time \| rg -i 'DeliveryTrackingService\|foregroundServiceType\|FOREGROUND_SERVICE'`; FS1 progress read from the emulator: `curl -s -H "Authorization: Bearer owner" "http://127.0.0.1:8080/v1/projects/demo-otli/databases/(default)/documents/liveLocations/<orderId>"` twice, more than 60 s apart | optional excerpt; quote the `updatedAt` values in the `Evidencia:` line |
| Two actors (R1, R3) | Phone and AVD (`emulator-5554`) each with the shared APK and their own `adb reverse`; dump of the receiver after the actor's tap | optional dump |
| Seed idempotency (O3), automated (O5) | Seed twice on fresh emulators; after each run list counts per collection with the `Bearer owner` REST call (`rg -c '"name"'`); O5: `./gradlew testDebugUnitTest` and `npm --prefix backend test` summary lines | counts stated in the `Evidencia:` line; outputs optional |
| Bad credentials (A3) | Type with `input text`, submit; dump shows the error text and the login screen still present | dump excerpt |

**MIUI/HyperOS caveats** (a block can invalidate a result; mention it in the `Evidencia:` line or the finding, never work around silently): `input`, `pm grant/revoke` and `appops` need "USB debugging (Security settings)" enabled in Developer options (needs a Mi account); `adb install` needs "Install via USB"; Autostart and Battery saver can affect FS1/N1; the per-app notification toggle affects N1-N3; MIUI may show its own permission dialog instead of the AOSP one (N2/A1). No settings snapshot is recorded; only a setting that changed the outcome is mentioned.

## Closing a check (data flow)

```
run check ─→ set task state in tasks.md: [x] + Evidencia line, or [~], or USER-gap/FAILED + reason/finding
   (optional small text dump under evidence/)
end of the device session ─→ append rows to docs/manual-tests.md
                          └─→ one mem_save, topic sdd/otli-device-evidence/apply-progress
```
Row line format in `tasks.md`: `- [x] L2 Sign out legible in dark mode` followed by `  Evidencia: Sign out se ve habilitado y legible en modo oscuro, probado por el agente via adb en el Xiaomi (emulador local), 2026-10-14.` FAILED adds `finding: <one line>`; USER-gap adds `reason: <one line>`. A `[~]` is a row whose feature exists but was not exercised yet; it must be listed by `sdd-verify`.

`sdd-verify` for this change checks two rules (every `[x]` device task has an `Evidencia:` line; every `[~]` is listed in its report) and MUST also search Engram (`sdd/otli-device-evidence/*`, device-run notes such as #44/#45) for device-session records, since the previous verify missed them.

## File Changes

| File | Action | Description |
|---|---|---|
| `.gitignore` | Modify | Two-line guard: ignore `*.png`/`*.jpg` under the change's `evidence/` (ADR-E2) |
| `docs/manual-tests.md` | Create | Single manual-test log, one table, appended per device session (ADR-E3) |
| `openspec/changes/otli-device-evidence/evidence/*.txt` | Create (optional) | Small text dumps, only where they help |
| `openspec/changes/otli-device-evidence/tasks.md` | Create/Modify | Row tasks with `[ ]`/`[~]`/`[x]` and `Evidencia:` lines |
| `app/src/androidTest/java/com/otli/app/catalog/adapters/firestore/FirestorePhotoSourceTest.kt` | Create | O2 characterization test (~90 lines) |
| `openspec/changes/archive/2026-10-13-otli-mvp/tasks.md` | Modify | 2.4.1, 3.4.1, 4.3.1, 5.3.1 and F.x pointers (ADR-E6) |
| `docs/demo-script.md` | Modify | Tick sections 2-7 (O4) and 8 (O5) with date |

## Testing Strategy

| Layer | What | Approach |
|---|---|---|
| Instrumented | `FirestorePhotoSource` (O2) | ADR-E5, characterization + 3 mutants; suite 8 classes |
| JVM / rules | O5 reruns | existing commands, counts recorded |
| Device | Group A rows | adb techniques per table above; result in `Evidencia:` line and `docs/manual-tests.md` |
| Manual | Group B rows | dated user confirmation summarized in the `Evidencia:` line |

## Threat Matrix

N/A: no routing, subprocess, VCS/PR automation, or process-integration code is added. The adb commands are operator actions on a local device with demo data; no remote host is touched (production stays out unless authorized).

## Sequencing and review forecast

There are no evidence files to count except `docs/manual-tests.md` (a table of a few dozen short rows) and the optional small text dumps; images are never committed. The light regime lowers every estimate below the earlier figures.

| PR (auto-chain) | Content | Est. changed lines |
|---|---|---|
| 01 plan | exploration, proposal, spec, design, tasks | ~600 docs: recommended split into 01a (exploration+proposal+spec, ~330) and 01b (design+tasks, ~270) |
| 02 group C | `.gitignore` guard, `FirestorePhotoSourceTest`, archived text fixes, O1/O2 task lines | ~160 |
| 03 group A1 | L1-L6, A3, O3, O5, citations of done rows, log rows | ~120 |
| 04 group A2 | M5, G2, G5, A1, FS1-FS3, N1-N3, log rows | ~130 |
| 05 group B | R1, R3 (phone + AVD), R2, G4, A4, O4/AD2, demo-script ticks, archived F.x pointers, log rows | ~150 |

Order rationale: PR 02 first because it adds the `.gitignore` guard and the log file. 05 last because it depends on user time.

## Migration / Rollout

No migration. Rollback: `git revert` the PR commits; local screenshots can be deleted; emulator data is disposable.

## Risks

- MIUI "USB debugging (Security settings)" may be off: `input`/`pm` fail; record as a finding and ask the user to enable it.
- Case 4 of O2 depends on the read rule; verify before writing it.
- A light `Evidencia:` line is only as honest as its author; `sdd-verify` checks its presence and searches Engram, not its truth.

## Open Questions

None. The emulator question is resolved: the package is installed (37.2.12, WHPX OK), so R1/R3 are feasible.
