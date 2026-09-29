# Exploration: otli-mvp

## 1. Problem Statement, Goals, Non-Goals

### Problem
Nagarote, León (Nicaragua) has no local delivery platform. Existing regional
apps (PedidosYa-style) do not serve small towns with informal, reference-based
addressing ("del parque central 2 cuadras al sur") and cash-only commerce.
Otli is an academic MVP that proves the four-sided marketplace (customer,
merchant, courier, admin) can run on a single Android app with real-time,
multi-device coordination, for a solo developer in under 2 months.

### Goals (in scope, all mandatory per settled decisions)
- One Android app, role resolved at login (Customer / Merchant / Courier / Admin).
- Merchant catalog management (categories, products, availability toggle).
- Customer browse → cart → checkout with map-pin + textual-reference address.
- Real-time order lifecycle visible to all relevant actors across devices.
- Open-pool courier assignment with race-safe "first to accept wins" claim.
- Live courier location tracking on a map during active delivery.
- Cash-on-delivery only, amounts in NIO (Córdobas).
- Admin-configurable flat citywide delivery fee.
- Admin visibility/management of the platform (at minimum: fee config,
  users/merchants oversight — exact admin scope is an open question, see §6).

### Non-Goals (explicitly OUT — protects the 2-month solo timeline)
- Online/card payments, wallets, or any payment gateway integration.
- iOS app or any cross-platform distribution beyond Android.
- Web-based admin console (admin is a role inside the same Android app).
- Multi-city / multi-region support — Nagarote only, single flat fee zone.
- Promotions, discounts, coupons, loyalty programs.
- Ratings/reviews, chat/messaging between actors (beyond order status).
- Order editing after placement (cancel/reject only, no partial edits).
- Analytics/BI dashboards beyond what Admin needs to configure the fee.
- Push-notification-perfect delivery guarantees (best-effort FCM is enough).
- Automated courier-to-order matching/routing optimization (pool is manual accept).
- Offline-first support — app assumes connectivity (delivery app, low value offline).

## 2. Per-Role User Flows (minimal but complete)

### Customer
1. Register/login (phone or email) → role = Customer.
2. Browse merchants (list, maybe by category) → open merchant → browse products.
3. Add products to cart, adjust quantities.
4. Checkout: set delivery address (drop pin on map + free-text reference),
   confirm flat delivery fee + item total in NIO, confirm cash payment, place order.
5. Track order status in real time (placed → accepted → preparing → ready →
   courier assigned → picked up → on the way → delivered) with live courier
   position on map once a courier is assigned.
6. Can cancel while order is still `placed`/`accepted` (before merchant starts
   preparing); cannot cancel once `preparing` or later without merchant/admin
   involvement (open question, see §6).

### Merchant
1. Login → role = Merchant, scoped to their own merchant profile.
2. Manage catalog: create/edit categories and products (name, price, photo,
   availability toggle).
3. Receive new orders in real time; **accept or reject** each incoming order
   (reject requires a reason, releases customer/notifies).
4. Mark accepted order as `preparing`, then `ready for pickup` — this is the
   trigger that opens the order to the courier pool.
5. See which courier claimed the order and its status until handed off.
6. Toggle "store open/closed" to stop receiving new orders.

### Courier
1. Login → role = Courier.
2. Toggle availability (online/offline).
3. While online, see the open pool of `ready` orders (not yet claimed).
4. Accept an order — this must be a race-safe claim (only one courier wins if
   multiple tap simultaneously).
5. Navigate to merchant (map + address reference), mark `picked up`.
6. App streams live location to backend while delivery is in progress.
7. Navigate to customer, mark `delivered` (or report failed delivery —
   open question on failure/rejection handling, see §6).

### Admin
1. Login → role = Admin.
2. Configure the citywide flat delivery fee (single value, NIO).
3. View/manage merchants and couriers (at minimum: activate/deactivate
   accounts — exact CRUD scope is an open question, see §6).
4. View basic order oversight (list/status), for support/debugging purposes.

## 3. Domain Model Draft

- **User**: id, name, phone, email, passwordHash/authProviderId, role (enum:
  customer/merchant/courier/admin), createdAt, active (bool).
- **MerchantProfile**: id, userId (owner), name, description, photoUrl,
  isOpen (bool), createdAt. 1:1 with a User of role=merchant.
- **Category**: id, merchantId, name, sortOrder.
- **Product**: id, merchantId, categoryId, name, description, priceNio,
  photoUrl, isAvailable (bool).
- **CartItem** (client-local or lightweight server cart): productId, quantity,
  unitPriceSnapshot. Cart is scoped to one merchant (open question in §6).
- **Order**: id, customerId, merchantId, courierId (nullable until claimed),
  status (enum, see §4), subtotalNio, deliveryFeeNio, totalNio,
  deliveryAddress (embedded: lat, lng, textReference), createdAt,
  acceptedAt, readyAt, claimedAt, pickedUpAt, deliveredAt, cancelledAt,
  cancelReason (nullable), rejectReason (nullable).
- **OrderItem**: id, orderId, productId, nameSnapshot, unitPriceSnapshot, quantity.
- **CourierLocation**: courierId, lat, lng, updatedAt — written frequently
  while courier is online/on-delivery; read by customer app and admin
  (last-write-wins, keyed by courierId).
- **AppSettings**: singleton, deliveryFeeNio, updatedBy (adminId), updatedAt.
- **CourierAvailability** (could merge into User or CourierLocation): courierId,
  isOnline (bool), lastSeenAt.

Relations: User 1—1 MerchantProfile (if role=merchant); MerchantProfile 1—N
Category 1—N Product; User(customer) 1—N Order; MerchantProfile 1—N Order;
User(courier) 1—N Order (0..1 active claim — open question); Order 1—N
OrderItem; AppSettings is a singleton read by all clients.

## 4. Order State Machine

```
placed --(merchant accepts)--> accepted --(merchant starts prep)--> preparing
  --(merchant marks ready)--> ready --(courier claims, race-safe)--> claimed
  --(courier marks picked up)--> picked_up --(courier marks delivered)--> delivered

placed --(merchant rejects)--> rejected [terminal]
placed | accepted --(customer cancels)--> cancelled [terminal]
ready --(timeout / admin intervention, no courier claims)--> ??? [open question]
claimed --(courier fails to complete)--> ??? [open question — reopen to pool?]
```

- **placed**: Customer at checkout.
- **accepted / rejected**: Merchant (rejected is terminal, carries a reason).
- **preparing**: Merchant.
- **ready**: Merchant — pool-opening event; all online couriers see it in real time.
- **claimed**: Courier — first successful atomic claim wins; losers see the
  order disappear from the pool immediately.
- **picked_up**: Courier.
- **delivered**: Courier (terminal, success).
- **cancelled**: Customer, only while `placed` or `accepted` (cutoff to confirm, §6).
- **Race-safe claim**: a single atomic conditional write (Firestore transaction /
  Supabase RPC with row lock) that only succeeds if `status == ready AND
  courierId == null`, then sets `status = claimed, courierId = <claimant>`.
  Central correctness requirement; needs a dedicated concurrent-claim test.

## 5. Stack Options for Android + BaaS

| Approach | Pros | Cons | Effort |
|---|---|---|---|
| **Kotlin + Jetpack Compose + Firebase (Auth/Firestore/FCM)** | Native Android, first-class tooling; Firestore transactions for race-safe claim; FCM push; real-time listeners; Spark free tier: 1 GiB storage, 50K reads/day, 20K writes/day, 10 GiB egress/month ([back4app 2026](https://blog.back4app.com/firebase-pricing/)) | Quotas burned fast by unthrottled live location; Google lock-in; Compose learning curve | Medium |
| **Flutter + Firebase** | Same backend benefits; fast CRUD UI | Extra language (Dart) with no payoff since Android-only; heavier apps | Medium |
| **Kotlin or Flutter + Supabase** | Postgres ACID transactions / row locks for claim; Realtime; free tier: 500 MB DB, 1 GB storage, 50K MAUs, 200 concurrent realtime connections, 2M realtime msgs/month ([itpathsolutions 2026](https://www.itpathsolutions.com/supabase-free-tier-limits), [dev.to 2026](https://dev.to/nayankyada/supabase-pricing-2026-free-tier-limits-compute-costs-when-to-upgrade-52af)) | Free projects **pause after 1 week of inactivity**; Android SDK less mature | Medium |

### Maps SDK
- **Google Maps SDK for Android**: SDK usage is free, but an API key requires an
  enabled Cloud Billing account with a payment method
  ([FAQ](https://developers.google.com/maps/faq),
  [usage-and-billing](https://developers.google.com/maps/documentation/android-sdk/usage-and-billing)).
  Real blocker for a student without a card.
- **MapLibre Native Android + OSM tiles**: no billing account or API key; heavy
  geocoding not required (manual pin + free-text reference).

### Recommendation
**Kotlin + Jetpack Compose + Firebase (Auth, Firestore, FCM) + MapLibre (OSM tiles)**.
- Firestore transactions cover the race-safe claim directly.
- Avoiding Google Maps sidesteps the mandatory billing-account setup.
- Live courier location must be throttled (e.g. every 5–10 s) to protect the
  Firestore write quota — a design constraint.
- Firebase Emulator Suite enables local tests without production quotas —
  compatible with Strict TDD once re-enabled in `sdd-design`.
- Supabase is a credible fallback; its 1-week auto-pause is the deciding risk.

Pricing figures are from third-party aggregators; re-verify against official
pages in `sdd-design`.

## 6. Risks, Assumptions, Open Questions

### Top Risk (timeline)
1. **Scope vs. 2-month solo timeline**: user explicitly rejected phasing; all
   features are mandatory. Accepted by the user. Mitigation: fastest stack and
   vertical slices (§7) so something is demoable at every checkpoint.

### Other Risks
2. Firestore write-quota exhaustion if live location isn't throttled.
3. Race-safe claim is correctness-critical; needs a dedicated automated test.
4. Solo developer = no code review; enable Strict TDD once the stack is chosen.
5. Google Maps billing requirement (ruled out in favor of MapLibre/OSM).

### Assumptions (to validate)
- Reverse geocoding NOT required.
- A courier holds only one active order at a time.
- Flat citywide delivery fee per order (confirmed).

### Open Questions (ranked)
1. **Cancellation cutoff**: only while `placed`, or also `accepted`?
2. **Courier claim failure/abandonment**: auto-reopen to pool, merchant/admin
   intervention, or out of scope?
3. **No-courier-available timeout**: escalate to Admin, or wait indefinitely?
4. **Concurrent orders per courier**: confirm one active order at a time.
5. **Admin CRUD scope**: fee only, or also approve/suspend merchant and courier
   accounts (self-registration vs. admin-approved onboarding)?
6. **Multi-merchant cart**: confirm single-merchant cart per checkout.

## 7. Suggested Vertical-Slice Delivery Order

1. **Auth + roles skeleton** — login/register, role resolution, empty home per role.
2. **Merchant catalog + Customer browse** — read-only window shopping.
3. **Cart + checkout + order placement** — merchant receives and progresses orders in real time.
4. **Courier pool + race-safe claim + pickup/delivery** — full happy path.
5. **Live courier location on map** — after the core flow works.
6. **Admin settings + oversight** — fee UI plus resolved Admin scope.

Each slice maps to a chained PR under the auto-chain delivery strategy.

## Resolved Decisions (user, 2026-09-29)

1. **Cancellation cutoff**: the customer can cancel only while the order is
   `placed` (before merchant acceptance).
2. **Courier abandonment**: Admin can release a `claimed` order back to
   `ready` (the pool). No automatic timers.
3. **No courier available**: order waits in the pool indefinitely; Admin sees
   it in a "delayed/stuck orders" view and may cancel it.
4. **Courier load**: one active order per courier; the pool is hidden while
   the courier has an active order.
5. **Onboarding**: customers self-register and are active immediately;
   merchants and couriers self-register as `pending` and need Admin approval.
   Admin can suspend merchant and courier accounts.
6. **Cart**: single merchant per cart/order; adding a product from another
   merchant prompts to clear the cart.

## Ready for Proposal
Yes. All open questions are resolved.
