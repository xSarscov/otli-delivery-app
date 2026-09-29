# Tasks: Otli MVP — Four-Sided Delivery Marketplace for Nagarote

## Review Workload Forecast

| Field | Value |
|-------|-------|
| Estimated changed lines | ~8,400 lines total (additions + deletions) across the whole MVP |
| 400-line budget risk | High |
| Chained PRs recommended | Yes |
| Suggested split | 24 PRs across 6 slices (Slice 1: 4 PRs, Slice 2: 4 PRs, Slice 3: 5 PRs, Slice 4: 4 PRs, Slice 5: 4 PRs, Slice 6: 4 PRs + 1 non-counted remote-ops PR) |
| Delivery strategy | auto-chain |
| Chain strategy | pending — plan below is written so either `stacked-to-main` or `feature-branch-chain` works without rework; the orchestrator/user picks before the first PR is opened |

```text
Decision needed before apply: No
Chained PRs recommended: Yes
Chain strategy: pending
400-line budget risk: High
```

Rationale: greenfield native Android app (6 feature packages, each with domain/application/adapters layers) plus a parallel backend (security rules, rules tests, concurrency tests, seed script). Slice 3 (ordering) is the heaviest — cart/checkout UI with a map, the full order-creation rules and fee-snapshot logic, and the merchant/customer real-time boards. Slice 4 carries the mandatory concurrency test suite, which is deliberately kept as its own PR so a flaky/failing claim test never blocks unrelated UI review.

Every PR below is written to stand on its own: clear start state, clear finish state, its own verification command(s), and a rollback boundary that does not touch unrelated work. If `feature-branch-chain` is chosen, PR N+1 in the same slice targets PR N's branch; if `stacked-to-main` is chosen, each PR still merges in the listed order because later PRs depend on earlier files (rules additions are cumulative in one `firestore.rules` file, domain types are reused, etc.). Do not reorder PRs within a slice.

### Suggested Work Units

| Unit | Goal | Likely PR | Focused test command | Runtime harness | Rollback boundary |
|------|------|-----------|----------------------|-----------------|-------------------|
| 0.1 | Scaffold Gradle + backend projects, pin verified versions, `verifyAll`, flip Strict TDD on | PR 0 | `./gradlew verifyAll` | `./gradlew assembleDebug` (installs debug APK on a connected emulator) | Revert scaffold commit; no other code depends on it yet |
| 1.1–1.4 | Auth & roles: domain, rules, Firestore adapter, UI | PR 1.1–1.4 | see per-task below | Register 4 role types on a running emulator + app, confirm role-scoped home | Each PR reverts independently; UI PR depends on adapter PR only |
| 2.1–2.4 | Merchant catalog + customer browse | PR 2.1–2.4 | see per-task below | Create category/product in app, verify it appears in customer browse | Revert UI PR without touching rules/domain PRs |
| 3.1–3.5 | Cart, checkout, order placement, order board, tracking screen | PR 3.1–3.5 | see per-task below | Place a real order end-to-end against the emulator from two devices/emulators | Each PR independently revertable; 3.5 depends on 3.1–3.4 |
| 4.1–4.4 | Courier pool, race-safe claim, pickup/deliver | PR 4.1–4.4 | see per-task below | `claim-concurrency.test.ts` 20-iteration loop against the emulator | 4.2 (concurrency) can be reverted without reverting 4.1 domain policy |
| 5.1–5.4 | Live tracking: throttle, service, rules, map UI | PR 5.1–5.4 | see per-task below | Manual: two devices, one publishing GPS, one viewing the map | Each PR independently revertable |
| 6.1–6.4 | Admin: fee, approvals, release, stuck orders, order list | PR 6.1–6.4 | see per-task below | Approve a pending seed account, release a claim, cancel a stuck order in the app | Each PR independently revertable |
| 6.5 | Create Firebase project + deploy rules/indexes to production + production seed | separate, requires user authorization | N/A (remote op, no local test) | `firebase deploy --only firestore:rules,firestore:indexes` (remote — requires explicit user authorization) | Redeploy rules from the previous commit |

---

## Phase 0: Scaffold (prerequisite to every slice)

- [x] 0.1 Create the Gradle project skeleton: `settings.gradle.kts`, `build.gradle.kts`, `gradle/libs.versions.toml`, `gradlew`/`gradlew.bat`, `app/build.gradle.kts` (Hilt+KSP, Compose, Firebase BoM, MapLibre, JUnit4/Turbine/Truth/coroutines-test/Robolectric test deps, `minSdk 26`/`targetSdk 36`/`compileSdk 36`, JDK 17 toolchain, emulator `BuildConfig` fields for `otli.useEmulator`/`otli.emulatorHost`), `app/src/main/AndroidManifest.xml` (permissions: `ACCESS_FINE_LOCATION`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_LOCATION`, `POST_NOTIFICATIONS`; empty `MainActivity`), `.gitignore`.
  - **Verify official docs first**: before pinning any version, confirm each `[unverified]` claim in `openspec/changes/otli-mvp/design.md` against its listed official page (Firebase pricing/quotas, Firebase Android release notes, Cloud Storage Blaze FAQ, Firebase CLI releases, MapLibre Android API, OpenFreeMap, OSM tile policy, Android API levels) using the docs/web tool available in this session. Record the confirmed version numbers directly in `gradle/libs.versions.toml` and `backend/package.json`; do not carry an `[unverified]` version into a committed file.
  - Test-first: N/A (infra scaffold, no behavior yet) — acceptance is a green build.
  - Acceptance: `./gradlew assembleDebug` succeeds (this is the scaffold's own proof, no spec scenario applies).
  - Est. lines: ~180
- [x] 0.2 Create `app/src/main/java/com/otli/app/OtliApplication.kt` (`@HiltAndroidApp`) and one trivial placeholder unit test `app/src/test/java/com/otli/app/PlaceholderTest.kt` asserting `1 + 1 == 2`, to prove `testDebugUnitTest` runs.
  - Test-first: the placeholder test IS the first RED→GREEN cycle (write it failing on a bad assertion, then fix it).
  - Acceptance: `./gradlew testDebugUnitTest` passes.
  - Est. lines: ~30
- [x] 0.3 Create the backend project: `backend/package.json` (scripts: `test`, `test:android`, `emulators`, `seed`, `typecheck`), `backend/tsconfig.json`, `backend/vitest.config.ts`, `backend/firebase.json` (emulator ports: auth 9099, firestore 8080, ui 4000), `backend/.firebaserc` (`demo-otli` default alias for tests), `backend/firestore.indexes.json` (empty array, indexes added per slice), a minimal `backend/firestore.rules` that denies everything (`allow read, write: if false;`), and one placeholder rules test `backend/tests/rules/placeholder.test.ts` asserting a signed-out read is denied.
  - Test-first: the placeholder test IS the first RED→GREEN cycle for the rules runner.
  - Acceptance: `npm --prefix backend test` passes.
  - Est. lines: ~120
- [x] 0.4 Create `backend/contracts/order-transitions.json` with the full transition table from `design.md` (empty `allowed: []` at this point is wrong — populate it now since Slice 3 consumes it from both sides; see `ordering` spec, Requirement: Order State Machine With Actor-Authorized Transitions, for the exact 9 triples).
  - Test-first: N/A (static fixture; consumed by `OrderTransitionsContractTest` in Slice 3, task 3.1).
  - Acceptance: file exists and is valid JSON with exactly the 9 allowed triples.
  - Est. lines: ~40
- [x] 0.5 Wire the Gradle `verifyAll` aggregate task (`testDebugUnitTest` + `lintDebug` + an `Exec` task running `npm --prefix backend test` with fixed, non-user-supplied arguments) in `build.gradle.kts` or a new `buildSrc`/convention plugin, whichever is simpler for a single-module project. Add `README.md` documenting setup (JDK 17/21, Node ≥ 20, `firebase-tools`, emulator start, seed run) and the four exact test commands.
  - Test-first: N/A (build wiring) — acceptance is the aggregate running both suites.
  - Acceptance: `./gradlew verifyAll` runs `testDebugUnitTest`, `lintDebug`, and `npm --prefix backend test`, all green.
  - Est. lines: ~90
- [x] 0.6 **Strict TDD gate**: once 0.1–0.5 are green, edit `openspec/config.yaml` to set `strict_tdd: true`, `testing.status: available`, `rules.apply.tdd: true`, and update Engram topic `sdd/otli-delivery-app/testing-capabilities` to reflect both runners are live. Every task from Phase 1 onward follows RED → GREEN → REFACTOR with the runner named in its task.
  - Acceptance: `openspec/config.yaml` reflects the flip; no spec scenario (process gate).
  - Est. lines: ~10 (config diff)

**PR 0** = tasks 0.1–0.6 (~470 lines; over budget by design because it is one-time infra with no reviewable "logic" — acceptable as a single scaffold PR; if the reviewer wants a split, 0.1+0.2 vs 0.3+0.4+0.5+0.6 divides cleanly at the Android/backend boundary).

---

## Phase 1: Slice 1 — Auth and Roles Skeleton (`auth-roles`)

### PR 1.1 — Domain and application layer

- [x] 1.1.1 Create `core/money/Money.kt` (`@JvmInline value class Money(val centavos: Long)`, `plus`, `times`, `require(centavos >= 0)`) and `app/src/test/java/com/otli/app/core/money/MoneyTest.kt` (table-driven: addition, multiplication, negative-value rejection).
  - Test-first: write `MoneyTest` RED (class doesn't exist), then implement `Money` GREEN. Run: `./gradlew testDebugUnitTest --tests "com.otli.app.core.money.MoneyTest"`.
  - Acceptance: unit test only, no spec scenario (shared kernel used by `ordering` later).
  - Est. lines: ~70
- [x] 1.1.2 Create `core/time/Clock.kt` (port interface + `SystemClock` impl) and `core/result/DomainError.kt` (sealed error hierarchy root, e.g. `Unauthorized`, `InvalidTransition`, `NotFound`).
  - Test-first: N/A (interfaces/sealed types, tested indirectly through consumers).
  - Acceptance: compiles; consumed starting Slice 1 session logic.
  - Est. lines: ~40
- [x] 1.1.3 Create `auth/domain/Role.kt` (`enum class Role { CUSTOMER, MERCHANT, COURIER, ADMIN }`), `auth/domain/AccountStatus.kt` (`ACTIVE, PENDING, SUSPENDED`), `auth/domain/RegistrationPolicy.kt` (pure function: given a chosen `Role`, return the initial `AccountStatus` — `ACTIVE` for `CUSTOMER`, `PENDING` for `MERCHANT`/`COURIER`; `ADMIN` is not an acceptable input and the function returns a typed rejection), and `app/src/test/java/com/otli/app/auth/domain/RegistrationPolicyTest.kt`.
  - Test-first: write `RegistrationPolicyTest` RED covering all four scenarios below, then implement `RegistrationPolicy` GREEN.
  - Acceptance: auth-roles spec — "Customer registers and becomes active immediately", "Merchant registers and starts pending", "Courier registers and starts pending", "Admin accounts are never created through self-registration".
  - Est. lines: ~90
- [x] 1.1.4 Create `auth/domain/SessionState.kt` (sealed interface: `SignedOut`, `Loading`, `ProfileIncomplete`, `Pending(role)`, `Suspended(role)`, `Active(role)`) and `auth/application/AuthRepository.kt` (port: `observeAuthState(): Flow<AuthUser?>`, `observeUserDocument(uid): Flow<UserAccount?>`, `register(email, password, role, profileFields): Result<Unit>`, `login(email, password): Result<Unit>`, `logout()`).
  - Test-first: N/A (interfaces).
  - Acceptance: compiles; consumed by 1.1.5.
  - Est. lines: ~60
- [x] 1.1.5 Create `auth/application/ObserveSessionUseCase.kt` (combines auth state + user document into `SessionState`, per the Session gate sequence diagram in `design.md`) and `app/src/test/java/com/otli/app/auth/application/ObserveSessionUseCaseTest.kt` using a fake `AuthRepository` and `kotlinx-coroutines-test`/Turbine.
  - Test-first: write the test RED (signed out → `SignedOut`; signed in, no user doc yet → `ProfileIncomplete`; signed in + `pending` → `Pending(role)`; signed in + `suspended` → `Suspended(role)`; signed in + `active` → `Active(role)`), then implement GREEN.
  - Acceptance: auth-roles spec — "Each role reaches its own home", "Pending merchant or courier is blocked with a clear message", "Suspended account is blocked with a clear message", "Active account has unrestricted role access".
  - Est. lines: ~110

**PR 1.1** = tasks 1.1.1–1.1.5 (~370 lines). Verify: `./gradlew testDebugUnitTest`. Rollback: revert this PR; nothing else in the repo depends on these types yet except PR 1.4 (UI), which has not been written.

### PR 1.2 — Firestore rules for `users/{uid}`

- [x] 1.2.1 Extend `backend/firestore.rules` with the `users/{uid}` match block: self-create on registration (role ∈ {customer, merchant, courier}, status forced to the value `RegistrationPolicy` would produce — `active` for customer, `pending` for merchant/courier — and rejecting any client-supplied `role == 'admin'` or self-supplied `status` that doesn't match the role default), self-update of profile fields only (not `role`/`status`), Admin-only update of `status`. Add the composite index `users(status ASC, createdAt ASC)` to `backend/firestore.indexes.json`.
  - Test-first: write `backend/tests/rules/users.test.ts` RED first (one `assertSucceeds`/`assertFails` per bullet above, using `authenticatedContext`/`withSecurityRulesDisabled` fixtures per `design.md`), then extend the rules GREEN. Run: `npm --prefix backend test -- users`.
  - Acceptance: auth-roles spec — "Customer registers and becomes active immediately", "Merchant registers and starts pending", "Courier registers and starts pending", "Admin accounts are never created through self-registration", "A user cannot act on a resource they do not own" (profile fields of another uid).
  - Est. lines: ~230 (rules + tests)
- [x] 1.2.2 Add `backend/scripts/seed.ts` bootstrap: Admin SDK connection to the emulator (`FIRESTORE_EMULATOR_HOST`, `FIREBASE_AUTH_EMULATOR_HOST`), fixed-UID idempotent creation of the `seed-admin` Auth user + `users/seed-admin` document only (later tasks in Slices 2–6 extend this same script with merchant/courier/customer seed data). Add `npm run seed` wiring already declared in 0.3.
  - Test-first: N/A (imperative script) — acceptance is a manual run against the emulator producing the Admin account idempotently (re-running does not duplicate or error).
  - Acceptance: ADR-15 / proposal decision "seed script creates pre-approved test accounts"; unblocks Admin login before Slice 6's approval UI exists.
  - Est. lines: ~70

**PR 1.2** = tasks 1.2.1–1.2.2 (~300 lines). Verify: `npm --prefix backend test -- users`. Rollback: revert this PR; PR 1.1 domain types are unaffected.

### PR 1.3 — Firestore adapter

- [x] 1.3.1 Create `core/di/FirebaseModule.kt` (Hilt module providing `FirebaseAuth`, `FirebaseFirestore`, wiring `useEmulator(host, port)` from `BuildConfig.OTLI_USE_EMULATOR`/`OTLI_EMULATOR_HOST` per the Environment Wiring section of `design.md`).
  - Test-first: N/A (DI wiring, verified by instrumented tests connecting to the emulator).
  - Acceptance: app connects to the emulator in debug builds.
  - Est. lines: ~50
- [x] 1.3.2 Create `auth/adapters/firestore/FirestoreAuthRepository.kt` implementing `AuthRepository` (Firebase Auth for credentials, a single batched write of `users/{uid}` on register, `FirebaseAuth.authStateChanges()` + a `users/{uid}` snapshot listener for `observeUserDocument`) and `auth/di/AuthModule.kt` (Hilt binding).
  - Test-first: write `app/src/androidTest/java/com/otli/app/auth/adapters/firestore/FirestoreAuthRepositoryTest.kt` RED against the emulator (register customer → doc has `status=active`; register merchant/courier → `status=pending`; login with bad credentials → rejected and no session resolved), then implement GREEN. Run: `npm --prefix backend run test:android`.
  - Acceptance: auth-roles spec — "Invalid credentials are rejected", plus the three registration scenarios (adapter-level proof that PR 1.1's policy and PR 1.2's rules are wired correctly end-to-end).
  - Est. lines: ~230

**PR 1.3** = tasks 1.3.1–1.3.2 (~280 lines). Verify: `npm --prefix backend run test:android` (requires a running Android emulator/device). Rollback: revert this PR; depends on PR 1.1 (types) and PR 1.2 (rules), both already merged.

### PR 1.4 — UI and navigation

- [x] 1.4.1 Create `core/navigation/RootNavHost.kt` (`@Serializable` type-safe routes: `SignedOutGraph`, `Gate.PendingApproval`, `Gate.Suspended`, `Gate.ProfileIncomplete`, `CustomerGraph`, `MerchantGraph`, `CourierGraph`, `AdminGraph`) driven by `ObserveSessionUseCase`'s `SessionState`, plus minimal empty-home composables per role (`CustomerHomeScreen`, `MerchantHomeScreen`, `CourierHomeScreen`, `AdminHomeScreen` — placeholders filled by later slices) and `core/theme/Theme.kt`.
  - Test-first: write `app/src/test/java/com/otli/app/core/navigation/RootNavHostRoutingTest.kt` RED (Robolectric + Compose UI test, using a fake session `StateFlow`: each `SessionState` value routes to the expected graph/screen and no other role's screen is reachable), then implement GREEN.
  - Acceptance: auth-roles spec — "Each role reaches its own home", "A user cannot perform another role's action" (UI-level: no navigation path exists to another role's screens).
  - Est. lines: ~180
- [ ] 1.4.2 Create `auth/adapters/ui/RegisterScreen.kt` (container) + `RegisterContent.kt` (presentational: role picker excluding Admin, email/password/profile fields) + `RegisterViewModel.kt` (`StateFlow<RegisterUiState>`) and `app/src/test/java/com/otli/app/auth/adapters/ui/RegisterViewModelTest.kt`.
  - Test-first: write the ViewModel test RED (submitting with Admin as a role is impossible because it's not in the picker's domain — assert the picker's role list excludes Admin; successful submit calls `AuthRepository.register`; failure maps to an error message), then implement GREEN.
  - Acceptance: auth-roles spec — "Admin accounts are never created through self-registration".
  - Est. lines: ~150
- [ ] 1.4.3 Create `auth/adapters/ui/LoginScreen.kt` + `LoginContent.kt` + `LoginViewModel.kt` and its test; create `auth/adapters/ui/GateScreens.kt` (`PendingApprovalContent`, `SuspendedContent`, role-appropriate messages) and a Compose UI test asserting the correct message text per status.
  - Test-first: write `LoginViewModelTest` and `GateScreensTest` RED, then implement GREEN.
  - Acceptance: auth-roles spec — "Invalid credentials are rejected", "Pending merchant or courier is blocked with a clear message", "Suspended account is blocked with a clear message", "Active account has unrestricted role access".
  - Est. lines: ~170

**PR 1.4** = tasks 1.4.1–1.4.3 (~500 lines — over budget; split at merge time into 1.4a (1.4.1, ~180 lines) and 1.4b (1.4.2+1.4.3, ~320 lines) if the chosen chain strategy needs a hard split). Verify: `./gradlew testDebugUnitTest`. Rollback: revert 1.4b independently of 1.4a (navigation shell can ship before the concrete forms).

---

## Phase 2: Slice 2 — Merchant Catalog and Customer Browse (`merchant-catalog`)

### PR 2.1 — Domain

- [ ] 2.1.1 Create `catalog/domain/Merchant.kt` (name, description, photo ref, `isOpen`, location), `catalog/domain/Category.kt`, `catalog/domain/Product.kt` (name, `Money` price, `isAvailable`, `photoVersion`), and validation functions (non-blank name, positive price) in `catalog/domain/CatalogValidation.kt`.
  - Test-first: write `app/src/test/java/com/otli/app/catalog/domain/CatalogValidationTest.kt` RED (blank name rejected, zero/negative price rejected, valid product accepted), then implement GREEN.
  - Acceptance: merchant-catalog spec — "Merchant creates a product with a NIO price" (validation half; persistence is PR 2.2/2.3).
  - Est. lines: ~110
- [ ] 2.1.2 Create `catalog/application/MerchantRepository.kt` and `catalog/application/CatalogRepository.kt` ports (`observeMerchant`, `updateProfile`, `setOpen`, `observeCategories`, `upsertCategory`, `removeCategory`, `observeProducts`, `upsertProduct`, `removeProduct`, `setProductAvailability`, `observeMerchantsList`, `observeStorefront(merchantId)`).
  - Test-first: N/A (interfaces).
  - Acceptance: compiles; consumed by PR 2.3/2.4.
  - Est. lines: ~70

**PR 2.1** = tasks 2.1.1–2.1.2 (~180 lines).

### PR 2.2 — Firestore rules for catalog collections

- [ ] 2.2.1 Extend `backend/firestore.rules` with `merchants/{uid}` (owner create with `status='pending'`/`isOpen=false`, owner update of profile fields, Admin update of `status`, read by any signed-in user), `merchants/{uid}/categories/{cid}` (active-owner CRUD), `merchants/{uid}/products/{pid}` (active-owner CRUD, `priceCents > 0` enforced), `merchants/{uid}/productPhotos/{pid}` (active-owner write, `jpeg` bytes ≤ 300 KB). Add the `merchants(status ASC, name ASC)` composite index.
  - Test-first: write `backend/tests/rules/merchants.test.ts` and `backend/tests/rules/catalog.test.ts` RED first (owner CRUD succeeds; cross-merchant write denied; oversized photo denied; non-owner read still allowed), then extend rules GREEN.
  - Acceptance: merchant-catalog spec — "Merchant creates a product with a NIO price", "Merchant cannot manage another merchant's catalog", "Merchant closes the store" (rules half).
  - Est. lines: ~340
- [ ] 2.2.2 Extend `backend/scripts/seed.ts`: two `active` merchants (one `isOpen=true`, one `isOpen=false`), 2 categories and ~5 products each per ADR-15, one `pending` merchant (for the Slice 6 approval demo).
  - Test-first: N/A (seed script) — acceptance is a manual re-run staying idempotent.
  - Acceptance: unblocks Slices 3–5 catalog browsing before Slice 6's approval UI exists.
  - Est. lines: ~50

**PR 2.2** = tasks 2.2.1–2.2.2 (~390 lines). Verify: `npm --prefix backend test -- merchants catalog`.

### PR 2.3 — Merchant management UI

- [ ] 2.3.1 Create `catalog/adapters/firestore/FirestoreMerchantRepository.kt` and `FirestoreCatalogRepository.kt` implementing the ports from 2.1.2, plus `catalog/di/CatalogModule.kt`.
  - Test-first: write `app/src/androidTest/java/com/otli/app/catalog/adapters/firestore/FirestoreCatalogRepositoryTest.kt` RED against the emulator (create category/product round trip, availability toggle reflected in a listener), then implement GREEN.
  - Acceptance: merchant-catalog spec — "Merchant creates a product with a NIO price", "Merchant marks a product unavailable" (adapter half).
  - Est. lines: ~180
- [ ] 2.3.2 Create `catalog/adapters/device/ImageCompressor.kt` (resize ≤ 640px, JPEG quality ~70, per ADR-11) and a JVM unit test using a fixture bitmap/byte array asserting the output stays ≤ 300 KB for a representative input.
  - Test-first: write the compressor test RED, then implement GREEN.
  - Acceptance: supports photo upload within the rules-enforced 300 KB cap (ADR-11); no direct spec scenario, but required by "Active merchant edits their profile" (photo field).
  - Est. lines: ~90
- [ ] 2.3.3 Create `catalog/adapters/ui/MerchantProfileScreen.kt`/`Content`/`ViewModel` (edit name/description/photo, open/closed toggle) and its ViewModel test.
  - Test-first: write `MerchantProfileViewModelTest` RED, then implement GREEN.
  - Acceptance: merchant-catalog spec — "Active merchant edits their profile", "Merchant closes the store".
  - Est. lines: ~150
- [ ] 2.3.4 Create `catalog/adapters/ui/MerchantCatalogScreen.kt`/`Content`/`ViewModel` (category/product CRUD list, availability toggle) and its ViewModel test.
  - Test-first: write `MerchantCatalogViewModelTest` RED, then implement GREEN.
  - Acceptance: merchant-catalog spec — "Merchant creates a product with a NIO price", "Merchant marks a product unavailable", "Merchant cannot manage another merchant's catalog" (UI never offers another merchant's items — no cross-merchant screen path exists).
  - Est. lines: ~190

**PR 2.3** = tasks 2.3.1–2.3.4 (~610 lines — split at merge into 2.3a (2.3.1+2.3.2, ~270 lines) and 2.3b (2.3.3+2.3.4, ~340 lines)). Verify: `./gradlew testDebugUnitTest` + `npm --prefix backend run test:android`.

### PR 2.4 — Customer browsing UI

- [ ] 2.4.1 Create `catalog/adapters/ui/MerchantListScreen.kt`/`Content`/`ViewModel` (lists merchants with open/closed indicator) and its test.
  - Test-first: write `MerchantListViewModelTest` RED, then implement GREEN.
  - Acceptance: merchant-catalog spec — "Customer browses merchants list".
  - Est. lines: ~140
- [ ] 2.4.2 Create `catalog/adapters/ui/StorefrontScreen.kt`/`Content`/`ViewModel` (categories + available products; closed-store banner; unavailable products visually distinct and not addable) and its test.
  - Test-first: write `StorefrontViewModelTest` RED (closed store → `canAddToCart=false` for every product; unavailable product → not addable even if store open), then implement GREEN.
  - Acceptance: merchant-catalog spec — "Customer browses an open merchant's available products", "Customer sees a closed store is unavailable for ordering".
  - Est. lines: ~180

**PR 2.4** = tasks 2.4.1–2.4.2 (~320 lines). Verify: `./gradlew testDebugUnitTest`.

---

## Phase 3: Slice 3 — Cart, Checkout, and Order Placement (`ordering`)

### PR 3.1 — Domain

- [ ] 3.1.1 Create `ordering/domain/OrderStatus.kt`, `ordering/domain/Actor.kt`, `ordering/domain/OrderTransitions.kt` (the `allowed: Set<Triple<...>>` table plus `isAllowed(from, to, actor)`) and `app/src/test/java/com/otli/app/ordering/domain/OrderTransitionsContractTest.kt` that parses `backend/contracts/order-transitions.json` (created in task 0.4) and asserts `OrderTransitions.allowed` is exactly equal to the fixture (ADR-14).
  - Test-first: write the contract-parity test RED (table doesn't exist yet), then implement `OrderTransitions` GREEN so it matches the fixture exactly.
  - Acceptance: ordering spec — "Order State Machine With Actor-Authorized Transitions" (all 9 valid transitions), "Merchant cannot progress an order out of sequence".
  - Est. lines: ~120
- [ ] 3.1.2 Create `ordering/domain/Cart.kt` (single-merchant invariant, `AddResult` sealed interface: `Added`/`ConflictingMerchant`) and `app/src/test/java/com/otli/app/ordering/domain/CartTest.kt`.
  - Test-first: write `CartTest` RED (same-merchant add succeeds silently; different-merchant add returns `ConflictingMerchant` and leaves the cart unchanged until confirmed), then implement GREEN.
  - Acceptance: ordering spec — "Customer adds a product from the same merchant", "Customer adds a product from a different merchant".
  - Est. lines: ~110
- [ ] 3.1.3 Create `ordering/domain/CheckoutCalculator.kt` (`Totals(subtotal, fee, total)`, `total == subtotal + fee`) and `app/src/test/java/com/otli/app/ordering/domain/CheckoutCalculatorTest.kt`.
  - Test-first: write the test RED (subtotal from item unit prices × quantity; total = subtotal + fee, using `Money` arithmetic), then implement GREEN.
  - Acceptance: ordering spec — "Order total equals subtotal plus fee at placement time" (domain half).
  - Est. lines: ~80

**PR 3.1** = tasks 3.1.1–3.1.3 (~310 lines). Verify: `./gradlew testDebugUnitTest`.

### PR 3.2 — Order creation rules (fee snapshot, availability)

- [ ] 3.2.1 Extend `backend/firestore.rules` with `orders/{orderId}` `create()`: customer-owner only, `deliveryFeeCents == get(settings/app).deliveryFeeCents`, `totalCents == subtotalCents + deliveryFeeCents`, `subtotalCents > 0`, `1 ≤ items.size() ≤ 30`, `paymentMethod == 'cash'`, merchant `status == 'active' && isOpen == true`, `courierId == null`. Create the `settings/app` document read path (needs a default doc — add a seed task). Add `orders(customerId ASC, createdAt DESC)` and `orders(merchantId ASC, createdAt DESC)` composite indexes.
  - Test-first: write `backend/tests/rules/order-create.test.ts` RED first (valid create succeeds; wrong fee/total denied; closed-store denied; unavailable-item-referencing product denied at the app layer per 3.2.3 — rules cannot loop over items, so only aggregate totals and store status are rule-checked per ADR-10; oversized item list denied; non-cash denied), then extend rules GREEN.
  - Acceptance: ordering spec — "Order total equals subtotal plus fee at placement time", "Placement blocked by a closed store"; admin spec — "Admin updates the flat fee" (fee-read half only; fee mutation itself is Slice 6).
  - Est. lines: ~260
- [ ] 3.2.2 Extend `backend/scripts/seed.ts` with `settings/app.deliveryFeeCents = 3000` (C$ 30.00) per ADR-15, and one `active` customer (`seed-customer-1`).
  - Test-first: N/A (seed data).
  - Acceptance: unblocks order placement testing before Slice 6's fee UI exists.
  - Est. lines: ~30
- [ ] 3.2.3 Create `ordering/application/PlaceOrder.kt` use case: reads current cart + live product availability + merchant open status via the ports, rejects locally (before ever writing) if any item became unavailable or the store closed since being added to the cart, otherwise builds the `OrderDraft` (denormalized snapshot per `design.md`'s Firestore Data Model) and calls `OrderRepository.place`. Create `app/src/test/java/com/otli/app/ordering/application/PlaceOrderTest.kt` with fake ports.
  - Test-first: write `PlaceOrderTest` RED (happy path places with correct snapshot; closed-store rejection names the merchant; unavailable-item rejection names the item), then implement GREEN.
  - Acceptance: ordering spec — "Placement blocked by a closed store", "Placement blocked by a product becoming unavailable", "A later fee change does not affect existing orders" (client-side snapshot half; server enforcement is 3.2.1).
  - Est. lines: ~160

**PR 3.2** = tasks 3.2.1–3.2.3 (~450 lines — split at merge into 3.2a (3.2.1+3.2.2, ~290 lines) and 3.2b (3.2.3, ~160 lines) if needed).

### PR 3.3 — Merchant transition and customer cancel rules

- [ ] 3.3.1 Extend `backend/firestore.rules` `orders/{orderId}` with `merchantStep()` (`placed→accepted`, `accepted→preparing`, `preparing→ready`, each restricted to `changed().hasOnly([...])` matching the transition), `merchantReject()` (`placed→rejected` requiring non-empty `rejectReason` ≤ 200 chars), and `customerCancel()` (`placed→cancelled`, owner-only, `cancelledBy=='customer'`). Combine into the `allow update` clause alongside the claim/deliver/release functions stubbed as `false` placeholders (implemented in Slice 4/6).
  - Test-first: write `backend/tests/rules/order-transitions.test.ts` RED first, iterating `backend/contracts/order-transitions.json` per ADR-14 (every allowed `(from,to,actor)` triple that this PR's functions cover succeeds; every other combination is denied, including out-of-sequence jumps and wrong-actor attempts), then extend rules GREEN.
  - Acceptance: ordering spec — "Merchant rejects a placed order with a reason", "Merchant rejects without a reason is refused", "Customer cancels only while placed", "Customer cannot cancel once accepted or later", "A different customer cannot cancel someone else's order", "Merchant cannot progress an order out of sequence".
  - Est. lines: ~300

**PR 3.3** = task 3.3.1 (~300 lines). Verify: `npm --prefix backend test -- order-transitions`.

### PR 3.4 — Cart and checkout UI

- [ ] 3.4.1 Create `ordering/application/OrderRepository.kt` port and `ordering/adapters/firestore/FirestoreOrderRepository.kt` (`place`, `observe`, `observeForCustomer`, `observeForMerchant`, `transition`), plus `ordering/di/OrderingModule.kt`.
  - Test-first: write `app/src/androidTest/java/com/otli/app/ordering/adapters/firestore/FirestoreOrderRepositoryTest.kt` RED against the emulator (place → observe round trip; transition denied when rules reject it), then implement GREEN.
  - Acceptance: ordering spec — "Real-time visibility of state changes" (adapter half — listener delivers updates).
  - Est. lines: ~190
- [ ] 3.4.2 Create `ordering/adapters/ui/CartScreen.kt`/`Content`/`ViewModel` (add/remove items, cross-merchant confirm dialog wired to `Cart.AddResult`) and its test.
  - Test-first: write `CartViewModelTest` RED, then implement GREEN.
  - Acceptance: ordering spec — "Customer adds a product from the same merchant", "Customer adds a product from a different merchant" (UI half).
  - Est. lines: ~160
- [ ] 3.4.3 Create `ordering/adapters/ui/CheckoutScreen.kt`/`Content`/`ViewModel` (MapLibre `AndroidView` pin picker from `tracking/adapters/ui/MapStyle.kt` — created here since checkout is the first map consumer — textual reference field, cash-confirmation checkbox, calls `PlaceOrder`) and its test.
  - Test-first: write `CheckoutViewModelTest` RED (missing pin blocks submission; complete form calls `PlaceOrder` and surfaces its rejection reasons), then implement GREEN.
  - Acceptance: ordering spec — "Customer completes checkout with pin and reference", "Checkout is blocked without a pin".
  - Est. lines: ~220

**PR 3.4** = tasks 3.4.1–3.4.3 (~570 lines — split at merge into 3.4a (3.4.1, ~190 lines) and 3.4b (3.4.2+3.4.3, ~380 lines)).

### PR 3.5 — Merchant order board, customer tracking screen, local notifications

- [ ] 3.5.1 Create `core/notification/NotificationPort.kt` (interface) and `ordering/adapters/notification/LocalOrderNotifier.kt` (raises an Android notification when an observed order's status changes, per ADR-12) and a unit test using a fake `Flow<Order>` asserting one notification per distinct status change (no duplicate on unrelated field updates).
  - Test-first: write the notifier test RED, then implement GREEN.
  - Acceptance: proposal — "Best-effort push notifications for order status changes"; ADR-12.
  - Est. lines: ~110
- [ ] 3.5.2 Create `ordering/adapters/ui/MerchantOrderBoardScreen.kt`/`Content`/`ViewModel` (incoming `placed` orders with accept/reject/reason dialog, in-progress orders with preparing→ready actions) and its test.
  - Test-first: write `MerchantOrderBoardViewModelTest` RED, then implement GREEN.
  - Acceptance: ordering spec — "Merchant rejects a placed order with a reason", "Merchant rejects without a reason is refused", "Real-time visibility of state changes" (merchant side).
  - Est. lines: ~180
- [ ] 3.5.3 Create `ordering/adapters/ui/CustomerOrderTrackingScreen.kt`/`Content`/`ViewModel` (shows current status, cancel button enabled only while `placed`) and its test.
  - Test-first: write `CustomerOrderTrackingViewModelTest` RED (cancel button `enabled` iff status is `placed`; cancel calls `transition(cancelled)`), then implement GREEN.
  - Acceptance: ordering spec — "Customer cancels only while placed", "Customer cannot cancel once accepted or later", "Real-time visibility of state changes" (customer side).
  - Est. lines: ~150

**PR 3.5** = tasks 3.5.1–3.5.3 (~440 lines — split at merge into 3.5a (3.5.1, ~110 lines) and 3.5b (3.5.2+3.5.3, ~330 lines) if needed).

---

## Phase 4: Slice 4 — Courier Pool and Race-Safe Claim (`courier-dispatch`)

### PR 4.1 — Domain

- [ ] 4.1.1 Create `dispatch/domain/ClaimPolicy.kt` (`ClaimDecision` sealed interface: `Allowed`/`Denied(ClaimDenial)`; `ClaimDenial`: `NOT_READY`, `ALREADY_CLAIMED`, `COURIER_BUSY`, `COURIER_OFFLINE`, `COURIER_NOT_ACTIVE`) and `app/src/test/java/com/otli/app/dispatch/domain/ClaimPolicyTest.kt`.
  - Test-first: write `ClaimPolicyTest` RED (table-driven over every combination of order status/courier assignment/courier online/courier active/courier busy), then implement GREEN.
  - Acceptance: courier-dispatch spec — "A courier with an active order cannot claim another order", "Claiming an order already claimed by someone else fails cleanly" (domain half; rules in PR 4.2 are the real authority per ADR-7).
  - Est. lines: ~150
- [ ] 4.1.2 Create `dispatch/application/DispatchRepository.kt` port (`observePool`, `claim`, `markPickedUp`, `markDelivered`, `setOnline`).
  - Test-first: N/A (interface).
  - Acceptance: compiles; consumed by PR 4.3.
  - Est. lines: ~40

**PR 4.1** = tasks 4.1.1–4.1.2 (~190 lines).

### PR 4.2a — Claim/deliver/release pairing rules

- [ ] 4.2.1 Extend `backend/firestore.rules`: `orders/{orderId}` gets `claim()` (ready→claimed, `courierId==null`→`request.auth.uid`, `getAfter()` on `couriers/{uid}` requires `activeOrderId==orderId`), `deliver()` (`picked_up→delivered`, `courierId` must match, `getAfter()` requires the courier's `activeOrderId==null`), and a `pickUp()` function (`claimed→picked_up`, assigned courier only, no paired write); `couriers/{uid}` gets `pairedClaim()`, `pairedDeliver()`, and `availability()` (self-toggle `isOnline`, blocked while `activeOrderId != null` unless going online) exactly as shown in `design.md`'s rules excerpt. Add the `orders(status ASC, readyAt ASC)` composite index (courier pool query).
  - Test-first: write `backend/tests/rules/claim-pairing.test.ts` and `backend/tests/rules/courier-availability.test.ts` RED first (single-claim happy path succeeds with both documents updated; claim on a non-ready order denied; claim by an offline/busy courier denied; pickup/deliver by a non-assigned courier denied; deliver-before-pickup denied; a direct non-transactional write to a `claimed` order is denied; an order-only or courier-only half of a claim is denied), then extend rules GREEN.
  - Acceptance: courier-dispatch spec — "Two couriers claim the same order concurrently" (single-attempt half; true concurrency is PR 4.2b), "A courier with an active order cannot claim another order", "Claiming an order already claimed by someone else fails cleanly", "Claiming courier marks the order picked up", "A different courier cannot mark someone else's claimed order picked up", "Claiming courier marks the order delivered", "Cannot mark delivered before pickup", "Offline courier cannot see the pool" (availability toggle rule half).
  - Est. lines: ~330

**PR 4.2a** = task 4.2.1 (~330 lines). Verify: `npm --prefix backend test -- claim-pairing courier-availability`.

### PR 4.2b — Mandatory concurrency test suite

- [ ] 4.2.2 Create `backend/tests/rules/claim-concurrency.test.ts` with the four mandatory cases from `design.md`'s Testing Strategy: (1) N=2 couriers race-claim the same `ready` order via `Promise.allSettled` with real transactions — exactly one fulfilled, order shows the winner, only the winner's `activeOrderId` is set; (2) repeat with N=10 couriers; (3) one courier concurrently attempts to claim two different `ready` orders — exactly one succeeds; (4) confirm the direct-write and half-claim denials from 4.2.1 also hold under concurrent load. Wrap the suite in a 20-iteration loop to surface flakiness per the design's mandate. No rules changes expected here — this PR is pure proof against PR 4.2a's rules.
  - Test-first: this task IS the RED→GREEN cycle — the suite must fail against a rules file without the pairing from 4.2.1 (regression-proof it briefly against `git stash` of 4.2a if convenient) and pass against the merged rules.
  - Acceptance: courier-dispatch spec — "Two couriers claim the same order concurrently — exactly one wins" (the literal scenario); proposal success criteria — "When two couriers claim the same ready order simultaneously, exactly one succeeds (verified by an automated concurrency test and a live demo)".
  - Est. lines: ~260

**PR 4.2b** = task 4.2.2 (~260 lines). Verify: `npm --prefix backend test -- claim-concurrency` (run at least once with the loop enabled, not skipped). Rollback: revertable independently of 4.2a (removing the extra proof does not remove the underlying rule authority).

### PR 4.3 — Firestore dispatch adapter

- [ ] 4.3.1 Create `dispatch/adapters/firestore/FirestoreDispatchRepository.kt` implementing `DispatchRepository`: `claim` runs a `runTransaction` writing both `orders/{orderId}` and `couriers/{uid}` per ADR-7; `markDelivered` is the paired release transaction; `observePool` queries `status=='ready'` ordered by `readyAt`; `setOnline` is a plain document update. Add `dispatch/di/DispatchModule.kt`.
  - Test-first: write `app/src/androidTest/java/com/otli/app/dispatch/adapters/firestore/FirestoreDispatchRepositoryTest.kt` RED (two `FirebaseApp` instances signed in as different couriers racing `claim` on the same order against the emulator — exactly one succeeds, per `design.md`'s Integration testing row), then implement GREEN.
  - Acceptance: courier-dispatch spec — "Two couriers claim the same order concurrently — exactly one wins" (client-transaction half, complementing PR 4.2b's pure-rules proof).
  - Est. lines: ~230

**PR 4.3** = task 4.3.1 (~230 lines). Verify: `npm --prefix backend run test:android`.

### PR 4.4 — Dispatch UI

- [ ] 4.4.1 Create `dispatch/adapters/ui/AvailabilityToggleScreen.kt`/`Content`/`ViewModel` (online/offline switch) and its test.
  - Test-first: write the ViewModel test RED, then implement GREEN.
  - Acceptance: courier-dispatch spec — "Offline courier cannot see the pool", "Courier goes online and sees the pool" (UI half).
  - Est. lines: ~110
- [ ] 4.4.2 Create `dispatch/adapters/ui/PoolScreen.kt`/`Content`/`ViewModel` (lists `ready` orders when online and not busy, hides entirely when the courier has an active order, claim button surfaces `ClaimDenial` as a user message) and its test.
  - Test-first: write `PoolViewModelTest` RED (pool hidden while `activeOrderId != null`; pool reappears after a fake delivered event; claim failure shows the right denial message), then implement GREEN.
  - Acceptance: courier-dispatch spec — "Courier with an active order does not see the pool", "Courier's pool reappears after completing delivery", "Two couriers claim the same order concurrently" (losing courier sees it disappear).
  - Est. lines: ~200
- [ ] 4.4.3 Create `dispatch/adapters/ui/ActiveDeliveryScreen.kt`/`Content`/`ViewModel` (pickup/deliver action buttons enabled by current status) and its test.
  - Test-first: write `ActiveDeliveryViewModelTest` RED (picked-up button only enabled from `claimed`; delivered button only enabled from `picked_up`), then implement GREEN.
  - Acceptance: courier-dispatch spec — "Claiming courier marks the order picked up", "Claiming courier marks the order delivered", "Cannot mark delivered before pickup" (UI half).
  - Est. lines: ~140

**PR 4.4** = tasks 4.4.1–4.4.3 (~450 lines — split at merge into 4.4a (4.4.1+4.4.2, ~310 lines) and 4.4b (4.4.3, ~140 lines) if needed).

---

## Phase 5: Slice 5 — Live Courier Tracking (`live-tracking`)

### PR 5.1 — Domain

- [ ] 5.1.1 Create `tracking/domain/GeoFix.kt` (lat/lng/accuracy/timestamp) and `tracking/domain/LocationThrottle.kt` (pure function: publish iff `elapsed ≥ 10s && distance ≥ 10m`, or `elapsed ≥ 60s` heartbeat, per ADR-8) and `app/src/test/java/com/otli/app/tracking/domain/LocationThrottleTest.kt`.
  - Test-first: write `LocationThrottleTest` RED (table-driven across elapsed/distance combinations, including the heartbeat case), then implement GREEN.
  - Acceptance: live-tracking spec — "Rapid successive location reports are throttled" (client half; server floor is PR 5.2).
  - Est. lines: ~120

**PR 5.1** = task 5.1.1 (~120 lines).

### PR 5.2 — `liveLocations` rules

- [ ] 5.2.1 Extend `backend/firestore.rules` with `liveLocations/{orderId}`: read restricted to the order's customer, its assigned courier, and Admin; create/update restricted to the assigned active courier while the order is `claimed`/`picked_up`, `updatedAt == request.time`, `keys().hasOnly([...])` exactly as in `design.md`'s excerpt, and the 5-second write floor (`resource == null || request.time > resource.data.updatedAt + duration.value(5, 's')`).
  - Test-first: write `backend/tests/rules/live-locations.test.ts` RED first (assigned courier can write while claimed/picked_up, denied once delivered or while still ready; a write within 5s of the last one is denied; the order's customer and Admin can read, a different customer/merchant/courier cannot), then extend rules GREEN.
  - Acceptance: live-tracking spec — "Location publishes while claimed and picked up", "Location stops at delivery", "No tracking data before claim", "The order's own customer sees the courier's live position" (read half), "A different customer cannot see the courier's location", "Admin can view live location for oversight", "The merchant cannot see courier live location".
  - Est. lines: ~240

**PR 5.2** = task 5.2.1 (~240 lines). Verify: `npm --prefix backend test -- live-locations`.

### PR 5.3 — Location publishing service

- [ ] 5.3.1 Create `tracking/application/LocationSource.kt` and `LocationRepository.kt` ports, `tracking/adapters/device/FusedLocationSource.kt` (`FusedLocationProviderClient` wrapped as `Flow<GeoFix>`), `tracking/adapters/firestore/FirestoreLocationRepository.kt` (implements `publish`/`observe` against `liveLocations/{orderId}`), and `tracking/di/TrackingModule.kt`.
  - Test-first: write `app/src/androidTest/java/com/otli/app/tracking/adapters/firestore/FirestoreLocationRepositoryTest.kt` RED (publish while `claimed` succeeds and is observable; publish attempted twice within 5s — second is rejected by rules and the adapter surfaces that), then implement GREEN.
  - Acceptance: live-tracking spec — "Location publishes while claimed and picked up" (adapter half).
  - Est. lines: ~180
- [ ] 5.3.2 Create `tracking/adapters/service/DeliveryTrackingService.kt` (foreground service, `foregroundServiceType="location"`, starts when the courier's `activeOrderId` becomes non-null, applies `LocationThrottle` before each `publish`, stops on `activeOrderId==null` or order leaving `claimed`/`picked_up`) and register it in `AndroidManifest.xml`. Create a Robolectric service test asserting start/stop transitions on fake `activeOrderId` changes.
  - Test-first: write the service test RED, then implement GREEN.
  - Acceptance: live-tracking spec — "Location publishes while claimed and picked up", "Location stops at delivery"; ADR-8 (service lifecycle).
  - Est. lines: ~170

**PR 5.3** = tasks 5.3.1–5.3.2 (~350 lines). Verify: `./gradlew testDebugUnitTest` + `npm --prefix backend run test:android`.

### PR 5.4 — Live map UI

- [ ] 5.4.1 Create `tracking/adapters/ui/MapStyle.kt` (OpenFreeMap style URL constant + OSM raster fallback style JSON, attribution) — if not already created in task 3.4.3, this is where the fallback path and attribution UI are added.
  - Test-first: N/A (constants + attribution rendering, covered by the UI test in 5.4.2).
  - Acceptance: ADR-13.
  - Est. lines: ~50
- [ ] 5.4.2 Create `tracking/adapters/ui/LiveMapScreen.kt`/`Content`/`ViewModel` (MapLibre `AndroidView` showing the assigned courier's live marker, only rendered for the order's customer or Admin) and its test.
  - Test-first: write `LiveMapViewModelTest` RED (marker position updates from `LocationRepository.observe`; screen is not reachable/visible for a non-owner customer, merchant, or unrelated courier — assert the ViewModel refuses to expose location data for an unauthorized viewer role), then implement GREEN.
  - Acceptance: live-tracking spec — "The order's own customer sees the courier's live position" (UI half), "A different customer cannot see the courier's location", "Admin can view live location for oversight", "The merchant cannot see courier live location".
  - Est. lines: ~200

**PR 5.4** = tasks 5.4.1–5.4.2 (~250 lines).

---

## Phase 6: Slice 6 — Admin Settings and Oversight (`admin`)

### PR 6.1 — Domain and application

- [ ] 6.1.1 Create `admin/application/AdminRepository.kt` port (`setAccountStatus`, `setDeliveryFee`, `releaseClaim`, `cancelOrder`, `observeStuckOrders`, `observePendingAccounts`, `observeAllOrders`) exactly as specified in `design.md`.
  - Test-first: N/A (interface).
  - Acceptance: compiles; consumed by PR 6.3.
  - Est. lines: ~50
- [ ] 6.1.2 Create `admin/application/ApproveAccount.kt`, `SuspendAccount.kt`, `UpdateFee.kt`, `ReleaseClaim.kt`, `CancelOrder.kt` use cases (thin orchestration over the port; `ReleaseClaim`/`CancelOrder` enforce the pre-conditions from the spec — release only from `claimed`, cancel-stuck only from `ready`-with-no-courier, admin-cancel allowed from `placed`/`accepted`/`preparing`/`ready`) and `app/src/test/java/com/otli/app/admin/application/AdminUseCasesTest.kt` with fake ports.
  - Test-first: write `AdminUseCasesTest` RED (each use case's precondition, success, and rejection paths), then implement GREEN.
  - Acceptance: admin spec — "Admin updates the flat fee", "Admin approves a pending merchant", "Admin approves a pending courier", "Admin suspends an active merchant", "Admin suspends an active courier", "Admin releases a claimed order back to the pool", "Admin sees unclaimed ready orders", "Admin cancels a stuck order" (application half of every one).
  - Est. lines: ~220

**PR 6.1** = tasks 6.1.1–6.1.2 (~270 lines).

### PR 6.2 — Admin rules

- [ ] 6.2.1 Extend `backend/firestore.rules`: `settings/app` (Admin-only write of `deliveryFeeCents`, signed-in read — already partially covered by PR 3.2.1's read path), `users/{uid}`+`merchants/{uid}` batched status mirror update (Admin-only, both documents in one batch), `orders/{orderId}` `release()` (`claimed→ready`, Admin-only, paired `couriers/{uid}.activeOrderId→null` via `getAfter()`) and `adminCancel()` (`placed|accepted|preparing|ready → cancelled`, Admin-only, non-empty `cancelReason`, `cancelledBy=='admin'`). Add the `orders(status ASC, createdAt ASC)` composite index (Admin stuck view) if not already present.
  - Test-first: write `backend/tests/rules/admin.test.ts` RED first (fee update by Admin succeeds, by anyone else denied; status-mirror batch succeeds atomically; release from `claimed` succeeds and pairs the courier write, release attempted by a non-admin denied; admin-cancel from each allowed source state succeeds with a reason, denied without one, denied from `claimed`/`picked_up`/`delivered`/`rejected`/`cancelled`), then extend rules GREEN.
  - Acceptance: admin spec — every scenario under "Flat Delivery Fee Configuration", "Merchant and Courier Approval", "Merchant and Courier Suspension", "Release of Abandoned Claims" (rules half).
  - Est. lines: ~320

**PR 6.2** = task 6.2.1 (~320 lines). Verify: `npm --prefix backend test -- admin`.

### PR 6.3 — Firestore admin adapter, fee and approvals UI

- [ ] 6.3.1 Create `admin/adapters/firestore/FirestoreAdminRepository.kt` implementing the port from 6.1.1 and `admin/di/AdminModule.kt`.
  - Test-first: write `app/src/androidTest/java/com/otli/app/admin/adapters/firestore/FirestoreAdminRepositoryTest.kt` RED (fee update round trip; approve/suspend round trip; release-claim round trip clearing both documents), then implement GREEN.
  - Acceptance: admin spec adapter-level proof for the same scenarios as 6.2.1.
  - Est. lines: ~180
- [ ] 6.3.2 Create `admin/adapters/ui/FeeSettingsScreen.kt`/`Content`/`ViewModel` and its test.
  - Test-first: write the ViewModel test RED, then implement GREEN.
  - Acceptance: admin spec — "Admin updates the flat fee".
  - Est. lines: ~110
- [ ] 6.3.3 Create `admin/adapters/ui/ApprovalsScreen.kt`/`Content`/`ViewModel` (pending merchants/couriers list, approve/suspend actions) and its test.
  - Test-first: write the ViewModel test RED, then implement GREEN.
  - Acceptance: admin spec — "Admin approves a pending merchant", "Admin approves a pending courier", "Admin suspends an active merchant", "Admin suspends an active courier", "Pending account remains blocked until approved".
  - Est. lines: ~150

**PR 6.3** = tasks 6.3.1–6.3.3 (~440 lines — split at merge into 6.3a (6.3.1, ~180 lines) and 6.3b (6.3.2+6.3.3, ~260 lines) if needed).

### PR 6.4 — Stuck orders, order list, release/cancel UI, demo script

- [ ] 6.4.1 Create `admin/adapters/ui/StuckOrdersScreen.kt`/`Content`/`ViewModel` (lists unclaimed `ready` orders, release-claim action for `claimed` orders shown elsewhere, cancel action) and its test.
  - Test-first: write the ViewModel test RED, then implement GREEN.
  - Acceptance: admin spec — "Admin sees unclaimed ready orders", "Admin cancels a stuck order", "Admin releases a claimed order back to the pool", "Non-admin cannot release a claim" (UI never exposes the action to a non-admin — no such path exists), "Non-admin cannot change the fee" (same reasoning).
  - Est. lines: ~180
- [ ] 6.4.2 Create `admin/adapters/ui/OrderListScreen.kt`/`Content`/`ViewModel` (all orders across merchants/customers with status) and its test.
  - Test-first: write the ViewModel test RED, then implement GREEN.
  - Acceptance: admin spec — "Admin views the full order list".
  - Est. lines: ~130
- [ ] 6.4.3 Write `docs/demo-script.md`: the manual E2E checklist from `design.md`'s Testing Strategy (full happy path on two-plus devices, claim race by simultaneous tap, Admin release/cancel), and update `README.md` with the final run/demo instructions.
  - Test-first: N/A (documentation).
  - Acceptance: proposal success criteria — every unchecked box in "Success Criteria (academic demo)" maps to one demo-script step.
  - Est. lines: ~90

**PR 6.4** = tasks 6.4.1–6.4.3 (~400 lines).

### PR 6.5 — Production deployment (requires user authorization; not counted against the review budget)

- [ ] 6.5.1 **Requires user authorization.** Create the Firebase project on the Spark plan and register the Android app (produces the real `google-services.json`, replacing the placeholder committed in Phase 0).
  - Acceptance: proposal Dependencies — "A free-tier BaaS project (Firebase proposed)".
- [ ] 6.5.2 **Requires user authorization.** Deploy security rules and indexes to production: `firebase deploy --only firestore:rules,firestore:indexes`.
  - Acceptance: rules identical to the ones proven green in every `backend/tests/rules/*.test.ts` file across Phases 1–6.
- [ ] 6.5.3 **Requires user authorization.** Run `backend/scripts/seed.ts --target=production --confirm` with `GOOGLE_APPLICATION_CREDENTIALS` and `OTLI_SEED_PASSWORD` set, per ADR-15.
  - Acceptance: production accounts exist for the live-device demo.

Do not execute 6.5.1–6.5.3 automatically; each is a remote operation and must be explicitly authorized by the user at the time it runs, per the proposal's dependency on a real Firebase project and `design.md`'s Migration/Rollout section.

## Key Learnings

1. The design's rules excerpt (claim/deliver/release pairing via `getAfter()`) had to be split across two PRs (4.2a rules, 4.2b concurrency proof) so a flaky concurrency test never blocks unrelated rules review.
2. Every rules-touching PR needed its own composite index addition tracked alongside the rule, since `firestore.indexes.json` and `firestore.rules` evolve together per query.
3. Slice 3 (ordering) is the single heaviest slice because it is the first consumer of the map widget (checkout pin) and carries both the fee-snapshot rule and the full merchant transition rule set.
4. Splitting the mandatory concurrency test suite into its own PR (4.2b) keeps rollback independent: removing the extra 20-iteration proof never removes the underlying rule authority proven by 4.2a.
5. The seed script grows incrementally across Slices 1, 2, and 3 (not all at once) because each slice needs different pre-approved fixtures before its own UI exists.
