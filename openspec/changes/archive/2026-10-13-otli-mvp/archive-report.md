# Archive Report: otli-mvp

**Archived**: 2026-10-13
**Archived to**: `openspec/changes/archive/2026-10-13-otli-mvp/`
**Main specs synced to**: `openspec/specs/{admin,auth-roles,courier-dispatch,live-tracking,merchant-catalog,ordering}/spec.md`
**Repository state at close**: `main` @ 0364c4b (implementation merged); archive performed on branch `docs/otli-mvp-archive`

## Final State at Close

Final-state ranking applied per the archive contract: (1) persisted tasks file, (2) orchestrator launch-prompt facts, (3) intermediate snapshots (verify-report, apply-progress).

- **Implementation**: complete and merged to `main` (HEAD 0364c4b). Final-state fact from the orchestrator launch prompt, corroborated by the tasks file.
- **Tasks**: 84 of 84 checked in `tasks.md` (0 unchecked). Source: the on-disk tasks file in this archive, which outranks the Engram tasks revision (#24) that still describes device checks as pending.
- **Production Firebase (PR 6.5)**: done per the orchestrator launch prompt: rules and indexes deployed, app registered, production seed run (see tasks 6.5.1 to 6.5.3).
- **Later user decision**: Admin may cancel a `picked_up` order with a reason (F.13), superseding older spec and design text (Engram #46, topic `otli/admin-cancel-scope`). The admin spec in this archive already contains the requirement "Admin Cancellation of a Picked-Up Order".
- **Verification**: `sdd/otli-mvp/verify-report` (Engram #70) verdict is **PASS WITH WARNINGS** (0 critical, 5 warnings, 4 suggestions). Verification ran at main @ 0364c4b on 2026-10-13. Per the Final-State Authority hierarchy, this is an intermediate snapshot: it is recorded as history, not as a certificate. Its test counts (1073 JVM unit tests, 321 rules tests) are the counts observed at verification time.
- **Not run by verify**: the seven instrumented (androidTest) adapter classes, because no device was attached.

## Observation IDs Read for This Archive

| Artifact | Engram topic key | Observation ID | Notes |
|---|---|---|---|
| proposal | `sdd/otli-mvp/proposal` | #20 | Read in full. Mirror on disk: `proposal.md` |
| spec (summary) | `sdd/otli-mvp/spec` | #22 | Read in full. Stale summary: it lists fewer requirements than the on-disk specs. The on-disk spec files were archived as the source of truth |
| design | `sdd/otli-mvp/design` | #23 | Read in full. Mirror on disk: `design.md` |
| tasks | `sdd/otli-mvp/tasks` | #24 | Read in full. Revision 13 is stale (device checks pending). The on-disk `tasks.md` was used as the completion authority |
| verify-report | `sdd/otli-mvp/verify-report` | #70 | Read in full. Intermediate snapshot at main @ 0364c4b |
| later decision | `otli/admin-cancel-scope` | #46 | Read in full. Supersedes earlier admin-cancel text (F.13) |
| supporting decision | `otli/proposal-decisions` | #21 | Surfaced by search only; not required for the archive |

Other artifacts in the change folder with no Engram topic read by this phase: `exploration.md` (archived as a file) and `config.yaml` (untouched).

## Specs Synced

All six delta specs were full specs (no ADDED, MODIFIED, REMOVED, or RENAMED sections), and `openspec/specs/` was empty. Each was copied mechanically with `cp` and verified with `diff -r`.

| Domain | Action | Requirements |
|---|---|---|
| auth-roles | Created | 4 |
| merchant-catalog | Created | 4 |
| ordering | Created | 6 |
| courier-dispatch | Created | 4 |
| live-tracking | Created | 5 |
| admin | Created | 8 |

Requirement counts are taken from the on-disk spec files. They differ from the Engram #22 summary, which is stale.

## Known Gaps (recorded from verify-report #70, at verification time)

Warnings:
1. Seven instrumented adapter test classes (FirestoreAuthRepository, FirestoreMerchantRepository, FirestoreCatalogRepository, FirestoreOrderRepository, FirestoreDispatchRepository, FirestoreLocationRepository, FirestoreAdminRepository) were not run at verification time. Tasks 2.3.0d, 2.3.1, 3.4.1, 4.3.1, and 5.3.1 mention device-only checks. Only FirestoreAdminRepositoryTest (13/13) is reported green, by the orchestrator, not re-verified.
2. Native MapLibre rendering, gestures, foreground service, GPS settings dialog, and notifications were checked manually on a device only. Many F.x entries still say "device check pending".
3. ADR-3 `DomainPurityTest` guard (scanning `**/domain/**` for android or firebase imports) was never implemented. A manual scan found no such imports.
4. The apply-progress snapshot has no formal TDD Cycle Evidence table, so per-task RED, GREEN, and TRIANGULATE evidence cannot be cross-checked.
5. Tasks 2.3.0c, 2.3.0d, and 2.3.1 are checked, but their own text says part was "left unchecked". The checkbox state and the embedded text disagree. Per the archive rules, checkboxes are not rewritten; this is recorded here.

Compliance partials from the verify matrix: real-time listener round trip, location-services prompt, courier map rendering, and cross-cutting device-only paths were PARTIAL (not proven by an executed automated test).

Suggestions:
- Design Q3 is unchecked in `design.md`, although the concurrency tests resolve it.
- Design or spec drift not reconciled in the artifacts (recorded here, not repaired):
  - The design pool index table says "oldest first", but F.8 changed the pool to newest-placed-first.
  - The ordering spec lists 9 transitions, while the transition contract has 14. The extra five (admin cancel of picked_up and related) live in the admin spec.
  - The design says to commit `google-services.json`, but the app uses `app/firebase-prod.properties` (deviation recorded in task 6.5.1).
- 19 lint warnings (for example OldTargetApi, NewerVersionAvailable); lint has 0 errors.
- Restrict the production API key by package name and SHA-1 (a manual console step in task 6.5.1).

## Final Numbers

- JVM unit tests: 1073 tests in 121 classes, 0 failures (verify-time, `./gradlew testDebugUnitTest --rerun`).
- Rules tests: 321 of 321 passed across 17 files (verify-time, `npm --prefix backend test`).
- Lint: 19 warnings, 0 errors. `assembleDebug` succeeded.
- Instrumented tests: not run by verify; device results are orchestrator-reported only.

## Native Status

`gentle-ai sdd-status` is not available in this install. Native status is unresolved and was not fabricated.

## Contradictions Recorded (not silently resolved)

- Tasks 2.3.0c, 2.3.0d, 2.3.1: checkbox "checked" versus embedded text "left unchecked" (verify warning 5). Recorded, not repaired.
- Engram tasks #24 (device checks pending) versus `tasks.md` on disk (84 checked). The on-disk file ranks higher and was used.
- Engram spec summary #22 versus the on-disk spec files. The on-disk files ranked higher and were archived.

## Archive Contents

- `proposal.md`: present
- `exploration.md`: present
- `design.md`: present
- `tasks.md`: present, 84 of 84 checked, 0 unchecked
- `specs/`: present (six capability folders)
- `archive-report.md`: this file (additive; not in the source snapshot)

## Resolutions after archive (2026-10-13, additive)

Recorded after the archive, on the same day, before the archive branch was pushed. The sections above stay as written.

- Warning 1 (instrumented tests): resolved. `npm --prefix backend run test:android` on a physical device (2510DRA23L) ran all seven adapter classes: 61 tests, 0 failures (admin 13, auth 11, catalog 9, merchant 5, dispatch 11, order 8, location 4).
- Warning 3 (ADR-3 guard): resolved. `app/src/test/java/com/otli/app/DomainPurityTest.kt` fails when a file under a `domain/` package imports `android.`, `androidx.` or `com.google.firebase.`. It was shown to fail with an injected forbidden import and to pass on the real sources. The domain sources are declared as inputs of the unit-test tasks, so Gradle cannot report it up to date after an edit that changes no compiled class. JVM unit tests are now 1075, 0 failures.
- Warning 5 (checked tasks whose text said "left unchecked"): resolved in the text of 2.3.0c, 2.3.0d and 2.3.1. The original wording is kept and the outcome is appended. 2.3.0d and 2.3.1 are backed by the device run above. 2.3.0c is a manual check by the user (tap to place the pin, scrolling the screen), not an automated one.
- Still open: warning 2 (other device-only checks: foreground service, GPS settings dialog, notifications, map rendering of the live courier position), warning 4 (no formal TDD Cycle Evidence table), tasks 3.4.1, 4.3.1 and 5.3.1 still carry "compile-only / device pending" wording although the device run above covers their adapters, and the design drift listed under Suggestions.
