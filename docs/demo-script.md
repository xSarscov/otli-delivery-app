# Otli demo script

Last run: 2026-10-14 (sections 2-7 walked through by the agent with adb while the user watched the phone, then confirmed by the user; section 8 from the automated runs of the same day). The run is recorded in `openspec/changes/otli-device-evidence/evidence/o4-walkthrough.txt`.

Manual end-to-end checklist for the academic demo. Every box under "Success Criteria (academic demo)"
in `openspec/changes/otli-mvp/proposal.md` maps to at least one step here (see the table at the end).
Tick each step as you go; if one fails, stop and note which step and what the screen said.

## 1. Before you start

You need the PC (emulators + seed) and two phones for the claim race. One phone is enough for
everything else: switch accounts by signing out from the overflow menu (three dots) of each home.

1. Start the emulators and load the seed data. USB (phone reaches the PC through `adb reverse`) and
   Wi-Fi (`emulators:lan`) setups are in `README.md`, sections "Instrumented tests on a device",
   "Running the emulators and seed data" and "Testing on a phone over Wi-Fi".
2. Install the debug APK built for that setup (`./gradlew assembleDebug`, add
   `-Potli.emulatorHost=<PC LAN IP>` for Wi-Fi).
3. For a clean run, stop the emulators, delete `backend/emulator-data`, start them again and re-seed.
   Re-seeding alone restores the fee (C$ 30.00), the two seeded couriers (offline, free) and the two
   pending accounts, but it does not delete orders from earlier runs.

All seeded accounts use the password `otli-demo-123`.

| Role | Email | Notes |
|---|---|---|
| Admin | `admin@otli.test` | Only account that can approve, suspend, set the fee, release and cancel |
| Customer | `customer1@otli.test` | Ana Lopez |
| Merchant | `merchant1@otli.test` | Comedor Dona Marta, open, has a catalog |
| Merchant | `merchant2@otli.test` | Pizzeria Don Chepe, closed |
| Merchant (pending) | `merchant-pending@otli.test` | Blocked until the Admin approves it |
| Courier | `courier1@otli.test` | Luis Mendoza |
| Courier | `courier2@otli.test` | Marta Ruiz |
| Courier (pending) | `courier-pending@otli.test` | Blocked until the Admin approves it |

Whenever a step says "sign in as X", sign out first. Whenever a step says "on phone A / phone B",
the two phones are signed in at the same time (A as courier 1, B as courier 2).

## 2. Accounts, roles and gates

- [x] Sign in as the Admin, customer, merchant 1 and courier 1 in turn. Each lands on its own home
      (Admin: tabs Active, All orders, Accounts, Fee; customer: store list; merchant: Orders, Catalog,
      Store tabs; courier: availability and the pool). (2026-10-14)
- [x] Sign in as `merchant-pending@otli.test`: the screen says "Approval pending" and nothing else is
      reachable. Sign out. Do the same with `courier-pending@otli.test`. (2026-10-14)
- [x] Register a brand-new merchant ("Create an account", role Merchant, store name, phone, pin) and a
      brand-new courier. Each lands on "Approval pending". (2026-10-14)

## 3. Fee, order placement and totals

- [x] Sign in as the customer, open Comedor Dona Marta, add two or three products, open the cart and
      check out. The summary shows Subtotal, Delivery fee C$ 30.00 and Total = Subtotal + fee. (2026-10-14)
- [x] Drop the delivery pin, write an address reference, tick the cash confirmation, press
      "Place order". The tracking screen opens with "Waiting for the store to answer". Note the total. (2026-10-14)
- [x] Sign in as the Admin, open the Fee tab, type `45` and press "Save fee". The screen shows
      "Fee saved." and "Current fee: C$ 45.00". (2026-10-14)
- [x] Sign in as the customer. "My orders" still shows the first order with its original total. Start
      a new order: checkout now shows Delivery fee C$ 45.00 and a total that adds up. Place it. (2026-10-14)
- [x] Sign in as the Admin and set the fee back to `30`. (2026-10-14)

## 4. Live status without refreshing

Use two phones (or sign in and out on one) for these. The customer screen must update by itself.

- [x] Merchant 1, Orders tab: the placed order is under "New orders". Press "Accept". The customer's
      tracking screen moves to "The store accepted your order" without any pull or reload. (2026-10-14)
- [x] Merchant: "Start preparing", then "Mark ready". The customer follows each status ("Your order is being prepared", "Your order is ready"). (2026-10-14)
- [x] Customer cancel rule: place a third order and, while it is still "Waiting for the store to answer", press
      "Cancel order" on its tracking screen. It becomes "Cancelled". On any order the store has
      already accepted, "Cancel order" is disabled and the hint says it can only be cancelled while
      the store has not answered. (2026-10-14)

## 5. Courier claim race and one active order

Phone A is courier 1, phone B is courier 2. Both go online ("You are online").

- [x] Merchant marks an order "Ready". Both couriers see it under "Orders waiting for a courier". (2026-10-14)
- [ ] Both press "Claim" at the same moment (count down 3-2-1). Exactly one succeeds; the other sees
      "That order was already taken by another courier." and the order leaves its list. (not ticked: covered by automated tests (R2), no manual race performed, 2026-10-14)
- [x] The winner now has an active delivery. Mark another order ready: the winner no longer sees the pool
      at all (no "Orders waiting for a courier" list, no "Claim" button), so it cannot claim another order, and it cannot go offline
      (the switch is disabled and the text says "Finish your delivery to go offline.") (2026-10-14)

## 6. Live courier position

- [x] The winner keeps GPS on (location services). While the order is claimed or picked up, the
      customer's tracking screen shows the "Courier location" map with the courier dot and the
      delivery pin, and how long ago the position was updated. (2026-10-14; the courier dot is not in the accessibility tree, the user confirmed it visually on the phone)
- [x] The courier presses "Picked up", then "Delivered". The customer sees "Delivered" and the map
      goes away. The courier is free again and can go offline. (2026-10-14)

## 7. Admin: approvals, suspensions, release and cancellation

Sign in as the Admin for all of this.

Approvals and suspensions (tab Accounts):

- [x] The new merchant and courier from step 2, plus the two seeded pending accounts, are under
      "Waiting for approval". Press "Approve" on a merchant and on a courier. They move to "Active". (2026-10-14)
- [x] Sign in as the approved merchant: it now reaches the Orders, Catalog and Store tabs. Sign in as the
      approved courier: it can go online. (2026-10-14)
- [x] Back as the Admin, press "Suspend" on that merchant. Signed in on the merchant phone at the
      same time, the app switches to "Account suspended" by itself. Press "Reactivate" and the
      merchant gets back in. (2026-10-14)
- [x] Suspend a courier the same way: it can no longer go online or claim. (2026-10-14)

Release a claimed order (tab Active):

- [x] Mark an order ready, let courier 1 claim it. As the Admin, tab Active shows it under "With a
      courier". Press "Release to the pool". It moves to "Waiting for a courier", courier 1's active
      delivery disappears and courier 1 is free; courier 2 can now claim it. (2026-10-14)

Cancel an order with a reason (tab Active):

- [x] Place an order and leave it unanswered (the merchant does nothing). It shows under "In the
      kitchen". Press "Cancel order", write a reason, confirm. The customer's tracking screen shows
      "Cancelled" and "Cancelled by Otli: <reason>". (2026-10-14)
- [x] Mark another order ready and leave it unclaimed: it shows under "Waiting for a courier" with how
      many minutes it has waited. Cancel it with a reason; the customer sees it. (2026-10-14)
- [x] A claimed order has no "Cancel order" button: release it first, then cancel it. (2026-10-14)
- [x] Let courier 1 claim an order and press "Picked up", then leave. As the Admin, tab Active shows it under
      "Picked up, on the way" with "Cancel order" and no release. Cancel it with a reason: the customer sees
      "Cancelled by Otli: <reason>", courier 1's active delivery disappears, the courier is free and the order
      does not come back to the pool. (2026-10-14)

Support list and detail (tab All orders):

- [x] Every order of every customer and store is listed, newest first, with its status. (2026-10-14; exercised with one customer and one store only)
- [x] Tap one that a courier is delivering: the detail shows the store, customer, items, totals and
      the live map with the courier position. Back returns to the list. (2026-10-14)

## 8. Automated proofs (run on the PC, no phone)

- [x] `./gradlew verifyAll` finishes green (unit tests, lint and the rules tests). (2026-10-14: BUILD SUCCESSFUL in 7m 8s, with 321/321 rules tests in 17 files; `testDebugUnitTest` ran 1075 tests green earlier the same day)
- [x] `npm --prefix backend test` is green. It includes `claim-concurrency.test.ts`, the 20-round claim
      race, the transition matrix and the Admin release and cancel rules. (2026-10-14: 321/321 rules tests in 17 files, O5)
- [x] Optional, with a phone attached: `npm --prefix backend run test:android` runs the Firestore
      adapter tests, including the Admin repository. (2026-10-14: 65/65 in 8 classes on the Xiaomi, Admin 13, task 3.7)

## 9. Success criteria coverage

| Success criterion | Steps |
|---|---|
| Four roles log in and reach their own home; pending and suspended are blocked | 2, 7 (suspension) |
| Cash order in NIO with pin and reference; total equals subtotal plus the Admin fee | 3 |
| Status changes reach the other devices without refresh | 4, 6 |
| Two couriers claiming at once: exactly one wins | 5, 8 |
| A courier with an active order cannot see or claim another | 5 |
| The customer sees the courier moving on the map | 6, 7 (detail) |
| Admin approves, suspends, changes the fee, releases a claim, cancels a stuck order | 3, 7 |
| The customer can cancel only while the order is still waiting for the store | 4 |
| Automated tests cover the state machine and claim logic, locally | 8 |
