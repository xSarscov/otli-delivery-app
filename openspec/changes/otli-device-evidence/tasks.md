# Tasks: Otli Device Evidence (close the 36 device-evidence rows of `otli-mvp`)

Light evidence regime (user decision 2026-10-13). Source of truth for the rules: `specs/device-evidence/spec.md`; for techniques: `design.md`; row meanings: `exploration.md`. This change adds no product behavior: its only code is one instrumented characterization test.

## Review Workload Forecast

| Field | Value |
|-------|-------|
| Estimated changed lines | Planning docs ~760 (docs only, sliced in 3); apply work ~540 (PR 02 ~150, PR 03 ~120, PR 04 ~120, PR 05 ~150) |
| 400-line budget risk | High as a whole change; every individual PR is planned under 400 (Low/Medium) |
| Chained PRs recommended | Yes |
| Suggested split | PR 00 (archive, already exists) → 01a → 01b → 01c → 02 → 03 → 04 → 05 |
| Delivery strategy | auto-chain |
| Chain strategy | stacked-to-main |
| `size:exception` needed | No PR in this chain needs it (see slice table); PR 00 is outside this change and must be measured on its own |

```text
Decision needed before apply: No
Chained PRs recommended: Yes
Chain strategy: stacked-to-main
400-line budget risk: High
```

Rationale: the total is well over 400 lines, but it is almost all documentation and small task-line edits, and each slice is a standalone deliverable. The light regime (no screenshots, no hashes, no per-artifact records) removed the evidence-file volume the earlier forecast carried: the plan was ~600 across two slices, now measured as ~760 (exploration 103 + proposal 89 + spec 147 + design 136 + this file 281 lines) and cut in three so that none exceeds 400. Apply PRs are lower than the earlier figures because evidence lives in one-sentence `Evidencia:` lines and a short log table.

### State of the branches (2026-10-13, all LOCAL and UNPUSHED)

- `docs/otli-mvp-archive`: archive of `otli-mvp` plus the `DomainPurityTest` commit. This is PR 00. It is not part of this change's budget; run `git diff --stat main..docs/otli-mvp-archive` before pushing and decide separately whether it is a moves-only archive (rename-heavy) or needs its own slicing. It targets `main`.
- `feat/otli-device-evidence-01-plan` is stacked on `docs/otli-mvp-archive` and holds the planning artifacts of this change (exploration, proposal, spec, design, tasks). At planning time the git status snapshot shows them as untracked; they must be committed as three work-unit commits (exploration+proposal, spec+design, tasks) so the slices below can be cut without rewriting content. Keep `feat/otli-device-evidence-01-plan` as the tip of the plan slices and cut `-01a` and `-01b` branches at the earlier commits.
- Stacked-to-main mechanics: the first PR in the chain (PR 00) targets `main`; every later PR targets the previous PR's branch and is retargeted to `main` when its predecessor merges. If GitHub shows a predecessor's changes in a child diff, the base is wrong: retarget or rebase before review. Nothing is pushed or opened by this phase; push, PR creation and merge stay human decisions.

### Chained PR slice table (stacked-to-main)

| PR | Branch (suggested) | Targets | Holds | Est. changed lines | `size:exception` |
|----|--------------------|---------|-------|--------------------|------------------|
| 00 | `docs/otli-mvp-archive` (exists, local) | `main` | archive of `otli-mvp`, `DomainPurityTest` commit | not in this budget; measure before push | decide on its own |
| 01a | `feat/otli-device-evidence-01a-explore-proposal` | PR 00 | `exploration.md`, `proposal.md` | ~190 | No |
| 01b | `feat/otli-device-evidence-01b-spec-design` | 01a | `specs/device-evidence/spec.md`, `design.md` | ~285 | No |
| 01c | `feat/otli-device-evidence-01-plan` (exists, local) | 01b | this `tasks.md` | ~280 | No |
| 02 | `feat/otli-device-evidence-02-group-c` | 01c | Phase 0 repo files (0.1, 0.2), Phase 3 (C): `FirestorePhotoSourceTest`, archived text fixes, O1/O2 | ~150 | No |
| 03 | `feat/otli-device-evidence-03-adb-a1` | 02 | Phase 1 block A1: L1-L6, A3, O3, O5, citations of the 8 evidenced rows, log rows | ~120 | No |
| 04 | `feat/otli-device-evidence-04-adb-a2` | 03 | Phase 1 block A2: M5, G2, G5, A1, FS1-FS3, N1-N3, log rows | ~120 | No |
| 05 | `feat/otli-device-evidence-05-user-close` | 04 | Phase 2 (B): R1, R3, R2, G4, A4, O4, AD2; Phase 4 (D): demo-script ticks, archived F.x pointers, final sweep, archive notes | ~150 | No |

If PR 05 grows past 400 (many F.x pointers), split the archived F.x pointer edits into a PR 05b instead of asking for `size:exception`. Order rationale: PR 02 first because it adds the `.gitignore` guard and the log file that every device session needs; PR 05 last because it depends on user time.

### Suggested Work Units

| Unit | Goal | Likely PR | Focused test command | Runtime harness | Rollback boundary |
|------|------|-----------|----------------------|-----------------|-------------------|
| 1 | Planning docs, part 1 | PR 01a | `rg -c '' openspec/changes/otli-device-evidence/exploration.md openspec/changes/otli-device-evidence/proposal.md` (files present) | N/A: documentation only, no runtime boundary | Revert the commit; nothing depends on it |
| 2 | Planning docs, part 2 | PR 01b | `rg -n 'Requirement:' openspec/changes/otli-device-evidence/specs/device-evidence/spec.md` | N/A: documentation only | Revert; PR 01c reads it but no code does |
| 3 | Task plan | PR 01c | `rg -c '^- \[' openspec/changes/otli-device-evidence/tasks.md` (36 row tasks plus setup, support and close-out tasks) | N/A: documentation only | Revert the file |
| 4 | Guard, log file, O2 test, O1 text fixes | PR 02 | `ANDROID_SERIAL=xkqwijtcyttsaahi npm --prefix backend run test:android` (8 classes, 65 tests) | The same run on the phone against fresh emulators; three mutants run one class with `-Pandroid.testInstrumentationRunnerArguments.class=...` | Delete `FirestorePhotoSourceTest.kt`, the two `.gitignore` lines, `docs/manual-tests.md`, and revert the archived task text; no runtime code is touched |
| 5 | adb checks block A1 | PR 03 | `./gradlew testDebugUnitTest` and `npm --prefix backend test` for O5 | adb on the phone against local emulators (techniques in `design.md`) | Revert task-line states, log rows and optional dumps; nothing else depends on them |
| 6 | adb checks block A2 | PR 04 | none (device checks); O5 counts come from PR 03 | adb on the phone against local emulators | Revert task-line states, log rows and optional dumps |
| 7 | User closes, demo ticks, archive pointers | PR 05 | `rg -n 'device check pending' openspec/changes/archive/2026-10-13-otli-mvp/tasks.md` (only USER-gap rows may remain, each pointing at its reason) | Phone + AVD two-actor runs (R1, R3) and the user's walkthrough (O4) | Revert the task states, demo-script ticks and archived pointers |

## How to read and fill this file

Row line format (from the spec and design): `- [ ] N.N **ID** action` followed by an indented `Evidencia:` line.

- `[ ]` not done. `[~]` implemented or available but NOT yet verified on the device (must be listed by `sdd-verify`; never presented as done). `[x]` done, only with an `Evidencia:` line.
- `Evidencia:` is ONE sentence: what was seen, where (phone / PC emulator / production), who (agent via adb / user), date. Spanish is used for these lines, as in the spec example. Example: `Evidencia: el aviso del GPS aparece y se va al restaurar, probado por el usuario en el Xiaomi (emulador local), 2026-10-14.`
- A row ends as exactly one of: DONE (`[x]` + `Evidencia:`), USER-gap (add `reason: <one line>` and the date), FAILED (add `finding: <one line>`; the fix goes to a separate change, never to this one). Never "pending", "n/a" or unlisted.
- Every task below carries `PR:` (the slice that holds its edit) and, for the 36 row tasks, an acceptance criterion tied to the spec requirement `Check families and acceptance criteria` unless another requirement is named.
- Screenshots are never committed (the `.gitignore` guard in 0.1 enforces it). Small text dumps are optional under `openspec/changes/otli-device-evidence/evidence/`.
- Device session routine: at the end of every device session (tasks 1.18, 1.29, 2.8) append rows to `docs/manual-tests.md` and write one Engram note, topic `sdd/otli-device-evidence/apply-progress`.
- MIUI/HyperOS blocks (USB debugging Security settings, Install via USB, Autostart, battery saver, per-app notification toggle) are findings, never silent workarounds (spec: `Defects are findings, not silent fixes`).

Apply order (not the same as the phase numbers): Phase 0 → Phase 3 (C, PR 02) → Phase 1 block A1 (PR 03) → Phase 1 block A2 (PR 04) → Phase 2 (B) and Phase 4 (D) (PR 05).

## Phase 0: Setup and tooling

- [x] 0.1 Add the two-line guard to `.gitignore`: `openspec/changes/otli-device-evidence/evidence/**/*.png` and `openspec/changes/otli-device-evidence/evidence/**/*.jpg`.
  - PR: 02. Acceptance (spec `Optional artifacts, no screenshots in the repo`): `git check-ignore -v openspec/changes/otli-device-evidence/evidence/x/a.png` and the same for `.jpg` report the new rule. Test-first: N/A (config line); the `check-ignore` command is the check.
- [x] 0.2 Create `docs/manual-tests.md` with one table, columns `date | row IDs | what was tested | result | where | who | reference`, and a short note that `reference` is an Engram id, a small text artifact path, or `-`.
  - PR: 02. Acceptance (spec `Manual-test log`): the file exists with exactly those columns; later sessions only append rows. Test-first: N/A (documentation).
- [x] 0.3 Session prerequisite: from `backend/` start fresh local emulators with `npx firebase emulators:start --only firestore,auth --project demo-otli` (no `emulator-data` exists, so do not use `npm run emulators`), then seed with `npm --prefix backend run seed`; run the idempotency repeat only in task 1.8.
  - PR: none (operator action, repeated each session). Acceptance: Firestore on 8080 and Auth on 9099 answer; seeded accounts such as `customer1@otli.test` and `merchant1@otli.test` can sign in. Stop these manual emulators before the full-suite run in 3.7 (`test:android` starts its own through `run-with-emulators.mjs`; confirm port use first).
  - Observed 2026-10-14 (session C): emulators started from `backend/`, ports 8080 and 9099 listening, `npm --prefix backend run seed` created the 8 seeded accounts and the catalog; manual emulators stopped before 3.7.
- [x] 0.4 Session prerequisite: confirm the phone with `adb devices` (serial `xkqwijtcyttsaahi`, model 2510DRA23L), build once with `./gradlew assembleDebug -Potli.emulatorHost=127.0.0.1`, install that APK on the phone, and tunnel with `adb -s xkqwijtcyttsaahi reverse tcp:8080 tcp:8080` and `adb -s xkqwijtcyttsaahi reverse tcp:9099 tcp:9099`. adb lives at `%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe` (not on PATH).
  - PR: none (operator action). Acceptance: the app signs in against the emulators. If `input` or `pm grant/revoke` fail, record a MIUI finding ("USB debugging (Security settings)" off) and ask the user to enable it; if `adb install` is blocked, ask for "Install via USB".
  - Partial 2026-10-14 (session C): `adb devices` lists `xkqwijtcyttsaahi` as `device` and both `adb reverse` tunnels were set; `connectedDebugAndroidTest` and `test:android` installed the debug and test APKs with no install block and the app signed in against the emulators (65/65). The standalone `assembleDebug` install for the adb checks is not done yet, so this box stays open for Phase 1.
  - Done 2026-10-14 (session A1): `./gradlew installDebug -Potli.emulatorHost=127.0.0.1` built and installed the one debug APK on the phone without an install block, `adb reverse` for 8080 and 9099 was set again, `input` and `uiautomator dump` worked (no "USB debugging (Security settings)" block), and the app signed in against fresh seeded local emulators as customer, merchant, courier and admin.
- [ ] 0.5 Session prerequisite for R1/R3 only: start AVD `Medium_Phone_API_36.1` (emulator 37.2.12, WHPX OK, serial usually `emulator-5554`), install the same APK, and run `adb -s emulator-5554 reverse tcp:8080 tcp:8080` and `tcp:9099 tcp:9099`.
  - PR: none (operator action). Acceptance: the AVD boots and the app signs in against the same emulators. If the AVD cannot be started at apply time, R1 and R3 become USER-gaps with that reason (spec `Realtime between two actors`).

## Phase 1: (A) adb-driven checks by family

Agent drives the phone with `adb` against the local emulators. Acceptance criteria are those of the spec requirement `Check families and acceptance criteria`.

### Block A1 (PR 03): layout/theme, auth, seed idempotency, automated proofs, citations of evidenced rows

Layout and theme

- [x] 1.1 **L1** Edge-to-edge: no content under the status bar. Technique: `uiautomator dump` plus `dumpsys window | rg -i statusBars`.
  - PR: 03. Acceptance: the first content node's `bounds` top is at or below the status bar height.
  - Evidencia: el primer contenido queda debajo de la barra de estado (barra 128 px segun `dumpsys window`; login arranca en y=196 y la barra superior de las cuatro pantallas de inicio en y=150), visto por el agente via adb en el Xiaomi 2510DRA23L (emuladores locales), 2026-10-14; extracto en `evidence/a1-adb-excerpts.txt`.
- [x] 1.2 **L2** Dark mode: the gate's Sign out is legible and enabled. Technique: `cmd uimode night yes`, relaunch, dump, then `night no`.
  - PR: 03. Acceptance: dump shows the Sign out node `enabled="true"`; contrast is judged by eye and the `Evidencia:` line says so.
  - Evidencia: en modo oscuro (el telefono ya estaba en `night yes`, sin cambiarlo) el boton Sign out de la pantalla "Approval pending" sale `enabled="true"` y se lee claro (texto claro sobre fondo oscuro, juzgado a ojo sobre una captura local no guardada), probado por el agente via adb en el Xiaomi (emuladores locales), 2026-10-14.
- [x] 1.3 **L3** ProfileIncomplete gate shows Sign out and Try again. Needs an Auth user without a `users/{uid}` document (craft one through the Auth emulator).
  - PR: 03. Acceptance: both buttons visible. If the account cannot be crafted, record a USER-gap with the reason.
  - Evidencia: una cuenta de Auth sin documento `users/{uid}` (creada con el emulador de Auth, `orphan1@otli.test`) muestra "Profile not found" con los botones Try again y Sign out habilitados, probado por el agente via adb en el Xiaomi (emuladores locales), 2026-10-14.
- [x] 1.4 **L4** Storefront has a top bar and up arrow, and system back returns.
  - PR: 03. Acceptance: dump shows the top bar and up arrow; `input keyevent BACK` returns to the previous screen.
  - Evidencia: el storefront de Comedor Dona Marta tiene barra superior con flecha (`content-desc="Back"`) y carrito, y `input keyevent BACK` vuelve al inicio del cliente, probado por el agente via adb en el Xiaomi (emuladores locales), 2026-10-14.
- [x] 1.5 **L5** Overflow menu offers Sign out on the customer home and on the merchant home.
  - PR: 03. Acceptance: Sign out is present on both homes.
  - Evidencia: el menu de tres puntos muestra "Sign out" en el inicio del cliente y en el del comerciante (tambien en el del administrador), probado por el agente via adb en el Xiaomi (emuladores locales), 2026-10-14.
- [x] 1.6 **L6** Every order list is newest-first and each row shows "Placed HH:mm".
  - PR: 03. Acceptance: with at least two orders per list, dumps show descending order and the "Placed HH:mm" text on customer, merchant, courier and admin lists.
  - Evidencia: con dos pedidos de customer1 (23:24:13Z y 23:24:52Z) las listas de cliente, comerciante, repartidor y administrador (Active y All orders) muestran primero el mas nuevo (C$ 150.00 y luego C$ 210.00) y "Placed 17:24" en cada fila (ambos del mismo minuto, el orden se comprobo contra `createdAt` en el emulador), visto por el agente via adb en el Xiaomi (emuladores locales), 2026-10-14.

Auth, seed idempotency, automated proofs

- [x] 1.7 **A3** Wrong password is rejected. Technique: `input text`, submit, dump.
  - PR: 03. Acceptance: an error text is shown, the login screen is still present, no session starts.
  - Evidencia: con contrasena incorrecta aparece "We could not sign you in. Check your email and password and try again.", la pantalla de login sigue presente y no se inicia sesion, probado por el agente via adb en el Xiaomi (emuladores locales), 2026-10-14.
- [x] 1.8 **O3** Seed and admin scripts are idempotent. On fresh emulators run the seed and the admin script, list document counts per collection with the `Bearer owner` REST call, run both again, list again.
  - PR: 03. Acceptance (spec `Seed idempotency (O3)...`): second run exits without error and counts per collection are identical; state the counts in `Evidencia:`.
  - Evidencia: con emuladores nuevos el seed corrio dos veces sin error y los conteos por coleccion fueron identicos (users 8, merchants 3, couriers 3, settings 1, categories 2+2, products 5+5, cuentas Auth 8; la segunda corrida actualizo las 8 cuentas sin crear ninguna), agente via adb/REST en el PC (emuladores locales), 2026-10-14; no existe un script de admin aparte: la cuenta admin la crea el propio seed (`seed-admin`).
- [x] 1.9 **O5** Rerun the demo-script section 8 automated proofs: `./gradlew testDebugUnitTest` (or `./gradlew verifyAll`) and `npm --prefix backend test`.
  - PR: 03. Acceptance: green; the `Evidencia:` line states the JVM and rules test counts (the 2026-10-13 baseline was 1075 JVM and 321 rules).
  - Evidencia: `./gradlew testDebugUnitTest --rerun` dio 1075 pruebas JVM sin fallos (incluye las 2 de DomainPurityTest) y `npm --prefix backend test` dio 321/321 pruebas de reglas en 17 archivos, ejecutado por el agente en el PC, 2026-10-14.

Rows already evidenced: record the citation, do not redo (spec `Already-evidenced rows are cited, not redone`)

- [x] 1.10 **M1** Pin picker: tap places the pin, screen scrolls. Cite the 2026-10-13 confirmation by the user on the physical device.
  - PR: 03. Acceptance: `Evidencia:` names who, where and the date; no new check.
  - Evidencia: la confirmacion del usuario en el telefono fisico del 2026-10-13 (tocar coloca el pin y la pantalla se desplaza) esta registrada en el inventario de la exploracion (Engram #73, fila M1) y en el flujo completo del usuario del #69; no existe una observacion Engram propia de M1; no se repitio.
- [x] 1.11 **M2** Customer live map renders courier marker and "x ago". Cite Engram #43 and #44.
  - PR: 03. Acceptance: `Evidencia:` names the Engram ids and their date; not re-executed.
  - Evidencia: el usuario probo el mapa en vivo en el telefono: caminata con repartidor OK el 2026-10-02 (Engram #43) y "customer sees courier" en la revision del 2026-10-03 (Engram #44); la etiqueta "x ago" no se nombra de forma expresa en esos registros; no se repitio.
- [x] 1.12 **M3** Courier map: own dot, pickup/dropoff emphasis, camera fit. Cite Engram #44 (camera fit was still listed manual-only in verify-report #70; state that nuance).
  - PR: 03. Acceptance: `Evidencia:` names #44 and date; not re-executed.
  - Evidencia: revision del usuario en el telefono el 2026-10-03 (Engram #44): punto propio, pin de recogida resaltado antes de recoger y pin de entrega despues, OK; el ajuste de camara no se nombra en #44 y el verify-report #70 aun lo listaba solo manual, matiz que queda sin confirmar de forma expresa; no se repitio.
- [x] 1.13 **M4** No native info window, Compose legend. Cite Engram #44 and #45.
  - PR: 03. Acceptance: `Evidencia:` names the ids and date; not re-executed.
  - Evidencia: F.11 (info window nativo flotando sobre Compose) se hallo en la revision del usuario del 2026-10-03 (Engram #44) y se corrigio con leyenda Compose, verificada en el telefono (Engram #45, 2026-10-04); no se repitio.
- [x] 1.14 **G1** "Turn on location?" dialog when going online with services off. Cite Engram #44.
  - PR: 03. Acceptance: `Evidencia:` names #44 and date; not re-executed.
  - Evidencia: el dialogo de ubicacion de F.9 y el aviso con boton funcionan, segun la revision del usuario en el telefono del 2026-10-03 (Engram #44); no se repitio.
- [x] 1.15 **G3** Publishing resumes after GPS off then on, no re-login. Cite Engram #45.
  - PR: 03. Acceptance (spec scenario `Citation`): `Evidencia:` names Engram #45 and its date; no new check.
  - Evidencia: la publicacion se reanuda tras apagar y encender el GPS sin volver a iniciar sesion: F.12 corregido y verificado en el telefono (Engram #45, 2026-10-04); no se repitio.
- [x] 1.16 **A2** Sign-out does not crash. Cite the 2026-10-13 logcat review (only benign PERMISSION_DENIED); locate its Engram record with `mem_search` and cite the id if one exists.
  - PR: 03. Acceptance: `Evidencia:` names the recorded 2026-10-13 evidence; not re-executed.
  - Evidencia: la revision de logcat del telefono del 2026-10-13 (Engram #69) mostro solo 64 `PERMISSION_DENIED` benignos en los 2 s posteriores al cierre de sesion y ningun fallo de Otli (el cierre era de `com.tiktok.manager`); no se repitio.
- [x] 1.17 **AD1** Cancelling a picked_up order frees the courier. Cite the AD1 device run (device test 13/13, manual scenario, production readback, 2026-10-13); locate its Engram observation with `mem_search` and cite the id.
  - PR: 03. Acceptance: `Evidencia:` names the observation id and date; not re-executed.
  - Evidencia: AD1/F.13: `FirestoreAdminRepositoryTest` 13/13 en el Xiaomi y escenario manual (pedido cancelado con motivo, `activeOrderId` del courier1 en null, fuera del pool) en la sesion del 2026-10-08 (Engram #28), reconfirmado en el resumen del 2026-10-13 (Engram #69) junto con el flujo del usuario contra produccion sin errores (no hay un registro propio de lectura en produccion); no se repitio.
- [x] 1.18 Close device session A1: append the rows of 1.1-1.9 to `docs/manual-tests.md` (the citations 1.10-1.17 are not a device session and need no log row) and write one Engram note under `sdd/otli-device-evidence/apply-progress`; commit task-line states, log rows and any optional text dumps as a work-unit commit.
  - PR: 03. Acceptance (spec scenario `End of a device session`): log rows and Engram note exist; no screenshot staged.
  - Done 2026-10-14: 9 rows (L1-L6, A3, O3, O5) appended to `docs/manual-tests.md`, Engram note saved under `sdd/otli-device-evidence/apply-progress` (#79, merged with the earlier batches), excerpts in `evidence/a1-adb-excerpts.txt`, no image in the tree. The orchestrator makes the work-unit commit.

### Block A2 (PR 04): map, location and permissions, foreground service, notifications

Run order inside the block: row A1 (1.22) before G5 (1.21) since both revoke location permission (restore with `pm grant` between them); FS1-FS3 and G2 in one delivery; N2 before N3 (reset flags); N3 last, then restore notification permission.

- [x] 1.19 **M5** Admin order detail shows the live map. Drive an order to a claimed or picked-up state and put a courier position in `liveLocations/<orderId>` (courier app on the AVD with `adb emu geo fix`, or an emulator REST write); open the order as Admin.
  - PR: 04. Acceptance: the detail shows the map and the courier marker. If it cannot be driven, record a USER-gap with the reason.
  - Evidencia: el detalle del pedido del administrador (pestana All orders) muestra el mapa "Courier location" con la leyenda "Delivery address" y "Courier" (punto azul del repartidor cerca de Jinotepe, "Updated 1 min ago") con un pedido `claimed` por courier1 en la UI, y la posicion en `liveLocations/<orderId>` la escribio el propio telefono con su ubicacion real (no se inyecto nada), probado por el agente via adb en el Xiaomi 2510DRA23L (emuladores locales), 2026-10-14; extracto en `evidence/a2-adb-excerpts.txt`.
- [x] 1.20 **G2** Mid-delivery warning with a re-open button when location services are off, removed when restored. Technique: `settings put secure location_mode 0` then `3`, verify with `settings get secure location_mode`.
  - PR: 04. Acceptance (spec scenario `Warning removed on restore`): warning plus button appear, and are gone after restore; the `Evidencia:` line says so.
  - Evidencia: con una entrega en curso y `location_mode` 0 aparece "Location is off — turn it on so the customer can follow you." con el boton "Turn on location" (que reabre el dialogo de Play Services, y este tambien salio solo una vez al apagar), y con `location_mode` 3 el aviso y el boton desaparecen y vuelve "Sharing your location with the customer", probado por el agente via adb en el Xiaomi (emuladores locales), 2026-10-14.
- [x] 1.21 **G5** With location permission revoked the courier map shows pins only and no courier marker. Technique: `pm revoke com.otli.app android.permission.ACCESS_FINE_LOCATION`, dump the map, then `pm grant` to restore.
  - PR: 04. Acceptance: pins visible, no courier marker.
  - Evidencia: con `ACCESS_FINE_LOCATION` y `ACCESS_COARSE_LOCATION` revocados el mapa de la entrega del repartidor lista solo los pines ("Pick up here" y "Delivery address") y ya no aparece "You" (que si estaba antes), con el aviso "Allow location"; al hacer `pm grant` y reabrir vuelven "You" y el servicio; visto por el agente via adb en el Xiaomi (emuladores locales), 2026-10-14; la ausencia del marcador se confirma por la leyenda del dump, no por pixeles del mapa.
- [x] 1.22 **A1** Going online without permission raises the runtime request. Technique: revoke and clear flags, go online as the courier, dump the dialog (MIUI may show its own dialog; say so).
  - PR: 04. Acceptance: the permission request appears.
  - Evidencia: con el permiso de ubicacion revocado y sus flags limpiados, activar "You are online" como courier1 abre el dialogo del sistema "Permitir que Otli acceda a la ubicacion de este dispositivo?" (GrantPermissionsActivity de AOSP, sin dialogo propio de MIUI) y al elegir "Mientras la app esta en uso" el repartidor queda en linea, probado por el agente via adb en el Xiaomi (emuladores locales), 2026-10-14.
- [x] 1.23 **FS1** Courier location keeps updating backgrounded and screen-off. Technique: `input keyevent HOME`, `input keyevent 223`, read `liveLocations/<orderId>.updatedAt` twice more than 60 s apart with the `Bearer owner` REST call.
  - PR: 04. Acceptance: `updatedAt` advances across two throttle intervals; quote both values in `Evidencia:`; mention Autostart or battery saver only if it changed the outcome.
  - Evidencia: con la pantalla apagada (`keyevent 223`, `mWakefulness=Dozing` en cada lectura) y la app en segundo plano `liveLocations/<orderId>.updatedAt` avanzo de 23:50:54.830Z a 23:51:55.600Z, 23:52:55.557Z y 23:54:57.436Z (heartbeat de 60 s con el telefono quieto), sin que Autostart ni el ahorro de bateria de MIUI cambiaran el resultado, probado por el agente via adb y REST en el Xiaomi (emuladores locales), 2026-10-14.
- [x] 1.24 **FS2** `DeliveryTrackingService` is listed while delivering and gone after delivered, release and cancel. Technique: `dumpsys activity services com.otli.app | rg -A5 DeliveryTrackingService` at each point.
  - PR: 04. Acceptance: listed during the delivery, absent after each of the three endings.
  - Evidencia: `dumpsys activity services` lista `DeliveryTrackingService` con la entrega en curso (claimed o picked_up) y ya no lo lista a los segundos de delivered (boton Delivered en la UI), de release y de cancel (estos dos escritos por el agente en el emulador con los mismos campos que el Admin, porque el telefono no puede ser admin y repartidor a la vez), probado por el agente via adb en el Xiaomi (emuladores locales), 2026-10-14.
- [x] 1.25 **FS3** The service starts as a location foreground-service type on API 34+. Technique: `logcat -d -v time | rg -i 'DeliveryTrackingService|foregroundServiceType|FOREGROUND_SERVICE'`.
  - PR: 04. Acceptance: logcat shows the location type and no `FOREGROUND_SERVICE` error on the phone's API level.
  - Evidencia: el servicio corre como `isForeground=true foregroundId=7001 types=0x00000008` (tipo location) con la notificacion del canal `tracking`, logcat muestra "Background started FGS: Allowed ... DeliveryTrackingService ... targetSdkVersion:36" en un buffer recien limpiado y ningun error `FOREGROUND_SERVICE`, probado por el agente via adb en el Xiaomi con Android 16 (API 36, emuladores locales), 2026-10-14.
- [x] 1.26 **N1** One local notification per status change for the customer and for the merchant. Technique: `dumpsys notification --noredact | rg -A3 'pkg=com.otli.app'` before and after each status change (channels `orders`, `tracking`).
  - PR: 04. Acceptance: exactly one record per change, for both roles.
  - Evidencia: con el cliente conectado cada cambio de estado (accepted, preparing, ready, escritos por el agente en el emulador) dejo exactamente 1 `notification_enqueue` del canal `orders` (mas el resumen de grupo que agrega el sistema), y con el comerciante conectado un pedido nuevo dejo 1 aviso "New order"; el comerciante solo recibe avisos de pedidos nuevos (no de cambios de estado, segun `LocalOrderNotifier.watchMerchant`), probado por el agente via adb en el Xiaomi (emuladores locales), 2026-10-14.
- [x] 1.27 **N2** `POST_NOTIFICATIONS` is asked once per home and not re-asked after a decision. Technique: revoke and `pm clear-permission-flags ... user-set user-fixed`, open each home twice, logcat plus dump of the dialog.
  - PR: 04. Acceptance: one prompt per home, none after a decision.
  - Evidencia: con `pm revoke` y `pm clear-permission-flags ... user-set user-fixed` el dialogo "Permitir que Otli te envie notificaciones?" salio 1 vez al abrir el inicio del comerciante y 1 vez al abrir el del repartidor, y tras Permitir no volvio a salir al reabrir, pero en el inicio del cliente tras un primer "No permitir" volvio a salir al reabrir (segunda pregunta, ya con "no volver a preguntar") y solo tras el segundo rechazo dejo de salir, probado por el agente via adb en el Xiaomi (emuladores locales), 2026-10-14.
  - Outcome FAILED (check executed, behavior differs from the acceptance for the deny path; the allow path passes on two homes). finding: after a first plain denial the home asks again on its next open (the app calls the launcher on every home entry and keeps no memory of the decision, so the platform's second prompt appears); it stops only after the second denial; fix goes to a separate change.
- [x] 1.28 **N3** With notifications disabled no notification appears and the app does not crash. Technique: `pm revoke com.otli.app android.permission.POST_NOTIFICATIONS` (or the MIUI per-app toggle), change a status, check `dumpsys notification` and `logcat | rg FATAL`; restore afterwards.
  - PR: 04. Acceptance (spec scenario `Notifications off`): no new record, no `FATAL`; the `Evidencia:` line states it.
  - Evidencia: con `POST_NOTIFICATIONS` denegado (`importance=NONE`) dos cambios de estado de un pedido del cliente dejaron 0 `notification_enqueue`, 0 registros de Otli en `dumpsys notification` y ningun `FATAL EXCEPTION` en logcat con el mismo proceso vivo, y tras `pm grant` el mismo cambio si genero 1 aviso, probado por el agente via adb en el Xiaomi (emuladores locales; se uso el rechazo del permiso, no el interruptor de MIUI), 2026-10-14.
- [x] 1.29 Close device session A2: append the rows of 1.19-1.28 to `docs/manual-tests.md`, write one Engram note under `sdd/otli-device-evidence/apply-progress`, restore the phone (location mode 3, permissions granted, `night no`), and commit as a work-unit commit.
  - PR: 04. Acceptance: log rows and Engram note exist; phone restored; no screenshot staged.
  - Done 2026-10-14: 10 rows (M5, G2, G5, A1, FS1, FS2, FS3, N1, N2, N3) appended to `docs/manual-tests.md`, Engram note saved under `sdd/otli-device-evidence/apply-progress` (#79, merged with the earlier batches), excerpts in `evidence/a2-adb-excerpts.txt`, no image in the tree. Outcomes: 9 DONE, 1 FAILED (N2 finding), 0 USER-gap. The phone was restored to the state read before the session (`location_mode` 0, `night yes`, `screen_off_timeout` 600000, verified with a second read; the text above says mode 3 and `night no`, which were not the phone's original values), the app was uninstalled and the tunnels and emulators stopped. The orchestrator makes the work-unit commit.

## Phase 2: (B) checks that need the person, and the two-actor realtime checks

- [ ] 2.1 **R1** A status change by one actor appears on the other's screen with no refresh. The user keeps the Xiaomi phone unlocked and plugged in as one role; the agent drives the AVD as the other role with `adb`, changes the order status, and dumps the phone screen.
  - PR: 05. Acceptance (spec scenario `Live propagation`): the receiver shows the new status with no manual refresh. Needs 0.5; if the AVD cannot start, USER-gap with that reason.
  - Evidencia: (to be written when closed)
- [ ] 2.2 **R3** Suspending a merchant while signed in flips that session to "Account suspended" without re-login. The phone is the signed-in merchant; the AVD is the Admin who presses "Suspend".
  - PR: 05. Acceptance: the phone shows "Account suspended" without any re-login. Same AVD fallback as 2.1.
  - Evidencia: (to be written when closed)
- [ ] 2.3 **R2** Claim race by literal simultaneous human tap. User only: two couriers press "Claim" together (demo-script section 5). The rules race tests (20 rounds) and `FirestoreDispatchRepositoryTest` may be cited as support but do not close it.
  - PR: 05. Acceptance: a dated user confirmation in `Evidencia:`, or a USER-gap with reason and date.
  - Evidencia: (to be written when closed)
- [ ] 2.4 **G4** Real walking track with the 10 s / 10 m / 60 s throttle. User only: walk with the phone as courier; the agent can read `liveLocations` before and after.
  - PR: 05. Acceptance: a dated user confirmation (Engram #43 walk of 2026-10-02 is background, not the closing evidence) or a USER-gap with reason.
  - Evidencia: (to be written when closed)
- [ ] 2.5 **A4** Production API key restricted by package and SHA-1. User only, in the Firebase/Google Cloud console (the user reported doing it on 2026-10-13 together with enabling Email/Password; no artifact exists).
  - PR: 05. Acceptance: dated user confirmation or USER-gap with reason. The agent MUST NOT probe production for this unless the user explicitly authorizes it.
  - Evidencia: (to be written when closed)
- [ ] 2.6 **O4** Redo the `docs/demo-script.md` sections 2-7 walkthrough. User only; the agent supplies the steps and records the dated confirmation (sections 4, 5 and 6 can also feed R1, R2 and the two-actor evidence if the user says so).
  - PR: 05. Acceptance (spec `Already-evidenced rows...`): dated user confirmation per section confirmed, or USER-gap with reason.
  - Evidencia: (to be written when closed)
- [ ] 2.7 **AD2** Fee, approvals, release, cancel and All orders. Covered by O4; no separate check.
  - PR: 05. Acceptance (spec scenario `AD2 covered by O4`): DONE citing O4 if O4 is DONE; if O4 is a USER-gap, AD2 is a USER-gap with the same reason.
  - Evidencia: (to be written when closed)
- [ ] 2.8 Close device session B: append the rows of 2.1-2.2 (and any agent-observed part of 2.3-2.6) to `docs/manual-tests.md`, write one Engram note under `sdd/otli-device-evidence/apply-progress`.
  - PR: 05. Acceptance: log rows and Engram note exist; user confirmations are dated.

## Phase 3: (C) Code gap and stale archived text

Strict TDD applies with an honest exception: `FirestorePhotoSource` already works (`FirestoreCatalogRepositoryTest.theStoredPhotoCanBeFetchedThroughThePhotoSource` already reads through it), so no meaningful RED exists. The test is recorded as a characterization test and validated by three temporary mutants, each observed failing then reverted and never committed. RED is never invented.

- [x] 3.1 Confirm the `productPhotos` read rule in `backend/firestore.rules` (read-only) before writing case 4. At planning time it reads `allow read: if signedIn();` (line 157), so a signed-out read is denied.
  - PR: 02. Acceptance: if the rule allows unauthenticated reads, drop case 4 and record a finding instead of adjusting the test to pass. Test-first: N/A (read-only check).
- [x] 3.2 Write `app/src/androidTest/java/com/otli/app/catalog/adapters/firestore/FirestorePhotoSourceTest.kt`, same shape as `FirestoreCatalogRepositoryTest.kt` (read-only for shape): `AndroidJUnit4`, `OtliFirebase.auth/firestore(context)`, `await { }` with `withTimeout(30_000)`, sign-in via `FirestoreAuthRepository.login(..., "otli-demo-123")`, `@After` cleanup and sign-out. Cases: (1) `aCustomerReadsAMerchantsProductPhoto` (merchant1 `upsertProduct` with bytes, customer1 `fetch(MERCHANT_1, productId)` equals the bytes, cleanup as the merchant); (2) `theProfilePhotoIsReadUnderTheFixedProfileId` (`FirestoreMerchantRepository.updatePhoto(MERCHANT_2, bytes)` then `fetch(MERCHANT_2, PhotoKey.PROFILE_PHOTO_ID)`); (3) `aPhotoThatDoesNotExistIsNull`; (4) `aSignedOutReaderIsRefused` (`runCatching { fetch(...) }.isFailure`).
  - PR: 02. Spec requirement `FirestorePhotoSource instrumented test (O2)`. TDD note: characterization test, no observable RED because the adapter exists; write the class, compile with `./gradlew compileDebugAndroidTestKotlin`, and state the exception in the commit and in the O2 `Evidencia:`.
- [x] 3.3 Run the new class on the phone: with emulators up and seeded (0.3, 0.4), `./gradlew connectedDebugAndroidTest -Potli.emulatorHost=127.0.0.1 -Pandroid.testInstrumentationRunnerArguments.class=com.otli.app.catalog.adapters.firestore.FirestorePhotoSourceTest`.
  - PR: 02. Acceptance: the 4 cases (or 3 if case 4 was dropped) pass on the phone.
  - Observed 2026-10-14: 4 tests, 0 failures (`aCustomerReadsAMerchantsProductPhoto`, `theProfilePhotoIsReadUnderTheFixedProfileId`, `aPhotoThatDoesNotExistIsNull`, `aSignedOutReaderIsRefused`) on the Xiaomi 2510DRA23L (Android 16) against fresh seeded local emulators; characterization test, no RED by design.
- [x] 3.4 Mutant M1 in `app/src/main/java/com/otli/app/catalog/adapters/firestore/FirestorePhotoSource.kt` `fetch`: `return null`. Run the class, record the failing test names (expected: cases 1 and 2), then revert.
  - PR: 02. Acceptance: observed failing; after revert `git diff --stat -- app/src/main` is empty; never committed.
  - Observed 2026-10-14: with `return null` the class ran 4 tests, 3 failed: `aCustomerReadsAMerchantsProductPhoto`, `theProfilePhotoIsReadUnderTheFixedProfileId` and `aSignedOutReaderIsRefused` (it expected a refusal and got null). Restored with `git checkout`, `git diff --quiet` on the file passed.
- [x] 3.5 Mutant M2 in `app/src/main/java/com/otli/app/catalog/adapters/firestore/FirestorePhotoSource.kt` `fetch`: read `.document(CatalogDocuments.PROFILE_PHOTO_ID)` instead of `photoId` (expected to kill case 1). Run, record failing names, revert.
  - PR: 02. Acceptance: observed failing; `git diff --stat -- app/src/main` empty after revert.
  - Observed 2026-10-14: with `.document(CatalogDocuments.PROFILE_PHOTO_ID)` the class ran 4 tests, 2 failed: `aCustomerReadsAMerchantsProductPhoto` (expected) and `aPhotoThatDoesNotExistIsNull` (the profile photo left under merchant 2 was read for `no-such-photo`). Restored, `git diff --quiet` passed.
- [x] 3.6 Mutant M3 in `app/src/main/java/com/otli/app/catalog/adapters/firestore/FirestorePhotoSource.kt` `fetch`: read under `merchantId = "seed-merchant-1"` hard-coded (expected to kill case 2). Run, record failing names, revert.
  - PR: 02. Acceptance: observed failing; `git diff --stat -- app/src/main` empty after revert.
  - Observed 2026-10-14: with `.document("seed-merchant-1")` the class ran 4 tests, 1 failed: `theProfilePhotoIsReadUnderTheFixedProfileId` (expected). Restored, `git diff --quiet` passed; `git status --short` was empty afterwards.
- [x] 3.7 Stop manual emulators, then run the full suite: `ANDROID_SERIAL=xkqwijtcyttsaahi npm --prefix backend run test:android`.
  - PR: 02. Acceptance (spec scenario `Suite green`): 8 classes pass (expected 65 tests: 61 plus 4); state the counts.
  - Observed 2026-10-14: manual emulators stopped first, then `test:android` BUILD SUCCESSFUL (exit 0): 65 tests, 0 failures, 0 skipped in 8 classes: Admin 13, Auth 11, Catalog 9, Merchant 5, PhotoSource 4, Dispatch 11, Order 8, Location 4. The runner writes one merged `TEST-2510DRA23L - 16.xml` under `app/build/outputs/androidTest-results/connected/debug/`, not one file per class.
- [x] 3.8 **O2** Close the row: `FirestorePhotoSourceTest` exists and passes on the phone.
  - PR: 02. Acceptance: `Evidencia:` states the characterization exception, the three mutants (failing test names) and the 8-class counts, who (agent via adb), where (phone, local emulators) and date.
  - Evidencia: `FirestorePhotoSourceTest` 4/4 en el Xiaomi 2510DRA23L contra emuladores locales (test de caracterizacion, sin RED porque el adaptador ya existia), 3 mutantes vistos fallar (M1: 3 casos, M2: 2 casos, M3: 1 caso) y revertidos, y suite completa 8 clases 65/65 (antes 61/61), agente via adb, 2026-10-14.
- [x] 3.9 Archived `openspec/changes/archive/2026-10-13-otli-mvp/tasks.md`, task 2.4.1: this change authorises the edit. Keep the original sentence (no strikethrough) and append the outcome right after the stale clause "androidTest pending device GREEN": covered by `FirestoreCatalogRepositoryTest` and `FirestorePhotoSourceTest`, device GREEN on the date of 3.7 (otli-device-evidence O2).
  - PR: 02. Acceptance (spec `Seed idempotency (O3) and automated rows (O1, O5)`): the line no longer asserts "pending". Do this only after 3.8 is DONE.
  - Done 2026-10-14: the original sentence is kept and the outcome is appended inside the parenthesis, right after "androidTest pending device GREEN".
- [x] 3.10 Same file, task 3.4.1: keep the original sentence "compile-checked only, its device run is pending" (no strikethrough) and append the outcome: passed on device 2026-10-13 (`FirestoreOrderRepositoryTest` 8/8, 61/61 run).
  - PR: 02. Acceptance: no "pending" or "compile-only" left in the live wording of 3.4.1.
- [x] 3.11 Same file, task 4.3.1: keep the original sentence "compile-checked only, device run pending" (no strikethrough) and append the outcome: passed on device 2026-10-13 (`FirestoreDispatchRepositoryTest` 11/11, 61/61 run).
  - PR: 02. Acceptance: as 3.10.
- [x] 3.12 Same file, task 5.3.1: keep the original sentence "compiled, device GREEN pending" (no strikethrough) and append the outcome: passed on device 2026-10-13 (`FirestoreLocationRepositoryTest` 4/4, 61/61 run).
  - PR: 02. Acceptance: as 3.10. Verify all four with `rg -n 'compile-only|device run pending|GREEN pending' openspec/changes/archive/2026-10-13-otli-mvp/tasks.md` (matches may remain only inside struck text).
- [x] 3.13 **O1** Close the row: cite the 2026-10-13 device run of 61/61 (7 classes, now 8 with O2) and the four text fixes.
  - PR: 02. Acceptance: `Evidencia:` names the 61/61 run, who and date, and the four task ids fixed.
  - Evidencia: la suite instrumentada paso 61/61 en 7 clases en el Xiaomi 2510DRA23L el 2026-10-13 y 65/65 en 8 clases el 2026-10-14 (agente via adb, emuladores locales), y las cuatro frases obsoletas del archivo `otli-mvp` (2.4.1, 3.4.1, 4.3.1, 5.3.1) quedaron corregidas conservando el texto original.
- [x] 3.14 Close device session C: append the O2 and 3.7 rows to `docs/manual-tests.md` (reference: the Engram id of the apply-progress note), write one Engram note under `sdd/otli-device-evidence/apply-progress`, commit as work units (test + log together; archived text fixes as a docs commit).
  - PR: 02. Acceptance: `git diff --stat main..HEAD -- app/src/main` is empty (no runtime code changed).

## Phase 4: (D) Close-out

- [ ] 4.1 Tick `docs/demo-script.md` sections 2-7 only for the items the user actually confirmed in O4, with the date; leave unconfirmed items unticked and record them as USER-gap in 2.6.
  - PR: 05. Acceptance: no tick without a matching dated confirmation in the O4 `Evidencia:`.
- [ ] 4.2 Tick `docs/demo-script.md` section 8 with the date for the items proven by O5 (1.9) and, if run, the optional `test:android` item (3.7).
  - PR: 05. Acceptance: ticks match the counts stated in the O5 and O2 `Evidencia:` lines.
- [ ] 4.3 In `openspec/changes/archive/2026-10-13-otli-mvp/tasks.md`, update the remaining F.x "device check pending" notes to point at the row that closed them (for example "device: otli-device-evidence L1, <date>"), only after that row is DONE, FAILED or a recorded USER-gap.
  - PR: 05. Acceptance: no F.x note says "pending" without a pointer to its row outcome.
- [ ] 4.4 Final sweep of the 36 rows: every row has exactly one outcome (DONE with `Evidencia:`, USER-gap with reason and date, or FAILED with finding); none reads "pending", "n/a" or unlisted. Self-check: the count from `rg -c '^- \[x\] [0-9]+\.[0-9]+ \*\*' openspec/changes/otli-device-evidence/tasks.md` equals the count from `rg -c '^  - Evidencia: [^(]' openspec/changes/otli-device-evidence/tasks.md`, and `rg -n '^- \[~\]'` lists every remaining `[~]` row for the verify report.
  - PR: 05. Acceptance (spec scenario `Complete log`): complete outcomes; the `[~]` list is ready for the verify report. Each FAILED finding is queued for a separate change, and no source edit for it appears here.
- [ ] 4.5 Prepare the `sdd-verify` hand-off: note in this file that verify must check (1) every `[x]` device task has `Evidencia:`, (2) every `[~]` is listed, and (3) must search Engram for `sdd/otli-device-evidence/*` and device-run notes such as #44/#45 as well as the repo files.
  - PR: 05. Acceptance (spec `Verification rules for this change`): the three rules are written in the hand-off note.
- [ ] 4.6 Add the archive notes to this file and to Engram: the change-local spec `specs/device-evidence/spec.md` MUST stay inside the archived change folder and MUST NOT be promoted or synced into `openspec/specs/`; list the follow-up items (FAILED findings, and the spec gaps for insets and dark mode, foreground-service lifecycle, order notifications, "own position unknown").
  - PR: 05. Acceptance: the archive note states "do not promote to `openspec/specs/`".

## Row coverage map (36 rows, each exactly one task)

| Rows | Tasks | PR |
|------|-------|----|
| L1-L6 | 1.1-1.6 | 03 |
| A3, O3, O5 | 1.7-1.9 | 03 |
| M1-M4, G1, G3, A2, AD1 (cited) | 1.10-1.17 | 03 |
| M5, G2, G5, A1 | 1.19-1.22 | 04 |
| FS1-FS3 | 1.23-1.25 | 04 |
| N1-N3 | 1.26-1.28 | 04 |
| R1, R3, R2 | 2.1-2.3 | 05 |
| G4, A4, O4, AD2 | 2.4-2.7 | 05 |
| O2, O1 | 3.8, 3.13 | 02 |

Count check: 6 + 3 + 8 + 4 + 3 + 3 + 3 + 4 + 2 = 36.
