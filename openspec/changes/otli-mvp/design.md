# Design: Otli MVP — Four-Sided Delivery Marketplace for Nagarote

## Technical Approach

One native Android app (Kotlin + Jetpack Compose) talks directly to Firebase
(Auth, Cloud Firestore) on the free **Spark** plan. There is no custom server
and no Cloud Functions: every business invariant that must hold server-side
(role access, allowed order transitions, the race-safe claim, the one-active-
order rule, the fee snapshot, the location write floor) is enforced by
**Firestore Security Rules**, which are versioned, tested against the Firebase
Emulator Suite, and deployed from the repository.

The Android code is organized by feature ("screaming" packages: `auth`,
`catalog`, `ordering`, `dispatch`, `tracking`, `admin`), each with a
pragmatic hexagonal split: pure-Kotlin `domain`, `application` use cases that
depend on ports, and `adapters` (Firestore, device, UI). UI follows the
container/presentational pattern with `ViewModel` + `StateFlow`.

The order state machine exists twice by necessity (Kotlin for UX, rules for
authority). A single JSON transition contract, consumed by both test suites,
keeps them in lockstep.

Delivery follows the proposal's six vertical slices; this design maps every
slice to concrete packages, collections, rules blocks, and tests.

### Verification status of external claims

The docs lookup tool (context7) and web access were **not available** in this
design session, so third-party policies below are marked **[unverified]**.
Software versions were verified and pinned on 2026-09-29 by scaffold task 0.1 in
`gradle/libs.versions.toml` and `backend/package.json`; those files are the
source of truth and the version numbers in the table below match them. Pricing,
quota and tile-policy claims remain **[unverified]** until checked on the listed pages.

## Final Stack

| Concern | Choice | Notes |
|---|---|---|
| Language / UI | Kotlin 2.4.20 (K2) + Jetpack Compose (Compose BOM 2026.09.00), Material 3 | Android-only; Compose avoids XML/Fragments |
| Build | Gradle 9.8.0 Kotlin DSL + version catalog, AGP 9.4.1 (built-in Kotlin, no `kotlin-android` plugin, KSP only), JDK 17 toolchain for the Android build | |
| SDK levels | `minSdk 26` (Android 8.0), `targetSdk 36`, `compileSdk 37` (the pinned AndroidX releases require compileSdk 37; targetSdk stays 36) | 26 gives `java.time` without desugaring and covers the vast majority of devices in use; Firebase BoM 34 raised its own floor to 23 [unverified] |
| DI | Hilt (with KSP) | See ADR-4 |
| Navigation | Navigation Compose ≥ 2.8 type-safe (`@Serializable` routes) | See ADR-5 |
| Async / state | Coroutines + Flow, `ViewModel` + `StateFlow` | |
| Auth | Firebase Auth, **email/password only** | Phone auth (SMS) has Blaze/quota implications; not needed |
| Database / realtime | Cloud Firestore (Android SDK via Firebase BoM 34.19.0 — use the non-`-ktx` artifacts; KTX modules were folded into the main modules) | Listeners for real time, transactions for the claim |
| Server-side authority | Firestore Security Rules (rules-only, no Cloud Functions) | See ADR-2 |
| Maps | MapLibre Native Android (`org.maplibre.gl:android-sdk` 13.6.0) wrapped in Compose via `AndroidView` | No API key, no billing |
| Tiles | OpenFreeMap vector style (`https://tiles.openfreemap.org/styles/liberty`) [unverified: free, no key, attribution required]; fallback OSM raster tiles with a proper User-Agent and attribution | See ADR-13 |
| Location | Google Play services `FusedLocationProviderClient` (`play-services-location` 21.x) inside a `location`-type foreground service | |
| Images | Coil 3 for display; photos stored as compressed JPEG bytes in Firestore | See ADR-11 (Cloud Storage requires Blaze) |
| Notifications | Local notifications raised from Firestore listeners (best effort) | See ADR-12 (remote FCM push needs a server) |
| Backend tooling | Node.js 22 LTS, `firebase-tools` 15.32.0, JDK 21 for the emulators (verified working) | |
| Rules tests | TypeScript + Vitest + `@firebase/rules-unit-testing` 5.0.2 (paired with Firebase JS SDK 12.19.0), Vitest 5.0.2, TypeScript 7.0.2 | |
| Seed | TypeScript script using `firebase-admin` | |
| Android tests | JUnit 4, `kotlinx-coroutines-test`, Turbine, Truth (or `kotlin.test`), hand-written fakes (MockK only when a fake is impractical), Compose UI test (`ui-test-junit4`), optional Robolectric | |

Official pages to confirm in slice 1: Firebase pricing (`https://firebase.google.com/pricing`),
Firestore quotas (`https://firebase.google.com/docs/firestore/quotas`),
Firebase Android release notes (`https://firebase.google.com/support/release-notes/android`),
Cloud Storage Blaze requirement FAQ (`https://firebase.google.com/docs/storage/faqs-storage-changes-announced-sept-2024`),
Firebase CLI release notes (`https://github.com/firebase/firebase-tools/releases`),
MapLibre Android (`https://maplibre.org/maplibre-native/android/api/`),
OpenFreeMap (`https://openfreemap.org/`),
OSM tile usage policy (`https://operations.osmfoundation.org/policies/tiles/`),
Android API levels (`https://developer.android.com/tools/releases/platforms`).

## Architecture Decisions

### ADR-1: Kotlin + Compose + Firebase (Spark) + MapLibre

**Choice**: Native Kotlin/Compose app on Firebase Auth + Firestore (Spark plan), MapLibre Native with free vector tiles.
**Alternatives considered**: Flutter + Firebase (second language with no payoff for an Android-only app); Kotlin + Supabase (Postgres row locks are excellent for the claim, but free projects pause after one week of inactivity — a real risk for a demo schedule with gaps — and the Android client is less mature); Google Maps SDK (requires a Cloud Billing account with a payment method).
**Rationale**: Firestore transactions + rules implement the race-safe claim and server-side transitions without writing a server; real-time listeners give multi-device updates for free; the Emulator Suite enables quota-free automated tests. Supabase stays the documented fallback, to be switched to only during slice 1 if Firebase proves unworkable.

### ADR-2: Rules-only server authority (no Cloud Functions)

**Choice**: Enforce every invariant with Firestore Security Rules using `get()`, `getAfter()`, `diff().affectedKeys()`, and `request.time`. No Cloud Functions.
**Alternatives considered**: Cloud Functions callable endpoints for transitions/claim (cleaner validation, but requires the **Blaze** plan, i.e. a billing account — the same blocker that ruled out Google Maps); custom server (hosting cost and time).
**Rationale**: Every required invariant is expressible in rules: transitions are single-document conditional updates, and the two-document claim/deliver/release pairs are made atomic and mutually dependent with `getAfter()`. Rules are deployable on Spark and testable offline in the emulator.
**Consequences**: (a) no server-side price recalculation — item prices in the order are client-supplied (ADR-10); (b) no remote push sender (ADR-12); (c) no Cloud Storage (ADR-11). Rules `get()` budget (10 document accesses per single-document request, 20 per transaction/batch [unverified]) is respected: the heaviest path (claim) uses ≤ 4 distinct documents.

### ADR-3: Single Gradle app module, screaming feature packages, pragmatic hexagonal layers

**Choice**: One `:app` module. Top-level packages per feature; inside each: `domain/` (pure Kotlin, no Android/Firebase imports), `application/` (use cases + port interfaces), `adapters/firestore/`, `adapters/ui/`, and `adapters/device/` where needed. `core/` holds cross-cutting code (money, clock, result types, DI, navigation root, theme).
**Alternatives considered**: Multi-module by feature/layer (`:feature:ordering:domain`, ...) — stronger isolation but costs build config, slower iteration, and ceremony a solo two-month project cannot afford; flat layer-first packages (`ui/`, `data/`, `domain/`) — hides the business capabilities.
**Rationale**: Screaming packages map 1:1 to the six capabilities and slices. Domain purity is protected by convention plus a cheap guard: a JVM unit test (`DomainPurityTest`) that scans `**/domain/**` sources for `import android.` / `import com.google.firebase.` and fails if found.
**Pragmatism rule**: a use case class exists only when there is logic (policy checks, orchestration, multiple ports). Trivial reads (e.g. "observe my orders") may be called from the ViewModel directly through the repository port. Ports are always interfaces so ViewModels and use cases are testable with fakes.

### ADR-4: Hilt for DI

**Choice**: Hilt with KSP; `@HiltViewModel` + `hiltViewModel()` in container composables; Firebase singletons provided from `core/di/FirebaseModule.kt`; ports bound to Firestore adapters in per-feature `di` modules.
**Alternatives considered**: Koin (lighter, but errors surface at runtime); manual `AppContainer` (boilerplate grows with six features).
**Rationale**: Compile-time graph validation, first-class ViewModel/Navigation integration, and `@TestInstallIn` for swapping adapters in instrumented tests.

### ADR-5: Role-scoped navigation behind a session gate

**Choice**: `RootNavHost` observes `SessionState` and routes to one of: `SignedOut` graph (login/register), `Gate` screens (`PendingApproval`, `Suspended`, `ProfileIncomplete`), or a role graph (`CustomerGraph`, `MerchantGraph`, `CourierGraph`, `AdminGraph`). Type-safe `@Serializable` routes (Navigation Compose ≥ 2.8).
**Alternatives considered**: Four activities (duplicated plumbing); Navigation 3 (newer API, less migration material — not worth the risk on a fixed deadline).
**Rationale**: Role and status can change while the app is open (Admin suspends a courier); a single reactive gate re-routes instantly and keeps each role graph unaware of the others.

### ADR-6: Role and account status in `users/{uid}`, not custom claims

**Choice**: `users/{uid}.role` and `.status` are the source of truth; rules read them with `get()`.
**Alternatives considered**: Firebase Auth custom claims (cheaper rule evaluation, but can only be set with the Admin SDK — i.e. a server/Cloud Function for self-registration and Admin approval).
**Rationale**: Works on Spark with client-only writes; Admin approval is a plain rules-guarded document update. Cost: one extra document read per rule evaluation, negligible at demo scale.

### ADR-7: Race-safe claim = Firestore transaction + `couriers/{uid}.activeOrderId` + mutually dependent rules

**Choice**: The claim is one client transaction that writes **both** `orders/{orderId}` (`ready → claimed`, `courierId = uid`) and `couriers/{uid}` (`activeOrderId = orderId`). Rules on each document require the other document's post-write state via `getAfter()`, so neither write can be committed alone. Pre-state conditions (`status == 'ready'`, `courierId == null`, `activeOrderId == null`, courier online and active) are checked against `resource`/`get()`.
**Alternatives considered**: Order-only update checking "courier has no active order" with a query (rules cannot run queries); counter documents; Cloud Function claim (Blaze).
**Rationale**: Firestore serializes commits on a document. Two couriers racing on one order both touch `orders/{orderId}`: the loser's transaction is aborted and retried by the SDK, re-reads `status == 'claimed'`, and fails with `AlreadyClaimed`; a malicious client that skips the transaction is still denied by rules because `resource.data.status != 'ready'` at commit. One courier racing on two orders touches `couriers/{uid}` twice: the second commit sees `activeOrderId != null` and is denied. The dedicated concurrency tests (Testing Strategy) prove both properties against the emulator.
**Release paths**: deliver (`picked_up → delivered` + `activeOrderId = null`) and Admin release (`claimed → ready`, `courierId = null` + `activeOrderId = null`) use the same paired-write pattern.

### ADR-8: Live location in `liveLocations/{orderId}`, throttled client-side and floored server-side

**Choice**: One document per active order (not on the order document, not per courier), overwritten in place. Client throttle: publish at most every **10 s** and only if moved ≥ **10 m** (plus a 60 s heartbeat when stationary). Server floor: rules reject an update less than **5 s** after the previous `updatedAt` (`request.time` based). Publishing runs in a `location` foreground service from `claimed` until `delivered`; rules deny writes once the order is not `claimed`/`picked_up`.
**Alternatives considered**: Fields on `orders/{id}` (every GPS tick would fan out to merchant/admin order lists — read amplification); `couriers/{uid}` location (exposes position outside deliveries and complicates who-can-read); Realtime Database (second datastore, extra rules language).
**Rationale**: Isolates high-frequency writes to the only readers who care (the order's customer and Admin), and the rules floor protects quotas even from a buggy client. Quota estimate below.

### ADR-9: Money as `Long` centavos (NIO minor units)

**Choice**: All amounts are integers in centavos (`C$ 1.00 = 100`), Kotlin `@JvmInline value class Money(val centavos: Long)`, Firestore fields suffixed `Cents` stored as integers. Formatting `C$ 120.00` only in the UI layer.
**Alternatives considered**: `Double` córdobas (rounding errors); whole córdobas `Int` (simpler but breaks if a merchant prices with centavos).
**Rationale**: Exact arithmetic, and rules can verify `totalCents == subtotalCents + deliveryFeeCents` with integer math.

### ADR-10: Fee snapshot enforced by rules; item prices trusted with merchant review

**Choice**: At order creation, rules require `deliveryFeeCents == get(settings/app).deliveryFeeCents`, `totalCents == subtotalCents + deliveryFeeCents`, `subtotalCents > 0`, `1 ≤ items.size() ≤ 30`, `paymentMethod == 'cash'`, merchant `status == 'active'` and `isOpen == true`. The fee is thereby snapshotted into the immutable order; later fee changes never affect existing orders. Item unit prices and the subtotal are computed client-side (`CheckoutCalculator`) and are not re-verified per item.
**Alternatives considered**: Unrolled per-item rule checks against product documents (rules have no loops; would consume the `get()` budget and cap items very low); Cloud Function pricing (Blaze).
**Rationale**: Cash-on-delivery plus a mandatory merchant `accept` step means a tampered price is visible to the merchant (items and prices are shown on the accept screen) before any money changes hands. Accepted residual risk, documented.

### ADR-11: Product photos as compressed bytes in Firestore

**Choice**: The app resizes a picked photo to max 640 px and JPEG quality ~70, and stores it in `merchants/{mid}/productPhotos/{pid}` as a Firestore `Bytes` field; rules cap it at 300 KB. Products carry `photoVersion` so Coil cache keys change on replacement. The merchant profile photo follows the same pattern, stored at `merchants/{mid}/productPhotos/profile` (a fixed id that cannot collide with generated product ids) with `merchants/{mid}.photoVersion` bumped on replacement.
**Alternatives considered**: Cloud Storage for Firebase (new default buckets require the Blaze plan since late 2024 [unverified — confirm on the FAQ linked above]); external free image host (third-party account and API key in the app); URL-only field (poor UX).
**Rationale**: Zero extra services, same rules model. Cost: one read per photo fetch (mitigated by Coil disk cache + Firestore offline cache) and storage within the 1 GiB Spark quota (≈ 3,000+ photos).

### ADR-12: Notifications are local, driven by Firestore listeners (best effort)

**Choice**: A `NotificationPort` implemented by `LocalOrderNotifier` raises Android notifications when an observed order changes status while the app process is alive (foreground, or the courier's foreground service). No remote FCM push in the MVP. `users/{uid}.fcmToken` is reserved but unused.
**Alternatives considered**: FCM remote push (sending requires a trusted server with service-account credentials — Cloud Functions on Blaze or a hosted server); FCM sent from the client (would ship credentials in the APK — rejected).
**Rationale**: The proposal requires best-effort notifications only, with listeners as the source of truth. This satisfies the demo on foreground devices. **Open question Q1** asks whether this interpretation is acceptable; the port makes adding FCM later a one-adapter change if Blaze becomes available.

### ADR-13: OpenFreeMap vector tiles, OSM raster as fallback

**Choice**: MapLibre style URL from OpenFreeMap; attribution shown on the map. Fallback style JSON pointing to `tile.openstreetmap.org` raster tiles with a descriptive User-Agent and attribution, no prefetching.
**Alternatives considered**: MapTiler/Stadia free tiers (API key and account); OSM public tiles as primary (the OSMF policy discourages app usage beyond light use).
**Rationale**: No key, no account, demo-scale traffic. Style URL lives in one constant (`tracking/adapters/ui/MapStyle.kt`) so switching is trivial.

### ADR-14: Shared order-transition contract fixture

**Choice**: `backend/contracts/order-transitions.json` lists every `(from, to, actor)` triple that is allowed. Kotlin unit tests (`OrderTransitionsContractTest`) assert the domain table equals the fixture; rules tests iterate the fixture to assert each allowed triple succeeds and every other `(from, to, actor)` combination is denied.
**Alternatives considered**: Maintaining the two implementations by hand (silent drift).
**Rationale**: The only practical way to keep Kotlin and rules in lockstep without code generation.

### ADR-15: Seed script with Admin SDK, emulator by default

**Choice**: `backend/scripts/seed.ts` (firebase-admin) creates fixed-UID Auth users and their documents idempotently. Default target is the emulator; production requires `--target=production --confirm` plus explicit service-account credentials.
**Rationale**: Unblocks slices 2–5 with pre-approved merchant/courier accounts before the slice 6 approval UI; also creates the only Admin account (Admin cannot self-register).

### ADR-16: Test pyramid and runners (Strict TDD enabled after scaffold)

**Choice**: JVM unit tests for domain/application/ViewModels (`./gradlew testDebugUnitTest`), rules tests against the Firestore emulator (`npm --prefix backend test`), and a small set of instrumented tests for Firestore adapters (`connectedDebugAndroidTest` under `emulators:exec`). Aggregate workspace command: `./gradlew verifyAll`.
**Rationale**: The critical invariants live in pure Kotlin and in rules — both testable fast and without a device. **Strict TDD is re-enabled** by this design; the config flag is flipped by the slice 1 scaffold task as soon as both runners exist and run green (see Migration / Rollout).

## Package and Repository Structure

```
otli-delivery-app/
├── settings.gradle.kts, build.gradle.kts, gradle/libs.versions.toml, gradlew(.bat)
├── app/
│   ├── build.gradle.kts                 # Hilt, Compose, Firebase BoM, MapLibre, verifyAll task wiring
│   ├── google-services.json             # committed; not a secret (rules are the security boundary)
│   └── src/
│       ├── main/java/com/otli/app/
│       │   ├── OtliApplication.kt, MainActivity.kt
│       │   ├── core/        money/ time/ result/ di/ navigation/ ui/theme/ ui/components/
│       │   ├── auth/        domain/ application/ adapters/{firestore,ui}/
│       │   ├── catalog/     domain/ application/ adapters/{firestore,ui,device(image)}/
│       │   ├── ordering/    domain/ application/ adapters/{firestore,ui,notification}/
│       │   ├── dispatch/    domain/ application/ adapters/{firestore,ui}/
│       │   ├── tracking/    domain/ application/ adapters/{firestore,device,service,ui}/
│       │   └── admin/       domain/ application/ adapters/{firestore,ui}/
│       ├── test/java/com/otli/app/...        # JVM unit tests, mirrors main
│       └── androidTest/java/com/otli/app/... # emulator-backed adapter tests
└── backend/
    ├── package.json, tsconfig.json, vitest.config.ts
    ├── firebase.json, .firebaserc            # emulators: auth 9099, firestore 8080, ui 4000
    ├── firestore.rules, firestore.indexes.json
    ├── contracts/order-transitions.json
    ├── scripts/seed.ts
    └── tests/rules/*.test.ts
```

Application id / root package: `com.otli.app`.

### Layer responsibilities

| Layer | Contains | May depend on |
|---|---|---|
| `domain` | Entities, value objects, enums, pure policies (`OrderTransitions`, `ClaimPolicy`, `Cart`, `CheckoutCalculator`, `LocationThrottle`, `RegistrationPolicy`) | Kotlin stdlib only |
| `application` | Use cases, port interfaces (`OrderRepository`, `DispatchRepository`, `LocationSource`, `NotificationPort`, `Clock`) | `domain`, coroutines |
| `adapters/firestore` | Port implementations, DTO ↔ domain mappers, transactions | `application`, Firebase SDK |
| `adapters/ui` | `XxxScreen` (container: `hiltViewModel()`, `collectAsStateWithLifecycle`), `XxxContent` (presentational, stateless, previewable), `XxxViewModel` (`StateFlow<XxxUiState>`, intent functions) | `application`, `domain`, Compose |
| `adapters/device`, `adapters/service` | Fused location, foreground service, image compression | `application`, Android SDK |

UI state convention: one immutable `data class XxxUiState` per screen, exposed as `StateFlow`; user intents are ViewModel functions; one-shot effects (navigate, snackbar) are modeled as state fields consumed and cleared by the container (no `SharedFlow` event buses).

## Firestore Data Model

All timestamps are server timestamps (`FieldValue.serverTimestamp()`), which rules check with `== request.time`.

| Path | Fields | Written by | Read by |
|---|---|---|---|
| `users/{uid}` | `role` (`customer`\|`merchant`\|`courier`\|`admin`), `status` (`active`\|`pending`\|`suspended`), `displayName`, `email`, `phone`, `createdAt`, `fcmToken?` (reserved) | self on create (role ≠ admin; status fixed by role), self for profile fields, Admin for `status` | self, Admin |
| `merchants/{uid}` | `name`, `description`, `phone`, `status` (mirror of user status for listing), `isOpen`, `location {lat,lng,reference}` (required), `photoVersion?` (int, 0/absent = none), `createdAt`, `updatedAt` | owner on create (`status='pending'`, `isOpen=false`; `name`, `phone` and `location` required), owner (not `status`), Admin (`status`) | any signed-in user |
| `merchants/{uid}/categories/{cid}` | `name`, `sortOrder` | active owner | signed-in |
| `merchants/{uid}/products/{pid}` | `categoryId`, `name`, `description`, `priceCents` (int > 0), `isAvailable`, `photoVersion` (int, 0 = none), `updatedAt` | active owner | signed-in |
| `merchants/{uid}/productPhotos/{pid}` | `jpeg` (Bytes ≤ 300 KB), `version` | active owner | signed-in |
| `couriers/{uid}` | `isOnline`, `activeOrderId` (string\|null), `updatedAt` | self (availability, paired claim/deliver), Admin (paired release) | self, Admin |
| `orders/{orderId}` | `customerId`, `customerName`, `customerPhone`, `merchantId`, `merchantName`, `pickup {lat,lng,reference}`, `dropoff {lat,lng,reference}`, `items [{productId,name,unitPriceCents,quantity}]`, `subtotalCents`, `deliveryFeeCents`, `totalCents`, `paymentMethod='cash'`, `status`, `courierId` (null until claimed), `rejectReason?`, `cancelReason?`, `cancelledBy?` (`customer`\|`admin`), `createdAt`, `acceptedAt?`, `preparingAt?`, `readyAt?`, `claimedAt?`, `pickedUpAt?`, `deliveredAt?`, `rejectedAt?`, `cancelledAt?`, `updatedAt` | see transition table | customer owner, merchant owner, assigned courier, active online-or-not courier when `status=='ready'` (pool), Admin |
| `liveLocations/{orderId}` | `courierId`, `lat`, `lng`, `accuracyM`, `updatedAt` | assigned courier while order `claimed`/`picked_up` | order's customer, assigned courier, Admin |
| `settings/app` | `deliveryFeeCents`, `updatedAt`, `updatedBy` | Admin | signed-in |

Denormalization choices: `merchantName`, `customerName/Phone`, `pickup`, and embedded `items` are copied into the order at creation so every order screen is a single-document read and the order is immutable history. `merchants.status` mirrors `users.status` so customers can list active merchants with one query; Admin updates both in one batch.

### Composite indexes (`backend/firestore.indexes.json`)

| Collection | Fields | Query |
|---|---|---|
| `orders` | `customerId` ASC, `createdAt` DESC | customer order history |
| `orders` | `merchantId` ASC, `createdAt` DESC | merchant incoming/active orders |
| `orders` | `status` ASC, `readyAt` ASC | courier pool (`status == 'ready'`, oldest first) |
| `orders` | `status` ASC, `createdAt` ASC | Admin stuck view (`status in [placed, accepted, preparing, ready]`) |
| `merchants` | `status` ASC, `name` ASC | customer merchant list |
| `users` | `status` ASC, `createdAt` ASC | Admin pending approvals |

Admin "all orders" uses the automatic single-field index on `createdAt` DESC with `limit(50)`.

## Order State Machine and Authorization

| From | To | Actor | Extra conditions (rules) |
|---|---|---|---|
| — | `placed` | active customer (owner) | fee snapshot, totals, merchant active + open, `courierId == null` (ADR-10) |
| `placed` | `accepted` | active merchant owner | only `status, acceptedAt, updatedAt` change |
| `placed` | `rejected` | active merchant owner | `rejectReason` non-empty string ≤ 200 chars |
| `placed` | `cancelled` | customer owner | `cancelledBy == 'customer'` |
| `accepted` | `preparing` | active merchant owner | |
| `preparing` | `ready` | active merchant owner | sets `readyAt` |
| `ready` | `claimed` | active courier | ADR-7 paired write |
| `claimed` | `picked_up` | assigned active courier | |
| `picked_up` | `delivered` | assigned active courier | paired write clearing `activeOrderId` |
| `claimed` | `ready` | Admin (release) | `courierId → null`, paired write clearing courier `activeOrderId` |
| `placed`\|`accepted`\|`preparing`\|`ready` | `cancelled` | Admin | `cancelledBy == 'admin'`, `cancelReason` non-empty |

Terminal: `delivered`, `rejected`, `cancelled`. A claimed order must be released before Admin can cancel it. Suspended actors cannot perform transitions; if a courier is suspended mid-delivery, Admin releases the claim.

### Rules structure (excerpt of the load-bearing parts)

```
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    function signedIn() { return request.auth != null; }
    function doc(path) { return get(/databases/$(database)/documents/$(path)); }
    function me() { return doc('users/' + request.auth.uid).data; }
    function isActive(role) { return signedIn() && me().role == role && me().status == 'active'; }
    function isAdmin() { return isActive('admin'); }
    function changed() { return request.resource.data.diff(resource.data).affectedKeys(); }
    function moves(from, to) { return resource.data.status == from && request.resource.data.status == to; }
    function courierPath(uid) { return /databases/$(database)/documents/couriers/$(uid); }
    function orderPath(id) { return /databases/$(database)/documents/orders/$(id); }

    match /orders/{orderId} {
      function claim() {
        let c = courierPath(request.auth.uid);
        return isActive('courier') && moves('ready', 'claimed')
          && resource.data.courierId == null
          && request.resource.data.courierId == request.auth.uid
          && request.resource.data.claimedAt == request.time
          && changed().hasOnly(['status', 'courierId', 'claimedAt', 'updatedAt'])
          && get(c).data.isOnline == true
          && get(c).data.activeOrderId == null
          && getAfter(c).data.activeOrderId == orderId;
      }
      function deliver() {
        let c = courierPath(request.auth.uid);
        return isActive('courier') && moves('picked_up', 'delivered')
          && resource.data.courierId == request.auth.uid
          && changed().hasOnly(['status', 'deliveredAt', 'updatedAt'])
          && getAfter(c).data.activeOrderId == null;
      }
      function release() {
        return isAdmin() && moves('claimed', 'ready')
          && request.resource.data.courierId == null
          && changed().hasOnly(['status', 'courierId', 'updatedAt'])
          && getAfter(courierPath(resource.data.courierId)).data.activeOrderId == null;
      }
      // create(), merchantStep(), customerCancel(), pickUp(), adminCancel() follow the table above
      allow update: if claim() || deliver() || release() /* || ... */;
    }

    match /couriers/{uid} {
      function isSelf() { return request.auth.uid == uid && isActive('courier'); }
      function pairedClaim() {
        let id = request.resource.data.activeOrderId;
        return isSelf() && resource.data.activeOrderId == null
          && changed().hasOnly(['activeOrderId', 'updatedAt'])
          && get(orderPath(id)).data.status == 'ready'
          && getAfter(orderPath(id)).data.status == 'claimed'
          && getAfter(orderPath(id)).data.courierId == uid;
      }
      function pairedDeliver() {
        return isSelf() && request.resource.data.activeOrderId == null
          && changed().hasOnly(['activeOrderId', 'updatedAt'])
          && getAfter(orderPath(resource.data.activeOrderId)).data.status == 'delivered';
      }
      function pairedRelease() {
        return isAdmin() && request.resource.data.activeOrderId == null
          && changed().hasOnly(['activeOrderId', 'updatedAt'])
          && getAfter(orderPath(resource.data.activeOrderId)).data.status == 'ready';
      }
      function availability() {
        return isSelf() && changed().hasOnly(['isOnline', 'updatedAt'])
          && (request.resource.data.isOnline == true || resource.data.activeOrderId == null);
      }
      allow update: if availability() || pairedClaim() || pairedDeliver() || pairedRelease();
    }

    match /liveLocations/{orderId} {
      function order() { return get(orderPath(orderId)).data; }
      allow read: if signedIn() && (order().customerId == request.auth.uid
        || order().courierId == request.auth.uid || isAdmin());
      allow create, update: if isActive('courier')
        && order().courierId == request.auth.uid
        && order().status in ['claimed', 'picked_up']
        && request.resource.data.courierId == request.auth.uid
        && request.resource.data.updatedAt == request.time
        && request.resource.data.keys().hasOnly(['courierId', 'lat', 'lng', 'accuracyM', 'updatedAt'])
        && (resource == null || request.time > resource.data.updatedAt + duration.value(5, 's'));
    }
  }
}
```

The final rules file is authored test-first in slices 1–6; the excerpt fixes the pattern, not every line.

## Data Flow

### Order lifecycle (sequence)

```
Customer app        Firestore (rules)          Merchant app         Courier app(s)
    | create order{placed, fee snapshot}           |                      |
    |------------------->| rules: create() ok      |                      |
    |                    |--- listener (merchantId)->| shows new order    |
    |                    |<-- update placed→accepted-|                    |
    |<-- listener -------|                          |                     |
    |                    |<-- accepted→preparing ----|                    |
    |                    |<-- preparing→ready -------|                    |
    |                    |--- listener (status==ready) ---------------->  | pool shows order
    |                    |<====== transaction: claim (order + courier) ===|
    |<-- claimed --------|--- claimed ------------->|                     |
    |                    |<-- liveLocations/{id} every >=10 s ------------| (foreground service)
    |<-- location -------|                          |                     |
    |                    |<-- claimed→picked_up ----------------------------|
    |                    |<====== transaction: deliver (order + courier) ==|
    |<-- delivered ------|--- delivered ----------->|                     | service stops, pool visible
```

### Race-safe claim (sequence)

```
Courier A                 Firestore                        Courier B
  | tx.get(order), tx.get(courierA)  |  tx.get(order), tx.get(courierB) |
  |  ClaimPolicy ok (ready, null)    |   ClaimPolicy ok (ready, null)   |
  | commit {order: claimed/A,        |                                  |
  |         courierA.active=id} ---->| rules ok -> COMMITTED            |
  |                                  |<---- commit {order: claimed/B, courierB.active=id}
  |                                  | order version changed -> ABORTED (SDK retries)
  |                                  |<---- retry: tx.get(order) -> status=claimed
  |                                  | ClaimPolicy -> AlreadyClaimed -> no write
  | UI: go to active delivery        |          UI: "Order taken by another courier"
```

A client that bypasses the transaction and writes directly is denied by rules
(`resource.data.status != 'ready'`), so correctness never depends on client code.

### Session gate

```
FirebaseAuth.authState ──┐
                         ├─> ObserveSessionUseCase ─> SessionState ─> RootNavHost
users/{uid} listener ────┘    (SignedOut | Loading | ProfileIncomplete |
                               Pending | Suspended | Active(role))
```

Registration writes `users/{uid}` (+ `merchants/{uid}` or `couriers/{uid}`) in one batch right after Auth sign-up. Merchant self-registration collects store name, contact phone (8 digits, optional +505) and a map pin (accepted change 2026-09-30); description, photo and hours are edited later in the merchant profile. If that batch fails, the next login lands on `ProfileIncomplete`, which retries the batch.

### Live tracking and quota estimate

Publishing: `DeliveryTrackingService` (foreground, `foregroundServiceType="location"`) starts when the courier's `activeOrderId` becomes non-null and stops when it becomes null or the order leaves `claimed`/`picked_up`. `LocationThrottle` (pure domain) decides whether a fix is published: `elapsed ≥ 10 s && distance ≥ 10 m`, or `elapsed ≥ 60 s` (heartbeat).

| Item | Estimate |
|---|---|
| Worst case writes per delivery (30 min, always moving, 10 s) | ~180 writes |
| Reads per delivery (customer listener + occasional Admin) | ~180–360 reads |
| Spark daily free quota [unverified — confirm on pricing page] | 20,000 writes, 50,000 reads, 20,000 deletes, 1 GiB stored |
| Deliveries/day before tracking alone exhausts writes | ~100 (demo needs < 20) |

Permissions: `ACCESS_FINE_LOCATION`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_LOCATION` (Android 14+), `POST_NOTIFICATIONS` (Android 13+). Background location permission is not required because the service is started while the app is visible.

## Interfaces / Contracts

```kotlin
// core/money/Money.kt
@JvmInline value class Money(val centavos: Long) {
    init { require(centavos >= 0) }
    operator fun plus(other: Money) = Money(centavos + other.centavos)
    operator fun times(qty: Int) = Money(centavos * qty)
}

// ordering/domain/OrderStatus.kt
enum class OrderStatus { PLACED, ACCEPTED, PREPARING, READY, CLAIMED, PICKED_UP, DELIVERED, REJECTED, CANCELLED }
enum class Actor { CUSTOMER, MERCHANT, COURIER, ADMIN }

// ordering/domain/OrderTransitions.kt
object OrderTransitions {
    fun isAllowed(from: OrderStatus, to: OrderStatus, actor: Actor): Boolean
    val allowed: Set<Triple<OrderStatus, OrderStatus, Actor>> // asserted against backend/contracts/order-transitions.json
}

// ordering/domain/Cart.kt  — single-merchant invariant
sealed interface AddResult { data class Added(val cart: Cart) : AddResult; data class ConflictingMerchant(val current: String) : AddResult }

// ordering/domain/CheckoutCalculator.kt
data class Totals(val subtotal: Money, val fee: Money, val total: Money)

// dispatch/domain/ClaimPolicy.kt
sealed interface ClaimDecision { data object Allowed : ClaimDecision; data class Denied(val reason: ClaimDenial) : ClaimDecision }
enum class ClaimDenial { NOT_READY, ALREADY_CLAIMED, COURIER_BUSY, COURIER_OFFLINE, COURIER_NOT_ACTIVE }

// dispatch/application/DispatchRepository.kt  (port)
interface DispatchRepository {
    fun observePool(): Flow<List<PoolOrder>>
    suspend fun claim(orderId: String, courierId: String): ClaimDecision   // runs a Firestore transaction
    suspend fun markPickedUp(orderId: String, courierId: String)
    suspend fun markDelivered(orderId: String, courierId: String)          // paired transaction
    suspend fun setOnline(courierId: String, online: Boolean)
}

// ordering/application/OrderRepository.kt  (port)
interface OrderRepository {
    suspend fun place(draft: OrderDraft): String
    fun observe(orderId: String): Flow<Order?>
    fun observeForCustomer(customerId: String): Flow<List<Order>>
    fun observeForMerchant(merchantId: String): Flow<List<Order>>
    suspend fun transition(orderId: String, to: OrderStatus, actor: Actor, reason: String? = null)
}

// tracking/application ports
interface LocationSource { fun fixes(): Flow<GeoFix> }
interface LocationRepository { suspend fun publish(orderId: String, courierId: String, fix: GeoFix); fun observe(orderId: String): Flow<GeoFix?> }

// admin/application/AdminRepository.kt  (port)
interface AdminRepository {
    suspend fun setAccountStatus(uid: String, role: Role, status: AccountStatus) // batch users + merchants mirror
    suspend fun setDeliveryFee(fee: Money)
    suspend fun releaseClaim(orderId: String)                                   // paired transaction
    suspend fun cancelOrder(orderId: String, reason: String)
    fun observeStuckOrders(): Flow<List<Order>>
    fun observePendingAccounts(): Flow<List<UserAccount>>
}

// core/notification
interface NotificationPort { fun notifyOrderStatus(orderId: String, status: OrderStatus) }
```

Transition contract fixture shape (`backend/contracts/order-transitions.json`):

```json
{ "allowed": [
  { "from": "placed", "to": "accepted", "actor": "merchant" },
  { "from": "ready",  "to": "claimed",  "actor": "courier" },
  { "from": "claimed","to": "ready",    "actor": "admin" }
] }
```

(The full list mirrors the transition table above.)

## Environment Wiring

- `debug` build: connects to the emulators when `otli.useEmulator=true` in `local.properties` (default true); host from `otli.emulatorHost` (default `10.0.2.2` for the Android emulator; LAN IP for physical devices). Wiring in `core/di/FirebaseModule.kt` via `useEmulator(host, port)` before first use.
- `release` build (and `debug` with `otli.useEmulator=false`): live Spark project, used for the real-device demo.
- Emulator project id: `demo-otli` (the `demo-` prefix needs no real project) for tests; `.firebaserc` default alias points to the real project id once created.
- Emulator state persistence for manual work: `firebase emulators:start --import=./emulator-data --export-on-exit` (directory gitignored).

## Seed Script

`backend/scripts/seed.ts`, run with `npm --prefix backend run seed` while emulators are running (`npm --prefix backend run emulators`).

- Targets the emulator by setting `FIRESTORE_EMULATOR_HOST=127.0.0.1:8080` and `FIREBASE_AUTH_EMULATOR_HOST=127.0.0.1:9099`, project `demo-otli`.
- Idempotent: fixed UIDs (`seed-admin`, `seed-merchant-1`, `seed-merchant-2`, `seed-courier-1`, `seed-courier-2`, `seed-customer-1`), `set(..., { merge: true })`, Auth users created or updated.
- Creates: Admin; two **active** merchants (one open, with location in Nagarote, 2 categories and ~5 products each, no photos); two **active** couriers (`isOnline=false`, `activeOrderId=null`); one active customer; one pending merchant and one pending courier (for slice 6 approval demos); `settings/app.deliveryFeeCents` (e.g. `3000` = C$ 30.00).
- Emails `*@otli.test`; emulator password `otli-demo-123`. Production target requires `--target=production --confirm`, `GOOGLE_APPLICATION_CREDENTIALS`, and a password from `OTLI_SEED_PASSWORD`; running it (and `firebase deploy`) is a remote operation that needs the user's explicit authorization each time.

## Testing Strategy

### Runners and exact commands

| Scope | Command (from repo root) | Needs |
|---|---|---|
| Android JVM unit tests | `./gradlew testDebugUnitTest` (Windows: `.\gradlew.bat testDebugUnitTest`) | JDK 17 |
| Security rules + concurrency tests | `npm --prefix backend test` → `firebase emulators:exec --only firestore,auth --project demo-otli "vitest run"` | Node ≥ 22, JDK 21, firebase-tools |
| Firestore adapter instrumented tests | `npm --prefix backend run test:android` → `firebase emulators:exec --only firestore,auth --project demo-otli "cd .. && ./gradlew connectedDebugAndroidTest"` | running Android emulator or device |
| Workspace aggregate (Strict TDD gate) | `./gradlew verifyAll` = `testDebugUnitTest` + `lintDebug` + Exec task running `npm --prefix backend test` | both of the above |
| Lint / typecheck | `./gradlew lintDebug`; `npm --prefix backend run typecheck` (`tsc --noEmit`) | |

Strict TDD loop for apply: Kotlin work → `./gradlew testDebugUnitTest --tests "<class>"` for RED/GREEN, then `./gradlew verifyAll` at task close; rules work → `npm --prefix backend test -- <file>` for RED/GREEN.

### What is tested where

| Layer | What to test | Approach |
|---|---|---|
| Unit (JVM) | `Money`, `Cart` single-merchant rule, `CheckoutCalculator`, `OrderTransitions` (+ contract fixture parity), `ClaimPolicy`, `LocationThrottle`, `RegistrationPolicy`, session mapping, domain purity guard | Plain JUnit, table-driven |
| Unit (JVM) | Use cases (`PlaceOrder`, `ClaimOrder`, `AdvanceOrder`, `ReleaseClaim`, `ApproveAccount`, `UpdateFee`) | Fake ports (in-memory), `runTest` |
| Unit (JVM) | ViewModels (UiState transitions, error mapping e.g. `AlreadyClaimed` → message) | Fakes + `kotlinx-coroutines-test` + Turbine, `MainDispatcherRule` |
| Unit (JVM, optional) | Presentational composables with non-trivial branching (pool hidden when active order, gate screens) | Robolectric + Compose UI test, only where cheap |
| Rules (emulator) | Every collection's read/write matrix per role and status; every allowed transition from the fixture succeeds and all others are denied; fee snapshot and totals; field-change restrictions (`hasOnly`); pending/suspended denial; location write floor and post-delivery denial; self-registration cannot create Admin or active merchant/courier | `@firebase/rules-unit-testing`: `authenticatedContext(uid)`, `withSecurityRulesDisabled` for fixtures, `assertSucceeds`/`assertFails`, `clearFirestore()` per test |
| Rules (emulator) — **mandatory concurrency** | (1) N = 2 and N = 10 couriers claim the same `ready` order concurrently with real transactions (`Promise.allSettled`): exactly one fulfilled; order `courierId` equals the winner; only the winner's `activeOrderId` is set. (2) One courier claims two different `ready` orders concurrently: exactly one succeeds; the other order stays `ready`/`courierId == null`. (3) Direct non-transactional write to a `claimed` order is denied. (4) Order-only or courier-only half of a claim is denied. | `backend/tests/rules/claim-concurrency.test.ts`; repeated 20 iterations in a loop to surface flakiness |
| Integration (instrumented) | `FirestoreDispatchRepository.claim` concurrent from two `FirebaseApp` instances signed in as different couriers; `FirestoreOrderRepository` place/observe round trip; mapper correctness | `connectedDebugAndroidTest` against emulators, Hilt `@TestInstallIn` |
| E2E (manual demo script) | Full happy path on two or more devices, claim race by simultaneous tap, Admin release/cancel | Checklist in `docs/demo-script.md` (slice 6) |

Coverage threshold stays `0` (not a gate); the concurrency and transition-matrix tests are the correctness gates.

## File Changes

Greenfield; all files are **Create**. Representative, not exhaustive — `sdd-tasks` decomposes per slice.

| File | Action | Description |
|---|---|---|
| `settings.gradle.kts`, `build.gradle.kts`, `gradle/libs.versions.toml`, `gradlew*` | Create | Gradle project, pinned versions, `verifyAll` aggregate task |
| `app/build.gradle.kts` | Create | SDK levels, Compose, Hilt/KSP, Firebase BoM, MapLibre, test deps, emulator `BuildConfig` fields |
| `app/src/main/AndroidManifest.xml` | Create | Permissions, `DeliveryTrackingService` with `foregroundServiceType="location"` |
| `app/src/main/java/com/otli/app/core/**` | Create | `Money`, `Clock`, `DomainError`, `FirebaseModule`, `RootNavHost`, theme, shared components |
| `app/src/main/java/com/otli/app/auth/**` | Create | Session gate, register/login, role routing (slice 1) |
| `app/src/main/java/com/otli/app/catalog/**` | Create | Merchant profile/catalog management, customer browse, photo compression (slice 2) |
| `app/src/main/java/com/otli/app/ordering/**` | Create | Cart, checkout with map pin, order placement, merchant order board, customer order tracking, local notifications (slice 3) |
| `app/src/main/java/com/otli/app/dispatch/**` | Create | Availability, pool, claim transaction, pickup/deliver (slice 4) |
| `app/src/main/java/com/otli/app/tracking/**` | Create | Throttle, fused location, foreground service, live map (slice 5) |
| `app/src/main/java/com/otli/app/admin/**` | Create | Fee, approvals/suspensions, stuck orders, release, cancel, order list (slice 6) |
| `app/src/test/**`, `app/src/androidTest/**` | Create | Unit and instrumented tests mirroring main |
| `backend/package.json`, `tsconfig.json`, `vitest.config.ts` | Create | Scripts: `test`, `test:android`, `emulators`, `seed`, `typecheck` |
| `backend/firebase.json`, `backend/.firebaserc` | Create | Emulator ports, rules/index paths |
| `backend/firestore.rules` | Create | Full rules (grown per slice) |
| `backend/firestore.indexes.json` | Create | Composite indexes above |
| `backend/contracts/order-transitions.json` | Create | Shared transition contract (ADR-14) |
| `backend/scripts/seed.ts` | Create | Seed accounts and demo data (ADR-15) |
| `backend/tests/rules/*.test.ts` | Create | Rules matrix and concurrency tests |
| `.gitignore` | Create | Gradle/IDE outputs, `local.properties`, `backend/node_modules`, `backend/emulator-data`, service-account keys |
| `README.md` | Create | Setup (JDK 17/21, Node, emulators, seed), commands |
| `openspec/config.yaml` | Modify | Stack context and testing commands (done in this phase); `strict_tdd: true` flipped by the scaffold task |

## Threat Matrix

N/A — no routing, shell, subprocess, VCS/PR automation, executable-file classification, or process-integration boundary in the product. (The only process integration is the developer-side `verifyAll` Exec task invoking `npm`, with fixed arguments and no user input.) Application security is handled by the Security Rules strategy and its rules test matrix above.

## Migration / Rollout

No data migration (greenfield). Rollout per slice:

1. **Slice 1 scaffold task**: create Gradle project and `backend/` package with one passing test each; confirm pinned versions against official pages; run `./gradlew testDebugUnitTest`, `npm --prefix backend test`, and `./gradlew verifyAll` green; then set `strict_tdd: true`, `testing.status: available`, and `rules.apply.tdd: true` in `openspec/config.yaml` and update Engram `sdd/otli-delivery-app/testing-capabilities`. Every later task follows Strict TDD.
2. Create the Firebase project (Spark), register the Android app, commit `google-services.json` and only then apply the `google-services` Gradle plugin (scaffold task 0.1 deliberately leaves it off; before that, debug builds reach the emulators through explicit `FirebaseOptions` for `demo-otli`, wired in task 1.3.1). Deploying rules/indexes (`firebase deploy --only firestore:rules,firestore:indexes`) is a remote operation requiring user authorization; it happens at the end of each slice that changes rules.
3. Supabase fallback decision point: end of slice 1 at the latest.

## Open Questions

- [x] **Q1 (product) — RESOLVED 2026-09-29: user chose the free Spark plan (no billing account); local notifications per ADR-12.**: Remote push (FCM) requires a trusted sender, i.e. Cloud Functions on the Blaze plan (billing account). Is "local notifications while the app is running" acceptable as the MVP's best-effort push, or can the user enable Blaze (with a budget alert) to add an FCM sender? Default in this design: local notifications (ADR-12).
- [x] **Q2 (product) — RESOLVED 2026-09-29: Spark plan; compressed photos in Firestore per ADR-11.**: Same billing question drives product photos (Cloud Storage now needs Blaze [unverified]). Default: compressed photos stored in Firestore (ADR-11). If Blaze is enabled for Q1, switch photos to Cloud Storage.
- [ ] **Q3 (technical, validated by tests)**: Rules evaluation plus `getAfter()` pairing is expected to be serializable with concurrent commits; the mandatory concurrency tests are the proof. If the emulator shows any double-claim, fall back to making the claim contend on a single document (e.g. `orders/{id}` only, with `activeOrderId` enforced via a per-courier lock document written in the same transaction).
- [x] **Q4 (technical) — PARTIALLY RESOLVED 2026-09-29 (task 0.1)**: All software versions are verified and pinned (see `gradle/libs.versions.toml`, `backend/package.json`). Third-party pricing, quota and tile-policy claims remain [unverified].
