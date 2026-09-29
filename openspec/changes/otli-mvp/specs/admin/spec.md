# Admin Specification

## Purpose

Defines the Admin role's platform-configuration and oversight
responsibilities: delivery fee configuration, merchant/courier approval and
suspension, visibility into stuck/unclaimed orders, release of abandoned
claims back to the pool, and general order oversight for support. Depends
on `auth-roles` for account status transitions, `ordering` for the order
state machine, and `courier-dispatch` for the claim/pool semantics
underlying release and stuck-order handling.

## Requirements

### Requirement: Flat Delivery Fee Configuration

The system MUST allow Admin to configure a single, citywide flat delivery
fee expressed in NIO. The system MUST apply the currently configured fee
only to orders placed after the change; it MUST NOT retroactively alter the
snapshotted fee or total of any already-placed order (see `ordering`,
Requirement: Order Total Reflects Snapshotted Fee and Prices).

#### Scenario: Admin updates the flat fee

- GIVEN an Admin account
- WHEN the Admin sets a new flat delivery fee value in NIO
- THEN the system MUST persist the new fee as the current platform fee
- AND subsequent order placements MUST snapshot this new fee

#### Scenario: Non-admin cannot change the fee

- GIVEN a Customer, Merchant, or Courier account
- WHEN that account attempts to change the platform delivery fee
- THEN the system MUST reject the attempt

### Requirement: Merchant and Courier Approval

The system MUST allow Admin to approve a `pending` Merchant or Courier
account, transitioning it to `active`, granting the account access to its
role-scoped actions.

#### Scenario: Admin approves a pending merchant

- GIVEN a Merchant account in status `pending`
- WHEN an Admin approves the account
- THEN the system MUST transition the account status to `active`
- AND the merchant MUST gain access to catalog-management actions

#### Scenario: Admin approves a pending courier

- GIVEN a Courier account in status `pending`
- WHEN an Admin approves the account
- THEN the system MUST transition the account status to `active`
- AND the courier MUST gain the ability to go online and view the pool

#### Scenario: Pending account remains blocked until approved

- GIVEN a Merchant or Courier account in status `pending`
- WHEN the account has not yet been approved by an Admin
- THEN role-scoped actions MUST remain blocked for that account (see `auth-roles`, Requirement: Account Status Gating)

### Requirement: Merchant and Courier Suspension

The system MUST allow Admin to suspend an `active` Merchant or Courier
account, immediately blocking that account's role-scoped actions.

#### Scenario: Admin suspends an active merchant

- GIVEN a Merchant account in status `active`
- WHEN an Admin suspends the account
- THEN the system MUST transition the account status to `suspended`
- AND the merchant MUST immediately lose access to catalog-management actions

#### Scenario: Admin suspends an active courier

- GIVEN a Courier account in status `active`, currently `offline` with no active order
- WHEN an Admin suspends the account
- THEN the system MUST transition the account status to `suspended`
- AND the courier MUST immediately lose the ability to go online or claim orders

### Requirement: Stuck and Unclaimed Orders Visibility

The system MUST provide Admin a view of orders that are `ready` and remain
unclaimed beyond ordinary expectations ("delayed/stuck orders"), and MUST
allow Admin to cancel such an order.

#### Scenario: Admin sees unclaimed ready orders

- GIVEN one or more orders in state `ready` with no assigned courier
- WHEN an Admin opens the stuck/unclaimed orders view
- THEN the system MUST list those orders

#### Scenario: Admin cancels a stuck order

- GIVEN an order in state `ready` with no assigned courier
- WHEN an Admin cancels the order
- THEN the system MUST transition the order to `cancelled`

### Requirement: Release of Abandoned Claims

The system MUST allow Admin to release an order in state `claimed` back to
state `ready`, clearing its assigned courier, so it becomes available again
in the open pool.

#### Scenario: Admin releases a claimed order back to the pool

- GIVEN an order in state `claimed`, assigned to courier X
- WHEN an Admin releases the claim
- THEN the system MUST transition the order to `ready`
- AND the system MUST clear the previously assigned courier
- AND the order MUST become visible again in the pool to eligible online couriers

#### Scenario: Non-admin cannot release a claim

- GIVEN an order in state `claimed`
- WHEN a Customer, Merchant, or a Courier other than Admin attempts to release the claim
- THEN the system MUST reject the attempt

### Requirement: Order List for Support

The system MUST provide Admin a list of orders with their current status,
for support and debugging purposes, across all merchants and customers.

#### Scenario: Admin views the full order list

- GIVEN orders exist across multiple merchants and customers
- WHEN an Admin opens the order list
- THEN the system MUST display orders with their current status regardless of which merchant or customer they belong to
