# Exploration: otli-device-evidence

Date: 2026-10-13. Source: read-only inventory of the archived change `otli-mvp` (tasks, six main specs, demo script, Engram records), produced by an explorer agent and spot-checked by the orchestrator (row O2 verified against the androidTest file list).

## Problem

`otli-mvp` was archived with a verify verdict of PASS WITH WARNINGS. Several checks that no automated suite can prove (device-only behaviour) have no evidence, or their evidence lives only in Engram and never reached `tasks.md`. Fourteen entries in the archived `tasks.md` still say "device check pending". One task (2.4.1) names an instrumented test that does not exist.

Automated evidence that already holds (2026-10-13): 1075 JVM unit tests, 321 Firestore rules tests, 61 instrumented adapter tests on a physical phone (7 classes), lint 0 errors, `assembleDebug`.

Evidence rule for this change: a check is closed only by (a) an executed test, (b) an artifact the agent captured with `adb` (screenshot, `uiautomator dump`, `dumpsys`, logcat) stored with the date, or (c) an explicit confirmation by the user, with date, recorded at that moment in `tasks.md` and Engram.

## Inventory (evidence found -> how it can be verified)

Verify column: AUTO = a test can be run; ADB = observable or drivable with adb on the phone; USER = needs the person's hands (second phone, walking, console).

### Layout and theme
| ID | Check | Evidence found | Verify |
|---|---|---|---|
| L1 | F.1 edge-to-edge: no content under the status bar | NONE explicit; the 2026-10-13 production flow reported no errors but names no layout | ADB |
| L2 | F.2 dark mode: Sign out contrast and enabled state on the gate | NONE (JVM `RootSurfaceTest` only) | ADB |
| L3 | F.3 ProfileIncomplete gate shows Sign out and Try again | NONE (needs an account without `users/{uid}`) | ADB with a crafted account, or USER |
| L4 | F.4 storefront top bar, up arrow, system back | NONE | ADB |
| L5 | F.5 overflow Sign out menu on customer and merchant homes | PARTIAL (sign-outs between roles seen in Engram #45, #69) | ADB |
| L6 | F.7/F.8 orders newest-first, "Placed HH:mm" on every list | F.7 verified on device (Engram #37, 2026-09-30); F.8 no explicit record | ADB |

### Map
| ID | Check | Evidence found | Verify |
|---|---|---|---|
| M1 | 2.3.0c pin picker: tap places the pin, screen scrolls | DONE: user, physical device, 2026-10-13 | done |
| M2 | 5.4.2 customer live map renders courier marker and "x ago" | DONE: Engram #43, #44 | done |
| M3 | F.10 courier map: own dot, pickup/dropoff emphasis, camera fit | DONE: Engram #44 (camera fit still listed as manual-only in verify-report #70) | done |
| M4 | F.11 no native info window, Compose legend | DONE: Engram #44, #45 | done |
| M5 | Admin order detail shows the live map | NONE specific (only the broad production flow) | ADB or USER |

### Location and GPS
| ID | Check | Evidence found | Verify |
|---|---|---|---|
| G1 | F.9 "Turn on location?" dialog going online with services off | DONE: Engram #44 | done (ADB can repeat) |
| G2 | F.9 warning mid-delivery with re-open button, removed when restored | PARTIAL: the "restored removes the warning" step has no record | ADB |
| G3 | F.12 publishing resumes after GPS off to on, no re-login | DONE: Engram #45 | done |
| G4 | Real walking track with the 10 s / 10 m / 60 s throttle | Engram #43 walk test OK (2026-10-02); throttle covered by JVM | USER |
| G5 | "Own position unknown" shows pins only, no courier marker | NONE on device (`MapSceneTest` JVM) | ADB (revoke permission) |

### Foreground service
| ID | Check | Evidence found | Verify |
|---|---|---|---|
| FS1 | `DeliveryTrackingService` keeps publishing with the app backgrounded or screen off | PARTIAL: walk test did not state the app was backgrounded | ADB |
| FS2 | Service notification appears and stops at delivered, release or cancel | NONE | ADB |
| FS3 | Foreground-service-type location starts on API 34+ | implicit only | ADB (logcat) |

### Notifications
| ID | Check | Evidence found | Verify |
|---|---|---|---|
| N1 | 3.5.1 local order notifications reach customer and merchant, one per status change | NONE (`LocalOrderNotifierTest` is JVM only) | ADB |
| N2 | `POST_NOTIFICATIONS` asked once on customer and merchant homes | NONE | ADB |
| N3 | Notification silently dropped when notifications are off | NONE | ADB |

### Realtime between actors
| ID | Check | Evidence found | Verify |
|---|---|---|---|
| R1 | Order status change reaches the other actor without refresh | PARTIAL: three roles on one phone (Engram #37, #40), no two-actor run | USER (second device) or phone + emulator |
| R2 | Claim race by simultaneous human tap | rules race tests (20 rounds) and the two-FirebaseApp race in `FirestoreDispatchRepositoryTest` (passed); no human tap race | AUTO for logic, USER for the literal tap |
| R3 | Merchant suspended live flips the signed-in phone to "Account suspended" | NONE | USER (2 devices) |

### Auth and permissions
| ID | Check | Evidence found | Verify |
|---|---|---|---|
| A1 | Runtime location permission request on going online | implied by Engram #44, no explicit record | ADB |
| A2 | F.6 sign-out does not crash | DONE: logcat review 2026-10-13, only benign PERMISSION_DENIED | done |
| A3 | Real bad-credential login | NONE | ADB or USER |
| A4 | Production API key restricted by package and SHA-1 | done by the user in the console (user statement 2026-10-13, together with enabling Email/Password); no artifact | USER |

### Admin
| ID | Check | Evidence found | Verify |
|---|---|---|---|
| AD1 | F.13 cancel picked_up order frees the courier | DONE: device test 13/13, manual scenario, production readback | done |
| AD2 | Fee, approvals, release, cancel, All orders | PARTIAL: broad production flow by the user, 2026-10-13 | done (broad) |

### Other
| ID | Check | Evidence found | Verify |
|---|---|---|---|
| O1 | 2.3.0d, 2.3.1, 3.4.1, 4.3.1, 5.3.1 adapter androidTests | AUTO: 61/61 on device 2026-10-13. Text of 3.4.1, 4.3.1, 5.3.1 still says compile-only / device pending | AUTO + text fix |
| O2 | 2.4.1 `FirestorePhotoSource` androidTest | **There is no `FirestorePhotoSourceTest`** among the 7 androidTest files (verified by the orchestrator) | NONE: code gap |
| O3 | Seed and admin scripts idempotent on re-run | guarded production seed has 11 tests; no manual rerun record | AUTO |
| O4 | `docs/demo-script.md` sections 2-7 unticked | broad confirmation 2026-10-13 only | USER |
| O5 | Demo script section 8 automated proofs unticked | AUTO: 1075 JVM, 321 rules | AUTO |

## Gaps in the specs themselves
- Dark mode and insets appear in no spec, only in F.1 and F.2.
- No spec scenario covers foreground-service persistence, the service-notification lifecycle, or order notifications (only ADR-12 and tasks 3.5.1, 5.3.2).
- "Own position unknown" is JVM-only; the ordering real-time scenario has no executed two-actor test.

## Open questions
- FS1: whether the 2026-10-02 walk test left the app in the background is not recorded.
- The phone serials `xkqwijtcyttsaahi` and `2510DRA23L` are the same device (adb lists both for it).
- A second physical phone is not available; R1 and R3 need a phone plus a PC Android emulator, or are left as explicit USER gaps.

## Recommendation
One SDD change, three groups of work:
- A: device checks the agent drives with adb against the local emulators (L1-L6, M5, G2, G5, FS1-FS3, N1-N3, A1, A3, O3), each closed with a stored artifact.
- B: checks that need the person (R1-R3, G4, A4, O4), closed with a dated confirmation.
- C: code gap O2 (write `FirestorePhotoSourceTest` test-first) and the stale text of 3.4.1, 4.3.1, 5.3.1, 2.4.1.
