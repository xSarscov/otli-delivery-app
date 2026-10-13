# Proposal: Otli MVP — Four-Sided Delivery Marketplace for Nagarote

## Intent

Nagarote, León (Nicaragua) has no local delivery platform. Regional apps do not
serve small towns that rely on reference-based addressing ("from the central
park, 2 blocks south") and cash-only commerce. Otli is an academic MVP that
proves a four-sided marketplace (Customer, Merchant, Courier, Admin) can run in
a single Android app with real-time, multi-device coordination, built by a solo
developer in under 2 months.

Success means an end-to-end demo on real devices: a customer places an order, a
merchant accepts and prepares it, exactly one courier claims it from an open
pool, the customer watches the courier move on a map, and the order is
delivered and paid in cash — while an Admin controls the fee, account
onboarding, and stuck orders.

## Scope

### In Scope
- One Android app; the role (Customer, Merchant, Courier, Admin) is resolved at login.
- Onboarding: customers self-register as `active`; merchants and couriers
  self-register as `pending` and require Admin approval; Admin can suspend
  merchant and courier accounts.
- Merchant catalog: categories, products (name, price in NIO, photo, availability),
  store open/closed toggle.
- Customer browsing, single-merchant cart (adding a product from another merchant
  prompts to clear the cart), and checkout with a map pin plus textual reference.
- Order lifecycle visible in real time to all relevant actors across devices:
  `placed → accepted → preparing → ready → claimed → picked_up → delivered`,
  with `rejected` (merchant, from `placed`, with reason) and `cancelled`.
- Customer cancellation only while the order is `placed`.
- Open courier pool of `ready` orders with a race-safe, first-claim-wins claim;
  one active order per courier (the pool is hidden while the courier has one).
- Courier online/offline availability.
- Live courier location tracking on a map during an active delivery (throttled).
- Cash on delivery only; all amounts in NIO.
- Flat citywide delivery fee configured by Admin.
- Admin oversight: release an abandoned `claimed` order back to `ready`; a
  "delayed/stuck orders" view of unclaimed orders, with the ability to cancel them;
  basic order list for support.
- Best-effort push notifications for order status changes.

### Out of Scope
- Online/card payments, wallets, or any payment gateway.
- iOS, cross-platform builds, or a web admin console.
- Multi-city or multi-zone support; distance-based fees.
- Promotions, coupons, loyalty, ratings/reviews, in-app chat.
- Order editing after placement; multi-merchant carts.
- Automatic timeouts, auto-reassignment, or routing optimization.
- Reverse geocoding and turn-by-turn navigation.
- Analytics/BI dashboards; offline-first operation.
- Guaranteed push delivery (best effort is sufficient).

## Capabilities

`openspec/specs/` is empty; every capability is new.

### New Capabilities
- `auth-roles`: registration, login, role resolution, account status
  (`active`, `pending`, `suspended`) and role-scoped access.
- `merchant-catalog`: merchant profile, categories, products, availability and
  store open/closed state; customer-facing browsing.
- `ordering`: single-merchant cart, checkout (pin + textual reference, fee,
  cash), order placement, merchant accept/reject/prepare/ready, customer
  cancellation while `placed`, and the order state machine.
- `courier-dispatch`: courier availability, open pool of `ready` orders,
  race-safe claim, one-active-order rule, pickup and delivery transitions.
- `live-tracking`: throttled courier location publishing during an active
  delivery and real-time map display for the customer (and Admin).
- `admin`: delivery fee configuration, merchant/courier approval and
  suspension, stuck-orders view, release of abandoned claims, order cancellation
  and oversight.

### Modified Capabilities
- None.

## Approach

Build vertically, one demoable slice at a time, on a Backend-as-a-Service to
avoid writing and hosting a server.

**Proposed stack (to be confirmed in sdd-design):** Kotlin + Jetpack Compose,
Firebase Auth, Cloud Firestore (real-time listeners, transactions), Firebase
Cloud Messaging, and MapLibre Native with OpenStreetMap tiles. Rationale from
exploration: Firestore transactions implement the race-safe claim directly;
MapLibre/OSM avoids the Google Maps billing-account requirement; the Firebase
Emulator Suite allows local automated tests without consuming quotas. Supabase
remains the fallback (its free-tier auto-pause after one week of inactivity is
the deciding risk). Per `openspec/config.yaml`, the final stack, architecture,
security-rules strategy, test runner, and the re-enablement of Strict TDD are
decisions owned by sdd-design.

Key design constraints carried forward:
- The claim MUST be a single atomic conditional write (`status == ready` and no
  courier, and the courier has no active order) with a dedicated concurrency test.
- State transitions MUST be enforced server-side (security rules or equivalent),
  not only in the client.
- Live location writes MUST be throttled (order of 5–10 s) to protect write quotas.

### Planned delivery increments (auto-chain, chained PRs)

Each slice is a demoable increment and is expected to split into several
chained PRs under the 400-line review budget; exact slicing belongs to sdd-tasks.

| # | Slice | Demo outcome | Capabilities |
|---|-------|--------------|--------------|
| 1 | Auth and roles skeleton | Register/login, role routing, pending/suspended gating, empty home per role | `auth-roles` |
| 2 | Merchant catalog and customer browse | Merchant manages catalog; customer browses merchants and products | `merchant-catalog` |
| 3 | Cart, checkout, and order placement | Customer places a cash order; merchant accepts/rejects and progresses it to `ready` in real time; customer cancels while `placed` | `ordering` |
| 4 | Courier pool and race-safe claim | Couriers see the pool, one wins the claim, pickup and delivery complete the happy path | `courier-dispatch` |
| 5 | Live courier tracking | Customer sees the courier moving on a map during delivery | `live-tracking` |
| 6 | Admin settings and oversight | Fee configuration, approvals/suspensions, stuck orders, claim release, cancellation | `admin` |

Slice 6's approval capability is a prerequisite for pending merchants and
couriers in real use. Decision (user, 2026-09-29): a seed script creates
pre-approved merchant and courier test accounts to unblock slices 2–5; the
approval UI stays in slice 6.

## Affected Areas

| Area | Impact | Description |
|------|--------|-------------|
| Android app module (path defined in sdd-design) | New | Entire application: UI per role, domain, data layer |
| Backend project configuration (security rules, indexes) | New | Access control and server-side transition enforcement |
| `openspec/changes/otli-mvp/specs/*` | New | Six capability specs listed above |
| `openspec/config.yaml` | Modified (later) | Stack, test runner, and Strict TDD settings after sdd-design |

## Risks

| Risk | Likelihood | Mitigation |
|------|------------|------------|
| Full scope in < 2 months by a solo developer (phasing rejected; accepted by the user) | High | BaaS instead of a custom server; strict vertical-slice order so a demo exists at every checkpoint; aggressive out-of-scope list; no scope additions without trade-off |
| Race condition in courier claim (two winners or a courier with two orders) | Medium | Single atomic transaction; server-side rule enforcement; dedicated concurrent-claim test against the emulator |
| Free-tier quota exhaustion from live location writes | Medium | Throttle location updates; publish only during an active delivery; stop on delivery/offline |
| Client-only enforcement lets a role perform forbidden transitions | Medium | Security rules (or equivalent) validate role, ownership, and allowed transitions |
| No code reviewer (solo developer) | Medium | Re-enable Strict TDD once the runner is chosen; small chained PRs; native review where enabled |
| Push notifications unreliable on some devices | Medium | Real-time listeners are the source of truth; push is best effort only |
| Map tile provider limits or usage policy for OSM public tiles | Low | Low demo traffic; confirm tile-usage policy or choose a free-tier tile host in sdd-design |
| Third-party pricing figures in exploration are stale | Low | Re-verify against official pricing pages in sdd-design |

## Rollback Plan

This is a greenfield change with no existing users or data. Each slice ships as
chained PRs; any slice can be reverted by reverting its PR(s) without affecting
earlier slices. Backend configuration (security rules, indexes) is kept in the
repository and redeployed from the previous commit to roll back. If the
proposed BaaS proves unworkable during slice 1, switch to the documented
fallback (Supabase) before slice 2, when the cost of change is lowest.

## Dependencies

- A free-tier BaaS project (Firebase proposed) and its local emulator.
- Map tiles source (OSM-based) compatible with MapLibre Native.
- At least two Android devices or emulators to demonstrate multi-device real time
  and concurrent claims.
- Stack and test-runner decisions from sdd-design.

## Assumptions

- Reverse geocoding is not required; the pin plus textual reference is sufficient.
- The app assumes connectivity during use.
- A single flat fee applies to every order in Nagarote.
- Demo-scale traffic fits within free-tier quotas when location is throttled.

## Success Criteria (academic demo)

- [ ] Each of the four roles can log in on the same app and reaches its own home; pending and suspended accounts are blocked with a clear message.
- [ ] A customer places a cash order in NIO with pin + reference, and the order total equals item subtotal plus the Admin-configured fee.
- [ ] Order status changes appear on the other actors' devices without manual refresh.
- [ ] When two couriers claim the same `ready` order simultaneously, exactly one succeeds (verified by an automated concurrency test and a live demo).
- [ ] A courier with an active order cannot see or claim another order.
- [ ] The customer sees the courier's position update on the map during delivery.
- [ ] Admin approves a pending merchant and courier, suspends an account, changes the fee, releases an abandoned claim back to the pool, and cancels a stuck order.
- [ ] The customer can cancel only while the order is `placed`.
- [ ] Automated tests cover the order state machine and claim logic and run locally without production quotas.
