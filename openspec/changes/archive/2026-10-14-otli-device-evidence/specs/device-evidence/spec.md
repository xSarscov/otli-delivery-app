# Device Evidence Specification

## Purpose

Defines the LIGHT verification protocol that closes the 36 device-evidence rows inherited from the archived `otli-mvp` change (inventory in `exploration.md`). Its goal is twofold: work that is really done MUST NOT stay "pending", and a task marked done MUST have something backing it. This is a CHANGE-LOCAL PROTOCOL: it adds, modifies and removes no product behavior. It MUST NOT be synced or promoted into `openspec/specs/` at archive; `sdd-archive` MUST leave it inside the archived change folder only. It adds no scenarios to the six main capability specs; the spec gaps found by the exploration (insets and dark mode, foreground-service lifecycle, order notifications, "own position unknown") are follow-up, not specified here.

Canonical list: rows L1-L6, M1-M5, G1-G5, FS1-FS3, N1-N3, R1-R3, A1-A4, AD1-AD2, O1-O5 (36). Row meanings are those of the `exploration.md` inventory.

## Requirements

### Requirement: Task states

Every task in this change's `tasks.md` MUST be in exactly one state: `[ ]` not done; `[~]` implemented or available but NOT yet verified on the device; `[x]` done. A device-check task MAY be `[x]` only if it carries an `Evidencia:` line (next requirement). A `[~]` MUST NOT be presented as done anywhere.

#### Scenario: Available but unverified
- GIVEN a feature exists in the app but no one has exercised it on a device in this change
- WHEN its task is recorded
- THEN it reads `[~]`, not `[x]` and not `[ ]`

### Requirement: Evidencia line

A device-check task marked `[x]` MUST have one `Evidencia:` line of ONE sentence stating: what was seen, where (phone, PC emulator, or production), who (agent via adb, or user), and the date. Example: "Evidencia: el aviso del GPS aparece y se va al restaurar, probado por el usuario en el Xiaomi (emulador local), 2026-10-14." No hash, size, file name, MIUI/notification/location settings or naming scheme is required. A `[x]` device task without that line is invalid.

#### Scenario: Done with Evidencia
- GIVEN an adb-driven check has just run and passed
- WHEN its task is closed
- THEN the task is `[x]` and carries a one-sentence `Evidencia:` line with what, where, who and date

#### Scenario: Unsupported claim
- GIVEN a device task is `[x]` with no `Evidencia:` line
- WHEN the change is reviewed
- THEN the task is invalid and MUST be given the line or reverted to `[~]` or `[ ]`

### Requirement: Manual-test log

`docs/manual-tests.md` MUST be the single manual-test log: one table with columns date | row IDs | what was tested | result | where | who | reference. `reference` is an Engram observation id, the path of a small text artifact, or "-". The agent MUST append rows at the end of every device session, and the same session MUST also write one Engram note under topic `sdd/otli-device-evidence/apply-progress`.

#### Scenario: End of a device session
- GIVEN a device session has finished
- WHEN the agent closes the session
- THEN `docs/manual-tests.md` has the new rows appended and one apply-progress note exists in Engram

### Requirement: Optional artifacts, no screenshots in the repo

Screenshots are NOT required and MUST NOT be committed or uploaded anywhere in the repo; they are optional, local, and MUST NOT be the only evidence for a row. Small TEXT artifacts (`uiautomator dump` excerpt, `dumpsys`, logcat excerpt) are optional, MAY be committed under `openspec/changes/otli-device-evidence/evidence/` when they help, and need no sha256, size budget or settings record.

#### Scenario: Screenshot taken
- GIVEN the agent or user took a screenshot during a check
- WHEN the row is closed
- THEN the screenshot is not committed and the `Evidencia:` line and the log row stand on their own

### Requirement: Closed set of row outcomes

Every one of the 36 rows MUST end in exactly one outcome: DONE (`[x]` with its `Evidencia:` line), USER-gap (check needs the person and was not performed; a reason and date MUST be recorded), or FAILED (check executed and the behavior was wrong; a finding MUST be recorded and the fix goes to a separate change). A row MUST NOT end as "pending", "n/a" or unlisted.

#### Scenario: Complete log
- GIVEN all tasks are finished
- WHEN `tasks.md` and `docs/manual-tests.md` are read
- THEN all 36 rows have an outcome and none reads "pending"

#### Scenario: USER-gap without reason
- GIVEN a row is marked USER-gap with no reason
- WHEN it is reviewed
- THEN the row is invalid and MUST be completed or reclassified

### Requirement: Already-evidenced rows are cited, not redone

Rows M2, M3, M4, G1, G3 and AD1 MUST be closed by citing their existing Engram observation id (#43-#45 and the AD1 device run) in the `Evidencia:` line; they MUST NOT be re-executed. M1 and A2 MUST be closed by citing their recorded 2026-10-13 evidence. O4 (demo-script walkthrough, `docs/demo-script.md` sections 2-7) MUST be redone by the user and recorded as a dated confirmation or USER-gap; AD2 is covered by O4 and MUST cite it.

#### Scenario: Citation
- GIVEN row G3 has Engram #45
- WHEN the row is closed
- THEN its `Evidencia:` line names Engram #45 and its date and no new check is required

#### Scenario: AD2 covered by O4
- GIVEN O4 is DONE
- WHEN AD2 is closed
- THEN AD2 is DONE citing O4; if O4 is a USER-gap, AD2 MUST be a USER-gap with the same reason

### Requirement: Check families and acceptance criteria

Checks run on the phone (Xiaomi 2510DRA23L) against the local Firebase emulators by default; production only with explicit user authorization. Acceptance criteria per family:
- L1-L6: L1 no content under the status bar; L2 in dark mode the gate's Sign out is legible and enabled; L3 ProfileIncomplete gate shows Sign out and Try again (USER-gap with reason if the account cannot be crafted); L4 storefront has top bar and up arrow and system back returns; L5 overflow menu offers Sign out on customer and merchant homes; L6 every order list is newest-first and each row shows "Placed HH:mm".
- N1-N3: N1 one local notification per status change for customer and for merchant; N2 `POST_NOTIFICATIONS` asked once per home and not re-asked after a decision; N3 with notifications disabled no notification appears and the app does not crash.
- FS1-FS3: FS1 courier location keeps updating backgrounded and screen-off (`updatedAt` advancing across two throttle intervals); FS2 `DeliveryTrackingService` listed while delivering and gone after delivered, release and cancel; FS3 started as a location foreground-service type on API 34+.
- G2, G5, A1: G2 warning with re-open button appears with location services off mid-delivery and disappears on restore; G5 with permission revoked the courier map shows pins only and no courier marker; A1 going online without permission raises the runtime request.
- A3: wrong password is rejected with an error and no session. A4 is a dated user confirmation or USER-gap and MUST NOT be probed with production access unless the user authorizes it. G4 is closed, by decision of the user (2026-10-14), by citing the real walk test of 2026-10-02 (Engram #43) together with the executed `LocationThrottleTest` (10 s / 10 m / 60 s heartbeat), stating what each one covers.

#### Scenario: Notifications off
- GIVEN notifications are disabled for the app
- WHEN a status change occurs
- THEN no notification is posted, the app does not crash, and the N3 task carries an `Evidencia:` line

#### Scenario: Warning removed on restore
- GIVEN the mid-delivery location warning is visible
- WHEN location services are turned back on
- THEN the warning is gone and the G2 `Evidencia:` line says so

### Requirement: Realtime between two actors (R1, R3)

R1 and R3 MUST use the user's Xiaomi phone as one actor and the PC Android emulator (AVD `Medium_Phone_API_36.1`, installed and accelerated with WHPX) as the other, both driven with `adb`. R1: a status change by one actor appears on the other's screen with no manual refresh. R3: suspending a merchant while signed in flips that session to "Account suspended" without re-login. If the emulator cannot be started at apply time, the row MUST be a USER-gap with that reason. R2 (claim race) is closed by executed automated tests (decision of the user, 2026-10-14: what matters is that exactly one claim wins and the loser is told, which the tests prove; a human-versus-adb tap race is neither fair nor repeatable): `backend/tests/rules/claim-concurrency.test.ts`, `FirestoreDispatchRepositoryTest.twoCouriersClaimingTheSameOrderExactlyOneWinsAndTheOtherIsToldItWasTaken` on the device, and `PoolViewModelTest` for the loser's message. No manual tap session is required.

#### Scenario: Live propagation
- GIVEN the phone and the emulator are signed in as different roles on the same order
- WHEN one actor changes the order status
- THEN the other shows the new status with no refresh and R1 gets an `Evidencia:` line

### Requirement: Seed idempotency (O3) and automated rows (O1, O5)

O3: running the seed and admin scripts twice against the emulators MUST leave identical data after the second run (no duplicates, no errors). O5: the demo-script section 8 proofs (JVM unit tests and Firestore rules tests) MUST be rerun and their counts stated in the `Evidencia:` line. O1: the 2026-10-13 61/61 device run MUST be cited and the stale text of tasks 2.4.1, 3.4.1, 4.3.1 and 5.3.1 in the archived `tasks.md` MUST no longer say "device check pending" or "compile-only".

#### Scenario: Second seed run
- GIVEN the seed has run once on clean emulators
- WHEN it runs again
- THEN it exits without error and document counts per collection are unchanged

### Requirement: FirestorePhotoSource instrumented test (O2)

`FirestorePhotoSourceTest` MUST exist under `app/src/androidTest/java/com/otli/app/catalog/adapters/firestore/`, be written test-first, and pass on the phone, bringing the instrumented suite to 8 green classes. If RED cannot be observed because the adapter already works, it MUST be recorded as a characterization test with that exception stated and validated by three temporary mutants of `FirestorePhotoSource.fetch`, each observed failing and then reverted; RED MUST NOT be invented.

#### Scenario: Suite green
- GIVEN the Auth and Firestore emulators are running (photos are Firestore documents)
- WHEN the instrumented suite runs on the phone
- THEN 8 classes pass including `FirestorePhotoSourceTest` and the O2 `Evidencia:` line states the counts

### Requirement: Defects are findings, not silent fixes

A defect found during a check MUST be recorded as FAILED with a finding, MUST NOT be fixed within this change, and MUST be routed to a separate change. MIUI restrictions that block a check MUST be recorded as findings, not worked around silently.

#### Scenario: Defect found
- GIVEN a check shows wrong behavior
- WHEN the row is closed
- THEN it reads FAILED with a finding and no source change appears in this change for it

### Requirement: Verification rules for this change

`sdd-verify` for this change MUST check that (1) every `[x]` device task has an `Evidencia:` line and (2) every `[~]` task is listed in its report. It MUST also search Engram for device-session records (topics `sdd/otli-device-evidence/*` and device-run notes such as #44/#45) in addition to the repo files, because a previous verify missed them.

#### Scenario: Missing Evidencia
- GIVEN a `[x]` device task without an `Evidencia:` line
- WHEN `sdd-verify` runs
- THEN it reports the task as a violation

#### Scenario: Engram-only records
- GIVEN device-session evidence exists only in Engram
- WHEN `sdd-verify` runs
- THEN it finds it by searching Engram and does not report the row as unevidenced
