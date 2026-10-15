# Verify report: otli-device-evidence (branch feat/otli-device-evidence-05-block-b, 2026-10-14)

**Status**: PASS WITH WARNINGS. CRITICAL 0, WARNING 5, SUGGESTION 5.
Native `gentle-ai sdd-status` is NOT available in this install (unknown command): native status is unresolved and was not fabricated. Artifact store: hybrid (openspec files + Engram), both read. Read-only diagnostic: no source, spec, design or tasks file was edited.

## Executive summary

- 36 canonical rows: 35 DONE + 1 FAILED (N2) + 0 USER-gap; 0 `[~]`, 0 `[ ]` in tasks.md (62 `[x]` in total: 36 row tasks + 26 setup/close-out tasks).
- Every `[x]` row task has its own `Evidencia:` line (36 = 36); no placeholders; every Engram id cited (#28, #43, #44, #45, #69, #70, #73, #79, #80, #81, #82) exists and says what the lines say, with the recorded nuances.
- No runtime code changed (`git diff main..HEAD -- app/src/main` empty); the only code is `FirestorePhotoSourceTest` (compiles, exit 0). `DomainPurityTest` sanity run 2/2 green.
- No image tracked in the repo; the gitignore guard works; archived tasks.md has 0 `~~`; the change-local spec is not in `openspec/specs/`.
- Main warning: `docs/demo-script.md` section 8 has `verifyAll` ticked (BUILD SUCCESSFUL in 7m 8s) while tasks 4.2, Engram #79 and #83 say it stays unticked, and no log row or evidence file records that run.
- Instrumented tests could not be run by this verifier (no phone attached); 4/4 and 65/65 rest on the recorded runs.

## 1. Evidencia on every `[x]` device task

| Check | Command | Result |
|---|---|---|
| `[x]` row tasks | `rg -c '^- \[x\] [0-9]+\.[0-9]+ \*\*' tasks.md` | 36 |
| Evidencia lines on their own indented line | `rg -c '^  - Evidencia: [^(]' tasks.md` | 36 |
| Row task without an Evidencia line | awk pairing of each `[x]` row with its next Evidencia | none |
| Placeholder (TBD/TODO/n/a/pending) in an Evidencia line | `rg -i` | none |
| All `[x]` tasks | `rg -c '^- \[x\]'` | 62 (36 rows + 5 Phase 0 + 3 session closes + 7 + 4 + 1 in Phase 3 + 6 Phase 4) |
| Row IDs present | extracted from the 36 `[x]` lines | A1-A4, AD1-AD2, FS1-FS3, G1-G5, L1-L6, M1-M5, N1-N3, O1-O5, R1-R3: exactly the 36 canonical IDs, no duplicate |

Violations: none.

## 2. `[~]` and open tasks

`rg -n '^- \[~\]'` finds none; `rg -n '^- \[ \]'` finds none. Nothing is listed as available-but-unverified.

## 3. Engram cross-check (searched, then mem_get_observation for full content)

| Id | Topic / content | Matches the files? |
|---|---|---|
| #73 explore | inventory summary; the full row table lives in `exploration.md` (M1 row there: DONE by the user on the physical device, 2026-10-13) | Yes. The Engram summary has no row M1 of its own; the row is in the file. |
| #74 proposal, #75 spec, #76 decisions, #77 design | planning mirrors | Partly stale (see W4). #76 still says screenshots are kept locally with sha256 and size; the light regime in the files supersedes it. |
| #78 tasks | mirror "as delivered 2026-10-13" | Stale by design (it says the file is the source of truth). |
| #79 apply-progress | 7 batches, 7 revisions | Consistent with tasks.md and docs/manual-tests.md: 4/4, three mutants failing 3/2/1, 65/65 in 8 classes (13/11/9/5/4/11/8/4), 9+10 device rows, R1/R3, O4 26 PASS / 1 SKIPPED / 0 FAIL / 0 BLOCKED, 36 = 35 DONE + 1 FAILED. |
| #80 N2 finding | N2 re-asked after a first denial; fix goes to a separate change `otli-notification-permission-once` | Matches task 1.27 and the log row N2. Its topic key is `otli/finding-n2-...`, not under `sdd/`; harmless. |
| #81 R2 decision | R2 closed by automated tests, user decision 2026-10-14 | Matches task 2.3 and the R2 paragraph of the spec. Cited tests exist (section 5). |
| #82 blocker note | O4 restart blocked by the phone dropping off USB | Consistent with batch 6 learned item 14 (cable changed); no row depends on it. |
| #83 archive notes | 36 = 35 + 1 + 0, do not promote the spec, follow-ups a-f | Matches the "Archive notes" section of tasks.md, except that item (e) lacks the L2 user confirmation and the `verifyAll` point (W1, W4). |
| #84 L2 decision | user confirmed the dark-mode Sign out button 2026-10-14, no re-check | Matches task 1.2 Evidencia and archive note (e) in the file. |
| #43 | Slice 5, device 48/48, user walk test OK, created 2026-10-02 | Supports M2 and G4 as cited; records no throttle values and no "x ago" label. |
| #44 | 2026-10-03 user review: F.9 dialog OK; F.10 own dot, pickup emphasis, dropoff; customer sees courier; F.11 and F.12 found | Supports M3, M4, G1, M2. Camera fit is not named (recorded nuance). |
| #45 | F.9-F.12 merged and verified on device, 2026-10-04 | Supports M4 (legend) and G3. |
| #28 | 2026-10-08 device session: `FirestoreAdminRepositoryTest` 13/13, F.13 manual scenario on emulator data | Supports AD1. A living note with 20 revisions, so the cited text can drift. |
| #69 | 2026-10-13: logcat 64 PERMISSION_DENIED within 2 s after sign-out, crash was `com.tiktok.manager`; user ran the full flow on production with no errors | Supports A2 and the AD1 reconfirmation exactly as written. |
| #68 | 2026-10-13: API key restriction (package + SHA-1) and Email/Password provider done manually by the user | Independent Engram backing for A4 (the A4 line does not cite it; S1). |
| #37 | Slice 3 summary, F.7 newest-first verified on device | Not cited by the lines; consistent with L6. |
| #70 | verify-report of the archived `otli-mvp` | Cited by M3; exists. |

No row is left unevidenced because its evidence lives only in Engram: the Engram-only rows (M1-M4, G1, G3, A2, AD1) are closed by citation, as the spec requires. Searching `sdd/otli-device-evidence` returned 11 observations (#73-#79, #81-#84); #80 has a different topic key and was fetched by id.

## 4. The 36 rows: one outcome each

Result: 35 DONE + 1 FAILED + 0 USER-gap (the expected shape). Breakdown (36 distinct IDs, each `[x]`):
- DONE with a run in this change (24): L1-L6, A3, O3, O5, M5, G2, G5, A1, FS1-FS3, N1, N3, R1, R3, O1, O2, O4, AD2.
- DONE by citation of an earlier record (8): M1, M2, M3, M4, G1, G3, A2, AD1.
- DONE by decision or citation of the user (3): R2 (tests), G4 (walk test + throttle test), A4 (user statement).
- FAILED (1): N2, with a `finding:` sub-line at tasks.md line 190; its `[x]` carries an Evidencia line plus an "Outcome FAILED" line.
- 24 + 8 + 3 = 35 DONE, plus 1 FAILED = 36. No row reads pending or n/a (the only hit of the word "pending" is the screen name "Approval pending" inside the L2 line).

Spot-check of 11 rows against `docs/manual-tests.md` and `evidence/*.txt`:

| Row | Evidencia claim | Evidence text | Verdict |
|---|---|---|---|
| L1 | status bar 128 px, login y=196, homes y=150 | a1: frame [0,0][1080,128], 196, 150; log row present | consistent |
| L6 | 2 orders 23:24:13Z and 23:24:52Z, 150.00 then 210.00, Placed 17:24, same minute, order checked on createdAt | a1 lines 14-18; log row present | consistent |
| O3 | users 8, merchants 3, couriers 3, settings 1, categories 2+2, products 5+5, Auth 8, no admin script | a1 lines 20-21; log row present | consistent |
| O5 | 1075 JVM, 321/321 in 17 files | a1 lines 22-23; log row (Engram #79) | consistent; not re-run here |
| M5 | map with Delivery address + Courier legend, Updated 1 min ago, position written by the phone | a2 lines 6-9; log row present | consistent |
| G2 | warning + Turn on location button at mode 0, gone at mode 3 | a2 lines 14-17; log row present | consistent |
| FS1 | updatedAt 23:50:54.830Z, 23:51:55.600Z, 23:52:55.557Z, 23:54:57.436Z, screen Dozing | a2 lines 22-24; log row present | consistent; stationary phone, advance by the 60 s heartbeat (stated) |
| N2 | allow path once on merchant and courier; deny path re-asks on customer home | a2 lines 39-42; log row FAILED | consistent |
| N3 | importance=NONE, 0 enqueues, no FATAL, control after grant = 1 | a2 lines 43-45; log row present | consistent |
| R1/R3 | phone updates about 2 s / 3 s after the AVD tap, no touch on the phone, same activity | b lines 11-24; log rows present | consistent |
| O4 | 26 PASS, 1 SKIPPED, 0 FAIL, 0 BLOCKED | o4 SUMMARY; two log rows (agent run, user attestation) | consistent with the summary line; the per-item table was not recounted line by line |

## 5. Spec compliance matrix (specs/device-evidence/spec.md)

| Requirement | Verdict | Proof |
|---|---|---|
| Task states | COMPLIANT | 0 `[~]`, 0 `[ ]`; every `[x]` row has Evidencia (section 1). |
| Evidencia line (what, where, who, date) | COMPLIANT | 36/36, each with where, who and a date. Several are long multi-clause sentences (S3). |
| Manual-test log | COMPLIANT | `docs/manual-tests.md`: exact 7 columns, 26 rows (O2 x3, block A1 9, block A2 10, R1, R3, O4 x2); one apply-progress Engram note per session, merged into #79. |
| Optional artifacts, no screenshots in the repo | COMPLIANT | `git ls-files` shows no png or jpg; `git check-ignore -v` reports `.gitignore:42` and `:43` for `evidence/x/a.png` and `a.jpg`; the evidence folder holds 4 `.txt` files only. |
| Closed set of row outcomes | COMPLIANT | 35 DONE + 1 FAILED (finding at tasks.md line 190) + 0 USER-gap. |
| Already-evidenced rows cited, not redone | COMPLIANT | M1-M4, G1, G3, A2, AD1 cite ids or dates and say they were not repeated; O4 by the user (dated attestation 2026-10-14); AD2 cites O4 section 7. |
| Check families and acceptance criteria | COMPLIANT (recorded limits) | L, N, FS, G2/G5/A1, A3 read against a1/a2 excerpts; G4 closed by user decision 2026-10-14 citing #43 and `LocationThrottleTest` (file exists, covers 10 s / 10 m / 60 s) and stating what each covers; A4 dated user statement 2026-10-13, no production probe by the agent. Scenarios Notifications off and Warning removed on restore are stated in the N3 and G2 lines. |
| Realtime between two actors (R1, R3, R2) | COMPLIANT | R1/R3 with phone + AVD `Medium_Phone_API_36.1` (`b-adb-excerpts.txt`); R2 by tests as the spec itself says: `backend/tests/rules/claim-concurrency.test.ts`, `FirestoreDispatchRepositoryTest.twoCouriersClaimingTheSameOrderExactlyOneWinsAndTheOtherIsToldItWasTaken` (line 232), `PoolViewModelTest.losingTheRaceTellsTheCourierTheOrderWasTaken` (line 236). The string `pool_message_taken` is asserted in `PoolContentTest`, not in `PoolViewModelTest` (S4). |
| Seed idempotency (O3), automated rows (O1, O5) | PARTIAL | O3/O5 compliant. O1: the four archived lines (2.4.1, 3.4.1, 4.3.1, 5.3.1) keep the original sentence and add a "Was pending at the time. Resolved ..." clause (17 such clauses in the archive file). The spec says they MUST no longer say "device check pending" or "compile-only"; the user rule (keep the original, no strikethrough) leaves the original words in place. Intent met, literal wording not (W5). |
| FirestorePhotoSource instrumented test (O2) | COMPLIANT (device run not re-executed here) | File exists (99 lines, the 4 designed cases), `compileDebugAndroidTestKotlin` exit 0; characterization exception and 3 mutants recorded (tasks 3.3-3.6, log rows 7-9); 65/65 in 8 classes recorded (3.7, log row 9). |
| Defects are findings, not silent fixes | COMPLIANT | N2 recorded FAILED; `git diff --stat main..HEAD -- app/src/main` empty; the MIUI install prompt is recorded as an observation in `b-adb-excerpts.txt`, not worked around silently. |
| Verification rules for this change | COMPLIANT | Hand-off written at the end of tasks.md; this verify searched Engram. |
| Spec note: not synced to openspec/specs | COMPLIANT | `openspec/specs/` holds the six capability specs only; no device-evidence file there; archive notes in tasks.md and #83 say so. |
| Archived-tasks fixes keep the original sentence, no strikethrough | COMPLIANT | `rg -c '~~'` on the archived `tasks.md` = 0 (rg exit 1). |

## 6. Coherence with design.md

- Environment: every new-run line says local emulators (a1, a2, b headers; o4 says "No production contact"). Only A4 relies on the user's statement about the production console. The AD1 line (a citation) mentions the user's earlier production flow from #69: a recorded past fact, not production access made by this change.
- R1/R3 two-actor method: followed (phone one actor, AVD `emulator-5554` the other, same APK, per-device `adb reverse`; phone only read between taps). A MIUI install prompt (`INSTALL_FAILED_USER_RESTRICTED` twice) is recorded in task 0.5 and `b-adb-excerpts.txt`.
- O2 characterization test with three mutants: as designed (M1 `return null` killed 3 cases, M2 profile id killed 2, M3 hard-coded `seed-merchant-1` killed 1); each reverted. The design expected M1 to fail cases 1 and 2; it also failed case 4, which is recorded.
- Evidence storage: light regime as designed (one-line Evidencia, one log, small text dumps, no image).
- Deviations from design.md and whether they are recorded:
  - L2 ran with the phone already in night mode (design: set it, then restore): recorded (L2 line, task 1.29, a1 excerpt).
  - FS2 release/cancel and N1 status changes were written into the emulator by the agent (the phone cannot be admin and courier at once): recorded in the lines.
  - R2, G4, A4 moved from USER to closed-by-decision/citation after design.md was written: recorded in spec.md, tasks.md and Engram #81, but not in design.md ADR-E4, proposal.md or exploration.md (S2).
  - No separate admin script for O3: recorded (the seed creates the admin).
  - The final PR chain differs from the design.md Sequencing table: recorded in the tasks.md forecast.

## 7. Strict TDD for the code task (3.1-3.8)

- `git diff --stat main..HEAD -- app/src/main`: empty. Non-doc files changed on the chain: `.gitignore`, `FirestorePhotoSourceTest.kt`, and two that belong to the previous branch of the chain, not to this change: `app/src/test/java/com/otli/app/DomainPurityTest.kt` and an 8-line `tasks.withType<Test>` input declaration in `app/build.gradle.kts` (commit ff2829f). Reported, not a defect of this change.
- The honest exception holds: no RED invented; three mutants each seen failing and reverted; the `productPhotos` read rule is `signedIn()` (task 3.1, firestore.rules:157), so case 4 was valid.
- Run by this verifier: `./gradlew compileDebugAndroidTestKotlin` exit 0; `./gradlew testDebugUnitTest --tests "com.otli.app.DomainPurityTest"` exit 0, tests=2 skipped=0 failures=0.
- Not run: the instrumented suite (no phone attached to this verifier) and the full JVM/rules suites (run earlier today per the hand-off: 1075 JVM, 321/321 rules, 65/65 instrumented). The recorded results are internally consistent across tasks.md (3.3-3.8, 1.9, 4.2), `docs/manual-tests.md` (rows 7-9, 18) and Engram #79.

## 8. Known limits

Recorded (tasks.md archive notes, Engram #83):
- M1, AD1, M3 citations rest on summary records (#73, #69, #28, #44); M3 camera fit is not named in #44.
- L2 contrast judged by the agent's eye on an unsaved screenshot, then confirmed by the user in chat 2026-10-14; light mode not tested.
- 7d.1 All orders list exercised with one customer and one store.
- G4: walk test #43 records no exact throttle values; the throttle is covered by `LocationThrottleTest`.
- N2 FAILED: the app re-asks POST_NOTIFICATIONS on every home entry after a first plain denial; follow-up change suggested `otli-notification-permission-once`.
- 5.2 claim race never done by hand (R2 by tests); 6.1 courier dot confirmed visually; the 5.3 demo-script wording was stale and was fixed.
- The 6.3 summary line of the archived `tasks.md` still says "pending on device" for `06-3b-admin-repository` (reported by apply, deliberately left).

New in this verify: W1-W5 and S1-S5 below.

## Issues

### CRITICAL
None.

### WARNING
- W1. `docs/demo-script.md` section 8 has `./gradlew verifyAll` ticked (2026-10-14: BUILD SUCCESSFUL in 7m 8s), added in commit 373b370. tasks.md 4.2, Engram #79 batch 7 and #83 say it stays unticked because `lintDebug` and `verifyAll` were not run. No log row, evidence file or Evidencia line records the verifyAll run. The hand-off says it was green on 2026-10-14, but this verifier did not re-run it and cannot confirm it from the repo. Record the run in `docs/manual-tests.md` or correct 4.2, and update #79 and #83.
- W2. Citation-backed rows are weaker than device runs: M1 (no record of its own), M2 (neither #43 nor #44 names the "x ago" label), M3 (camera fit unconfirmed), AD1 (cites #28, a living note with 20 revisions; the production "no errors" is a user statement in #69). All are disclosed in the lines and archive notes.
- W3. Evidence that cannot be re-audited: L2 contrast, M5 map dots and G5 marker absence (read from legend nodes, not pixels) rest on the agent description; screenshots are deliberately not kept. FS2 release/cancel and N1 statuses were simulated by REST writes. All stated in the lines.
- W4. Engram mirrors are stale against the files: #75 (spec) still says R2 is a user confirmation or USER-gap; #78 (tasks) is the 2026-10-13 plan; #76 mentions screenshots with sha256; #83 item (e) lacks the L2 confirmation. The files are the source of truth; archive should not rely on those Engram copies.
- W5. The archived `otli-mvp/tasks.md` still contains the original "device check pending", "device run pending" and "GREEN pending" words in F.1-F.13, 4.3.1 and 5.3.1 (each followed by an appended "Was pending at the time. Resolved ..." clause), so a grep for them still matches. Task 3.12 acceptance (matches only inside struck text) and spec O1 (MUST no longer say) conflict with the later user rule (keep the original, no strikethrough). Intent met, literal acceptance text not.

### SUGGESTION
- S1. Cite Engram #68 in the A4 Evidencia line (independent record that the API key restriction was done by the user, 2026-10-13).
- S2. Update `design.md` ADR-E4, `proposal.md` and `exploration.md` (or add a one-line note) so R2, G4 and A4 no longer read as USER rows.
- S3. Several Evidencia lines (O4, N2, 1.2, 1.17) are long multi-clause sentences; the spec asks for one sentence. Not a violation of the mechanical rule.
- S4. R2 cites `PoolViewModelTest` for `pool_message_taken`; that string is asserted in `PoolContentTest`, while `PoolViewModelTest` asserts `PoolMessage.ALREADY_TAKEN`. Cite both.
- S5. N2 is `[x]` with FAILED in a sub-line, so the mechanical count of 36 `[x]` includes it. State 35 DONE + 1 FAILED next to the count in the hand-off, and add the user confirmation of L2 to the L2 row of `docs/manual-tests.md`.

## Executed checks

| Command | Result |
|---|---|
| rg counts, awk pairing and rg -n for open or tilde tasks on tasks.md | 36, 36, 0 missing, 0 `[~]`, 0 `[ ]` |
| `git ls-files` filtered for png and jpg | no output |
| `git check-ignore -v` on evidence/x/a.png and a.jpg | both ignored by `.gitignore:42` and `:43` |
| `rg -c` for a double tilde on the archived tasks.md | 0 matches |
| `git diff --stat main..HEAD -- app/src/main` | empty |
| `./gradlew compileDebugAndroidTestKotlin` | exit 0 |
| `./gradlew testDebugUnitTest --tests "com.otli.app.DomainPurityTest"` | exit 0, 2 tests, 0 failures |
| Instrumented suite on the phone | UNAVAILABLE (no device attached to this verifier) |
| Full JVM, rules and `verifyAll` | NOT re-run (per instruction); recorded results only |
| Native `gentle-ai sdd-status` | UNAVAILABLE (unknown command) |

## next_recommended

`sdd-archive` (no CRITICAL). Before archiving, resolve W1 (one line in the log or in 4.2) and keep the change-local spec inside the archived folder (do not promote it to `openspec/specs/`). Queue a separate change `otli-notification-permission-once` for the N2 finding.
