# Proposal: otli-device-evidence

## Intent

`otli-mvp` was archived with PASS WITH WARNINGS. Device-only checks either have no evidence, or their evidence lives only in Engram and never reached `tasks.md`; fourteen archived tasks still read "device check pending", and task 2.4.1 cites an instrumented test (`FirestorePhotoSourceTest`) that does not exist. This change closes those gaps under a LIGHT evidence regime (user decision 2026-10-13): work that is really done must not stay "pending", and nothing marked done may lack backing. It is an evidence change, not a feature change.

Evidence rule: `tasks.md` states are `[ ]` not done, `[~]` implemented/available but NOT yet verified on the device, `[x]` done. A device-check task is `[x]` only with an `Evidencia:` line of one sentence (what was seen, where: phone / PC emulator / production, who: agent via adb / user, date). One manual-test log, `docs/manual-tests.md` (date | row IDs | what was tested | result | where | who | reference), is appended at the end of every device session, together with one Engram note under `sdd/otli-device-evidence/apply-progress`. No screenshots are required or committed; small text dumps are optional. No sha256, size budget, settings record or naming scheme.

## Scope

### In Scope
- **Group A, adb-driven checks** (agent drives the Xiaomi phone against local Firebase emulators, each closed with an `Evidencia:` line): L1-L6, M5, G2, G5, FS1-FS3, N1-N3, A1, A3; plus automated reruns O3 (seed/admin idempotency) and O5 (demo-script section 8 proofs).
- **Group B, checks needing the person** (closed with an `Evidencia:` line citing the user's dated confirmation, or recorded as an explicit USER-gap with a reason): R1 and R3 (Xiaomi phone plus the PC emulator, AVD `Medium_Phone_API_36.1`), R2 (literal human tap race), G4, A4, O4. UPDATE 2026-10-14 (later user decisions): R2 was closed by executed automated tests instead of a manual race, and G4 by citing the 2026-10-02 walk plus `LocationThrottleTest`; A4 is the user's dated statement; O4 was run by the agent and confirmed by the user per section.
- **Group C, code and text gaps**: write `FirestorePhotoSourceTest` (androidTest) test-first to close 2.4.1 (O2); correct the stale text of 2.4.1, 3.4.1, 4.3.1, 5.3.1 to reflect the 61/61 device run of 2026-10-13 (O1).
- **Already-done rows** (M1-M4, G1, G3, A2, AD1, AD2): cite the existing evidence (Engram id or date) in their `Evidencia:` line; not re-executed.
- `docs/manual-tests.md`, the single manual-test log, plus the task states and `Evidencia:` lines in `tasks.md`; each device session also writes one Engram apply-progress note.
- `sdd-verify` for this change checks that every `[x]` device task has an `Evidencia:` line, lists every `[~]`, and searches Engram for device-session records (the previous verify missed #44/#45).

### Out of Scope
- New features, UI changes, or behaviour fixes. A defect found during a check is recorded as a finding and becomes a separate change (or an explicitly authorized task), never a silent fix here.
- Acquiring a second physical phone.
- Changing production data; production project `otli-39efc` is used only if a check cannot be reproduced on the emulators, and only with explicit user authorization.
- Rewriting the six main specs (see open question on spec gaps).

## Capabilities

### New Capabilities
None.

### Modified Capabilities
None. Behaviour is unchanged; this change produces evidence and one missing adapter test. (Pending open question 4 on whether the spec gaps for insets/dark mode, foreground service and order notifications should gain scenarios.)

## Approach

1. Build the debug app against local emulators (`adb reverse` for the emulator ports) on device `xkqwijtcyttsaahi`; seed with the existing guarded seed scripts.
2. For Group A, drive each check with `adb` (`input`, `am`, `cmd location`, `pm revoke`, `svc`, `cmd notification`, `dumpsys activity services`, `dumpsys notification`, logcat). Where a check needs a second actor (N1 status changes, R1), drive the counterpart through emulator-side writes (admin/seed scripts or Firestore emulator REST) while the phone observes.
3. Mark each task `[x]` with its one-sentence `Evidencia:` line (or `[~]`, USER-gap, FAILED) as the check is done; at the end of every device session append rows to `docs/manual-tests.md` and write one Engram note. Small text dumps under `evidence/` are optional.
4. For Group B, present each check to the user with exact steps; record the dated confirmation in the `Evidencia:` line, or a USER-gap with the stated reason.
5. For Group C, strict TDD: write `FirestorePhotoSourceTest` against the Storage/Firestore emulators, observe RED where meaningful, then GREEN on the phone; then fix the stale task text in the archived `tasks.md`.

## Affected Areas

| Area | Impact | Description |
|------|--------|-------------|
| `openspec/changes/otli-device-evidence/` | New | Task states and `Evidencia:` lines; optional small text dumps |
| `docs/manual-tests.md` | New | Single manual-test log |
| `.gitignore` | Modified | Two-line guard against committing screenshots |
| `openspec/changes/archive/2026-10-13-otli-mvp/tasks.md` | Modified | Stale text of 2.4.1, 3.4.1, 4.3.1, 5.3.1; pending device rows point to new evidence |
| `app/src/androidTest/java/com/otli/app/catalog/adapters/firestore/FirestorePhotoSourceTest.kt` | New | Missing adapter test for task 2.4.1 |
| `docs/demo-script.md` | Modified | Tick sections with evidence (O4, O5) |

## Risks

| Risk | Likelihood | Mitigation |
|------|------------|------------|
| A check reveals a real defect | Med | Record as finding, keep row open as FAILED with evidence, route fix to a separate change; never mark DONE |
| Emulator behaviour differs from production (FCM-less local notifications, API key restriction) | Med | A4 stays USER; the `Evidencia:` line always states where (phone / PC emulator / production) |
| MIUI restrictions (autostart, battery saver, notification permission UI) alter FS1/N2/N3 | Med | Mention a blocking setting in the `Evidencia:` line or finding; treat MIUI-specific blocks as findings; no per-artifact settings record |
| Screenshots bloat the repository | Low | Resolved by the user (2026-10-13, later decision, supersedes the earlier sha256 rule): no screenshots are required or committed, never the only evidence; a two-line `.gitignore` guard prevents accidents; no hashes. Small text dumps may be committed |
| A light `Evidencia:` line is only as honest as its author | Low | `sdd-verify` checks every `[x]` device task has the line, lists every `[~]`, and searches Engram |
| `FirestorePhotoSource` is hard to exercise on emulators | Low | Design phase decides fixture strategy; strict TDD still applies |
| RED cannot be observed for a test written against existing, working code | Med | Record the exception honestly (characterization test) instead of inventing RED |
| Inventory count mismatch: the brief cites 33 checks, the exploration tables hold 36 rows | Low | Spec/design fix the canonical list before tasks |

## Rollback Plan

Pure additive evidence plus one test. Revert the change's commits (log, optional text dumps, archived `tasks.md` text edits, `demo-script.md` ticks, and the new androidTest) with `git revert`; no runtime code, data model, or production data is touched. Emulator data is disposable.

## Dependencies

- Xiaomi phone `xkqwijtcyttsaahi` (model 2510DRA23L) on USB, `adb` from `%LOCALAPPDATA%\Android\Sdk\platform-tools`.
- Local Firebase emulators and the existing seed/admin scripts.
- User availability for Group B, and for A4 (Firebase/Google Cloud console).

## Success Criteria

- [ ] Every inventory row ends as DONE (`[x]` with a one-sentence `Evidencia:` line: what, where, who, date), or as an explicit USER-gap (or FAILED finding) with a recorded reason. No row reads "pending"; every available-but-unverified item is `[~]`.
- [ ] No archived `otli-mvp` task still says "device check pending" or "compile-only".
- [ ] `FirestorePhotoSourceTest` exists and passes on the phone; the instrumented suite reports 8 classes green.
- [ ] `docs/manual-tests.md` has the rows of every device session and the Engram apply-progress notes agree with it; `sdd-verify` finds no `[x]` device task without `Evidencia:`.

## Open Product Questions

1. R1 and R3 need two devices: RESOLVED (user): Xiaomi phone plus a PC Android emulator. The open question about installing the emulator is also RESOLVED: the package is installed (version 37.2.12, WHPX acceleration OK, AVD `Medium_Phone_API_36.1`, image android-36.1 google_apis_playstore x86_64), so no authorization is needed.
2. For already-done rows whose evidence lives only in Engram (M2-M4, G1, G3, AD1), is citing the Engram id enough, or must each be re-executed and captured now?
3. AD2 and O4 rest on the broad 2026-10-13 production run: accept that confirmation, or redo each item against the emulators?
4. Should the spec gaps (insets/dark mode, foreground-service lifecycle, order notifications, "own position unknown") gain spec scenarios in this change, or stay out of scope?
5. Artifacts and screenshots: RESOLVED (user, 2026-10-13, light regime): no screenshots committed or required, no hashes; optional small text dumps may be committed under the change's `evidence/` folder.
6. Which inventory is canonical: 33 checks (brief) or the 36 rows in the exploration tables? RESOLVED: 36 rows (33 was a counting error).
