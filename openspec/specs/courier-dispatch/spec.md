# Courier Dispatch Specification

## Purpose

Defines courier availability, the open pool of `ready` orders, the
race-safe claim rule, the one-active-order-per-courier constraint, and
pickup/delivery transitions. Depends on `auth-roles` for courier identity
and account-status gating, and on `ordering` for the order state machine
into which claim/pickup/delivery transitions plug.

## Requirements

### Requirement: Courier Online/Offline Availability

The system MUST allow an `active` Courier to toggle between `online` and
`offline` availability. Only `online` couriers MUST be able to view or claim
orders from the pool.

#### Scenario: Offline courier cannot see the pool

- GIVEN an `active` Courier who is `offline`
- WHEN that courier opens the pool screen
- THEN the system MUST NOT display any `ready` orders to claim

#### Scenario: Courier goes online and sees the pool

- GIVEN an `active` Courier toggles to `online`
- WHEN the toggle succeeds
- THEN the system MUST make the pool of `ready`, unclaimed orders visible to that courier (subject to the one-active-order rule below)

### Requirement: Open Pool Visibility Hidden by Active Order

The system MUST hide the pool of claimable orders from any courier who
already has an active order (in state `claimed` or `picked_up`).

#### Scenario: Courier with an active order does not see the pool

- GIVEN an `active`, `online` Courier with an order currently in state `claimed` or `picked_up`
- WHEN that courier opens the pool screen
- THEN the system MUST NOT display any claimable orders

#### Scenario: Courier's pool reappears after completing delivery

- GIVEN a courier whose only active order transitions to `delivered`
- WHEN the courier returns to the pool screen
- THEN the system MUST display the current `ready`, unclaimed orders again

### Requirement: Race-Safe Order Claim

The system MUST implement claiming a `ready` order as a single atomic
operation that succeeds only if, at the moment of the attempt, the order is
still in state `ready` with no courier assigned, and the claiming courier
has no other active order. Exactly one courier MUST win when multiple
couriers attempt to claim the same order concurrently; all other concurrent
attempts on that same order MUST fail without side effects.

#### Scenario: Two couriers claim the same order concurrently — exactly one wins

- GIVEN an order in state `ready` with no courier assigned
- WHEN two different `online` couriers, each with no active order, attempt to claim it at effectively the same time
- THEN exactly one of the two claim attempts MUST succeed, transitioning the order to `claimed` with that courier assigned
- AND the other claim attempt MUST fail
- AND the losing courier MUST see the order disappear from their pool immediately

#### Scenario: A courier with an active order cannot claim another order

- GIVEN a courier who already has an order in state `claimed` or `picked_up`
- WHEN that courier attempts to claim a different `ready` order
- THEN the system MUST reject the claim attempt
- AND the state of the target order MUST remain unchanged

#### Scenario: Claiming an order already claimed by someone else fails cleanly

- GIVEN an order that has already transitioned to `claimed` by courier X
- WHEN a different courier Y attempts to claim the same order
- THEN the system MUST reject courier Y's attempt
- AND the order MUST remain assigned to courier X with no change to its state

### Requirement: Pickup and Delivery Transitions

The system MUST allow only the courier who claimed an order to mark it
`picked_up` and later `delivered`, and MUST reject these transitions from
any other courier or out of sequence.

#### Scenario: Claiming courier marks the order picked up

- GIVEN an order in state `claimed`, assigned to courier X
- WHEN courier X marks the order `picked_up`
- THEN the system MUST transition the order to `picked_up`

#### Scenario: A different courier cannot mark someone else's claimed order picked up

- GIVEN an order in state `claimed`, assigned to courier X
- WHEN a different courier Y attempts to mark it `picked_up`
- THEN the system MUST reject the attempt

#### Scenario: Claiming courier marks the order delivered

- GIVEN an order in state `picked_up`, assigned to courier X
- WHEN courier X marks the order `delivered`
- THEN the system MUST transition the order to `delivered` (terminal)
- AND the courier's active-order slot MUST become free, making the pool visible again

#### Scenario: Cannot mark delivered before pickup

- GIVEN an order in state `claimed` (not yet `picked_up`)
- WHEN the assigned courier attempts to mark it `delivered` directly
- THEN the system MUST reject the transition
