# Archive report: otli-device-evidence

**Archived**: 2026-10-14
**Archived to**: `openspec/changes/archive/2026-10-14-otli-device-evidence/`
**Store**: hybrid (files under `openspec/changes/` plus Engram project `otli-delivery-app`)
**Branch at archive**: `feat/otli-device-evidence-05-block-b`
**Status**: archived and closed. Implementation complete; the one FAILED row (N2) is queued as a separate change.

This report is the terminal record of the change as of close. Where an earlier snapshot (`verify-report.md`, Engram #85, written 2026-10-14 20:21) disagrees with a later fact, the later fact is reported here and the snapshot claim is attributed to its source and time.

## 1. Final state at close

Ranking used (per sdd-archive Final-State Authority): (1) the persisted `tasks.md`; (2) explicit final-state facts from the orchestrator launch prompt; (3) `verify-report` and apply-progress snapshots (history only).

- **Tasks** (`tasks.md`, archived copy): 62 of 62 checkboxes `[x]`; 0 `[ ]`; 0 `[~]`. The 62 are 36 canonical row tasks plus 26 setup and close-out tasks.
- **Canonical rows (36)**: 35 DONE + 1 FAILED (N2) + 0 USER-gap. Each DONE row has an `Evidencia:` line.
- **Runtime code**: no runtime code changed by this change. `git diff main..HEAD -- app/src/main` is empty. The only code added is the instrumented characterization test `FirestorePhotoSourceTest` (4 cases), validated by 3 temporary mutants that were each observed failing and then reverted.
- **Tests at close** (launch prompt, 2026-10-14; highest-ranked source for these numbers):
  - 1075 JVM unit tests (includes 2 `DomainPurityTest`).
  - 321/321 rules tests (17 files).
  - `./gradlew verifyAll` BUILD SUCCESSFUL in 7m 8s.
  - 65/65 instrumented tests in 8 classes on the Xiaomi 2510DRA23L (includes `FirestorePhotoSourceTest` 4/4).
  - Numbers from `verify-report` (61/61 instrumented, 1075 JVM, 321 rules, "full JVM/rules/verifyAll not re-run") are superseded by the figures above.
- **Verify report**: `verify-report.md` (Engram #85), status PASS WITH WARNINGS, CRITICAL 0, WARNING 5, SUGGESTION 5. Historical at its time. Status of its warnings at close:
  - W1 (`verifyAll` run not logged, demo-script section 8 ticked): fixed after the verify. Log row O5 in `docs/manual-tests.md` (2026-10-14, BUILD SUCCESSFUL 7m 8s, 321/321), note on task 4.2, commit `dd4615b`.
  - W5 (archived otli-mvp text keeps the original "pending" words next to "Resolved ..."): resolved by correcting the acceptance wording of task 3.12, commit `dd4615b`. Per the user's rule, the original sentences are kept on purpose, with no strikethrough.
  - W2, W3, W4: still open. Listed in section 4.
  - The verify-report's "S1 cite #68 in A4" and "S2 update design/proposal" suggestions: addressed by `dd4615b` (A4 cites #68; design/proposal have dated UPDATE notes).
- **Native status**: `gentle-ai sdd-status` is unavailable in this install (unknown command). Native status is unresolved and is not fabricated in this report.

## 2. Outcome summary (36 canonical rows)

| Family | Rows | Outcome |
|---|---|---|
| Layout and theme | L1-L6 | DONE (L2 confirmed by user, 2026-10-14) |
| Mapping | M1-M5 | DONE (M1-M4 by citation; M5 run) |
| Location | G1-G5 | DONE (G1, G3 by citation; G4 by citation of #43 and `LocationThrottleTest`; G2, G5 run) |
| Foreground service | FS1-FS3 | DONE (run on the Xiaomi) |
| Notifications | N1, N3 | DONE |
| Notifications | N2 | **FAILED** (finding; fix is a separate change) |
| Realtime | R1, R3 | DONE (two-actor, Xiaomi plus AVD `Medium_Phone_API_36.1`) |
| Realtime | R2 | DONE by executed automated tests (user decision 2026-10-14) |
| Auth and admin | A1, A2, A3, A4 | DONE (A2 by citation; A4 by user statement, cites #68) |
| Admin and demo | AD1, AD2 | DONE (AD1 by citation; AD2 covered by O4) |
| Ops and code | O1-O5 | DONE (O2 `FirestorePhotoSourceTest`; O4 walkthrough, confirmed by the user; O5 reruns) |

Counts: 35 DONE, 1 FAILED (N2), 0 USER-gap. Of the DONE rows, 8 are by citation of earlier records (M1-M4, G1, G3, A2, AD1); R2, G4 and A4 are closed by tests, citation or user statement as recorded in section 3.

## 3. User decisions that shaped the change

1. Screenshots are never stored in the repo. Text artifacts (uiautomator dump, dumpsys, logcat excerpts) may be committed under `evidence/`.
2. Light evidence regime: states `[ ]` / `[~]` / `[x]`, one sentence `Evidencia:` line per device task, single log `docs/manual-tests.md`, one Engram note per device session.
3. R1 and R3 two-actor checks use the Xiaomi phone plus a PC Android emulator (AVD `Medium_Phone_API_36.1`), driven with adb.
4. R2 closed by executed automated tests (`backend/tests/rules/claim-concurrency.test.ts`, `FirestoreDispatchRepositoryTest.twoCouriersClaimingTheSameOrderExactlyOneWinsAndTheOtherIsToldItWasTaken`, `PoolViewModelTest`). No manual tap session. Recorded in Engram #81.
5. G4 closed by citation (Engram #43 plus `LocationThrottleTest`, which covers 10 s / 10 m / 60 s).
6. A4 closed by the user's statement (Engram #68 as supporting record).
7. O4 demo-script walkthrough: run by the agent via adb on the Xiaomi and AVD (26 PASS, 1 SKIPPED, 0 FAIL, 0 BLOCKED); confirmed by the user per section (sections 2 to 7). The user also confirmed the dark-mode Sign out button for L2 (Engram #84).
8. Preflight: interactive, hybrid store, auto-chain PRs. Stacked-to-main delivery.
9. Engram #76 recorded the decisions above.

## 4. Known gaps and warnings still open at close

These are carried forward as recorded limits. Nothing here is described as verified beyond what the cited evidence supports.

- **W2, citation strength**: M1, M2, M3 and AD1 rest on summary records (Engram #28 is a living note with 20 revisions; #43 and #44 do not name the "x ago" label or the camera fit; #69 is the logcat record for A2 and AD1). M3 camera fit is "manual-only per verify-report #70" as recorded by the agent.
- **W3, evidence not re-auditable**: L2 contrast was judged by the agent by eye on a local unsaved screenshot and then confirmed by the user (2026-10-14). M5 dots and G5 marker absence were read from Compose legend nodes, because markers are not in the accessibility tree. FS2 release/cancel and N1 status changes were written by REST on the emulator.
- **W4, Engram mirrors stale against the files**: #75 (spec, still says R2 "not closed by tests"), #78 (tasks plan), #76 (mentions sha256/size, which the light regime dropped), #83 (archive-notes item e). The files are the source of truth. Engram #81 supersedes the R2 wording in #75.
- **Light mode untested**: L2 was done in dark mode only. Light mode was not tested.
- **Single-customer, single-store coverage**: demo-script 7d.1 (All orders list) was exercised with one customer and one store only.
- **G4 throttle values**: proven only by `LocationThrottleTest`; the walk test does not record exact values.
- **M5 / 6.1 markers**: map markers are not in the accessibility tree. The courier dot in demo-script 6.1 was confirmed visually by the user.
- **Demo-script section 5.3 wording**: stale in the demo script; a courier with an active delivery does not see the pool, so "Finish your current delivery before claiming another order." is unreachable from the UI. The demo script was corrected in this change; the behaviour (switch disabled, "Finish your delivery to go offline.") was observed.
- **Demo-script section 5.2** (claim race by hand): not performed; R2 closed by automated tests.
- **Archived otli-mvp**: a summary line in its 6.3 PR-slice text still says the 06-3b-admin-repository instrumented test is "pending on device". Left untouched by design (archived folders are not edited).
- **Instrumented suite**: 65/65 is recorded from the Xiaomi run on 2026-10-14. The verifier could not re-run it (no device at verify time).

## 5. Follow-ups (not done by this change)

1. **FAILED N2**: the app asks `POST_NOTIFICATIONS` on every home entry and keeps no memory of a plain denial (Engram #80). Fix in a separate SDD change, test-first, suggested name `otli-notification-permission-once`.
2. **Spec gaps** (no scenarios in the main specs): insets and dark mode; foreground-service lifecycle; order notifications; "own position unknown". Candidates for a later spec change.
3. **Light mode** UI check is still open.
4. **Weak citations** M1, M3 and AD1: re-run only if the user wants stronger evidence.
5. **Engram mirrors** #75, #76, #78, #83 are stale against the files. Refresh them only through a new save if needed; the files govern.

## 6. Change-local spec was NOT promoted

`specs/device-evidence/spec.md` is a change-local verification protocol for this change only. It adds no product behaviour. It was NOT synced or merged into `openspec/specs/`. `openspec/specs/` keeps its six capabilities unchanged: `admin`, `auth-roles`, `courier-dispatch`, `live-tracking`, `merchant-catalog`, `ordering`. No delta merge was performed in this archive step (sdd-archive Step 2 delta sync is skipped by decision; there is no main spec to update).

## 7. Mechanical archive readback

Procedure (sdd-archive Step 3):

1. Pre-move snapshot: `cp -R openspec/changes/otli-device-evidence <scratchpad>/snapshot`.
2. Move: `git mv openspec/changes/otli-device-evidence openspec/changes/archive/2026-10-14-otli-device-evidence` (exit 0). Destination was confirmed absent before the move. Source confirmed absent after the move.
3. Readback: `diff -r <scratchpad>/snapshot openspec/changes/archive/2026-10-14-otli-device-evidence` produced no output, exit 0.

This archive-report was added to the archived folder after that readback; it is additive and was not in the source snapshot.

Archived folder contents (10 tracked files before this report): `exploration.md`, `proposal.md`, `specs/device-evidence/spec.md`, `design.md`, `tasks.md`, `verify-report.md`, `evidence/a1-adb-excerpts.txt`, `evidence/a2-adb-excerpts.txt`, `evidence/b-adb-excerpts.txt`, `evidence/o4-walkthrough.txt` (text only, no images). Plus this `archive-report.md`.

`openspec/changes/` after the move contains only `archive/`.

## 8. Engram observations read for this archive

Retrieved with `mem_get_observation` in full (not from search previews):

| ID | Topic key | Role |
|---|---|---|
| #73 | `sdd/otli-device-evidence/explore` | exploration |
| #74 | `sdd/otli-device-evidence/proposal` | proposal |
| #75 | `sdd/otli-device-evidence/spec` | spec (condensed; file is source) |
| #76 | `sdd/otli-device-evidence/decisions` | user decisions |
| #77 | `sdd/otli-device-evidence/design` | design |
| #78 | `sdd/otli-device-evidence/tasks` | tasks plan (stale mirror) |
| #79 | `sdd/otli-device-evidence/apply-progress` | apply-progress (snapshot; cumulative to batch 7) |
| #80 | N2 finding (topic `otli/finding-n2-notification-permission`) | FAILED finding |
| #81 | `sdd/otli-device-evidence/decision-r2` | decision R2 |
| #82 | `sdd/otli-device-evidence/o4-restart-blocker` | discovery (O4 restart blocked, historical) |
| #83 | `sdd/otli-device-evidence/archive-notes` | archive notes |
| #84 | `sdd/otli-device-evidence/decision-l2` | decision L2 |
| #85 | `sdd/otli-device-evidence/verify-report` | verify report (snapshot) |
| #43 | session summary (otli-mvp) | cited for G4 and M2 |
| #44 | `otli/f9-f10-review` | cited for G1, M2, M3, M4 |
| #45 | session summary (otli-mvp) | cited for G3 |
| #68 | `otli/production-firebase` | cited for A4 |

Not read by this phase but cited by the verify report: #28, #37, #39, #40, #69, #70. They are outside this archive's required list.

## 9. Archive-phase verdict

- Mechanical move and readback: passed (empty `diff -r`).
- Spec sync: skipped by decision (change-local protocol, not promoted).
- Engram: archive report saved as `sdd/otli-device-evidence/archive-report` (type architecture, capture_prompt false).
- Commits: none made by this phase. The git index holds the staged rename; the orchestrator commits.
