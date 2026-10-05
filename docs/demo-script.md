# Otli demo script

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

- [ ] Sign in as the Admin, customer, merchant 1 and courier 1 in turn. Each lands on its own home
      (Admin: tabs Active, All orders, Accounts, Fee; customer: store list; merchant: Orders, Catalog,
      Store tabs; courier: availability and the pool).
- [ ] Sign in as `merchant-pending@otli.test`: the screen says "Approval pending" and nothing else is
      reachable. Sign out. Do the same with `courier-pending@otli.test`.
- [ ] Register a brand-new merchant ("Create an account", role Merchant, store name, phone, pin) and a
      brand-new courier. Each lands on "Approval pending".

## 3. Fee, order placement and totals

- [ ] Sign in as the customer, open Comedor Dona Marta, add two or three products, open the cart and
      check out. The summary shows Subtotal, Delivery fee C$ 30.00 and Total = Subtotal + fee.
- [ ] Drop the delivery pin, write an address reference, tick the cash confirmation, press
      "Place order". The tracking screen opens with "Waiting for the store to answer". Note the total.
- [ ] Sign in as the Admin, open the Fee tab, type `45` and press "Save fee". The screen shows
      "Fee saved." and "Current fee: C$ 45.00".
- [ ] Sign in as the customer. "My orders" still shows the first order with its original total. Start
      a new order: checkout now shows Delivery fee C$ 45.00 and a total that adds up. Place it.
- [ ] Sign in as the Admin and set the fee back to `30`.

## 4. Live status without refreshing

Use two phones (or sign in and out on one) for these. The customer screen must update by itself.

- [ ] Merchant 1, Orders tab: the placed order is under "New orders". Press "Accept". The customer's
      tracking screen moves to "The store accepted your order" without any pull or reload.
- [ ] Merchant: "Start preparing", then "Mark ready". The customer follows each status ("Your order is being prepared", "Your order is ready").
- [ ] Customer cancel rule: place a third order and, while it is still "Waiting for the store to answer", press
      "Cancel order" on its tracking screen. It becomes "Cancelled". On any order the store has
      already accepted, "Cancel order" is disabled and the hint says it can only be cancelled while
      the store has not answered.

## 5. Courier claim race and one active order

Phone A is courier 1, phone B is courier 2. Both go online ("You are online").

- [ ] Merchant marks an order "Ready". Both couriers see it under "Orders waiting for a courier".
- [ ] Both press "Claim" at the same moment (count down 3-2-1). Exactly one succeeds; the other sees
      "That order was already taken by another courier." and the order leaves its list.
- [ ] The winner now has an active delivery. Mark another order ready: the winner cannot claim it
      ("Finish your current delivery before claiming another order.") and cannot go offline
      ("Finish your delivery to go offline.").

## 6. Live courier position

- [ ] The winner keeps GPS on (location services). While the order is claimed or picked up, the
      customer's tracking screen shows the "Courier location" map with the courier dot and the
      delivery pin, and how long ago the position was updated.
- [ ] The courier presses "Picked up", then "Delivered". The customer sees "Delivered" and the map
      goes away. The courier is free again and can go offline.

## 7. Admin: approvals, suspensions, release and cancellation

Sign in as the Admin for all of this.

Approvals and suspensions (tab Accounts):

- [ ] The new merchant and courier from step 2, plus the two seeded pending accounts, are under
      "Waiting for approval". Press "Approve" on a merchant and on a courier. They move to "Active".
- [ ] Sign in as the approved merchant: it now reaches the Orders, Catalog and Store tabs. Sign in as the
      approved courier: it can go online.
- [ ] Back as the Admin, press "Suspend" on that merchant. Signed in on the merchant phone at the
      same time, the app switches to "Account suspended" by itself. Press "Reactivate" and the
      merchant gets back in.
- [ ] Suspend a courier the same way: it can no longer go online or claim.

Release a claimed order (tab Active):

- [ ] Mark an order ready, let courier 1 claim it. As the Admin, tab Active shows it under "With a
      courier". Press "Release to the pool". It moves to "Waiting for a courier", courier 1's active
      delivery disappears and courier 1 is free; courier 2 can now claim it.

Cancel an order with a reason (tab Active):

- [ ] Place an order and leave it unanswered (the merchant does nothing). It shows under "In the
      kitchen". Press "Cancel order", write a reason, confirm. The customer's tracking screen shows
      "Cancelled" and "Cancelled by Otli: <reason>".
- [ ] Mark another order ready and leave it unclaimed: it shows under "Waiting for a courier" with how
      many minutes it has waited. Cancel it with a reason; the customer sees it.
- [ ] A claimed order has no "Cancel order" button: release it first, then cancel it.

Support list and detail (tab All orders):

- [ ] Every order of every customer and store is listed, newest first, with its status.
- [ ] Tap one that a courier is delivering: the detail shows the store, customer, items, totals and
      the live map with the courier position. Back returns to the list.

## 8. Automated proofs (run on the PC, no phone)

- [ ] `./gradlew verifyAll` finishes green (unit tests, lint and the rules tests).
- [ ] `npm --prefix backend test` is green. It includes `claim-concurrency.test.ts`, the 20-round claim
      race, the transition matrix and the Admin release and cancel rules.
- [ ] Optional, with a phone attached: `npm --prefix backend run test:android` runs the Firestore
      adapter tests, including the Admin repository.

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
