# Ordering Specification

## Purpose

Defines the customer cart and checkout flow, order placement, the merchant
side of order progression, customer cancellation, and the order state
machine that governs all actor-authorized transitions. Depends on
`auth-roles` for actor identity/status and `merchant-catalog` for product
and store-availability data referenced at checkout.

## Requirements

### Requirement: Single-Merchant Cart

The system MUST scope a customer's active cart to exactly one merchant at a
time.

#### Scenario: Customer adds a product from the same merchant

- GIVEN a customer has at least one item in their cart from merchant M
- WHEN the customer adds another product also belonging to merchant M
- THEN the system MUST add the product to the existing cart without prompting

#### Scenario: Customer adds a product from a different merchant

- GIVEN a customer has at least one item in their cart from merchant M
- WHEN the customer attempts to add a product belonging to a different merchant M2
- THEN the system MUST prompt the customer to confirm clearing the current cart before adding the new item
- AND if the customer confirms, the system MUST clear the cart of merchant M's items and add the new item scoped to merchant M2
- AND if the customer declines, the system MUST leave the cart unchanged and MUST NOT add the new item

### Requirement: Checkout Requires Pin, Reference, and Cash Confirmation

The system MUST require a map pin location, a free-text address reference,
and confirmation of cash payment before an order can be placed. The system
MUST express all monetary amounts (item prices, delivery fee, subtotal,
total) in NIO.

#### Scenario: Customer completes checkout with pin and reference

- GIVEN a customer has at least one item in their cart
- WHEN the customer sets a map pin, enters a textual reference, and confirms cash payment
- THEN the system MUST allow the order to be placed
- AND the order MUST record the pin coordinates and the textual reference

#### Scenario: Checkout is blocked without a pin

- GIVEN a customer has items in their cart
- WHEN the customer attempts to place the order without setting a map pin
- THEN the system MUST reject the placement and indicate the pin is required

### Requirement: Order Total Reflects Snapshotted Fee and Prices

The system MUST compute an order's subtotal from the unit prices of items at
the moment of placement, and MUST snapshot the delivery fee configured by
Admin at that same moment. The order's total MUST equal subtotal plus the
snapshotted fee. A later change to the delivery fee or a product's price
MUST NOT alter the total, subtotal, item prices, or fee of any
already-placed order.

#### Scenario: Order total equals subtotal plus fee at placement time

- GIVEN a cart with items summing to a subtotal S in NIO, and an Admin-configured flat delivery fee F in NIO
- WHEN the customer places the order
- THEN the order's total MUST equal S + F
- AND the order MUST persist S and F as its own recorded values

#### Scenario: A later fee change does not affect existing orders

- GIVEN an order O was placed with a snapshotted fee F1
- WHEN the Admin later changes the platform delivery fee to F2 (F2 != F1)
- THEN order O's stored fee and total MUST remain based on F1
- AND only orders placed after the change MUST use F2

### Requirement: Order Placement Validates Store and Product Availability

The system MUST reject order placement if the merchant's store is `closed`
or if any cart item references a product that has become unavailable since
it was added to the cart.

#### Scenario: Placement blocked by a closed store

- GIVEN a customer's cart contains items from merchant M
- WHEN merchant M closes their store before the customer places the order
- THEN the system MUST reject the placement
- AND the system MUST inform the customer the store is closed

#### Scenario: Placement blocked by a product becoming unavailable

- GIVEN a customer's cart contains a product P that was available when added
- WHEN product P becomes unavailable before checkout completes
- THEN the system MUST reject placement of that item
- AND the system MUST inform the customer which item is no longer available

### Requirement: Order State Machine With Actor-Authorized Transitions

The system MUST model an order through the states `placed`, `accepted`,
`preparing`, `ready`, `claimed`, `picked_up`, `delivered`, `rejected`, and
`cancelled`. Every transition MUST be authorized only for the actor role
(and, where applicable, resource ownership) defined below, and the system
MUST reject any transition attempted by an unauthorized actor or from an
invalid source state.

Valid transitions:
- `placed` → `accepted` (Merchant, owner of the order's merchant)
- `placed` → `rejected` (Merchant, owner; MUST include a reason)
- `placed` → `cancelled` (Customer, owner of the order)
- `accepted` → `preparing` (Merchant, owner)
- `preparing` → `ready` (Merchant, owner)
- `ready` → `claimed` (Courier; see `courier-dispatch` for the race-safe claim rule)
- `claimed` → `picked_up` (Courier, the one who claimed the order)
- `picked_up` → `delivered` (Courier, the one who claimed the order)
- `claimed` → `ready` (Admin only; see `admin` capability for claim release)

`rejected`, `cancelled`, and `delivered` are terminal states.

#### Scenario: Merchant rejects a placed order with a reason

- GIVEN an order in state `placed` belonging to merchant M
- WHEN merchant M rejects the order and provides a reason
- THEN the system MUST transition the order to `rejected`
- AND the system MUST persist the rejection reason
- AND the system MUST NOT allow further state transitions on that order

#### Scenario: Merchant rejects without a reason is refused

- GIVEN an order in state `placed`
- WHEN the owning merchant attempts to reject the order without providing a reason
- THEN the system MUST reject the rejection attempt and require a reason

#### Scenario: Customer cancels only while placed

- GIVEN an order belonging to the customer, in state `placed`
- WHEN the customer cancels the order
- THEN the system MUST transition the order to `cancelled`

#### Scenario: Customer cannot cancel once accepted or later

- GIVEN an order belonging to the customer, in any state other than `placed` (for example `accepted`, `preparing`, `ready`, `claimed`, `picked_up`)
- WHEN the customer attempts to cancel the order
- THEN the system MUST reject the cancellation attempt
- AND the order's state MUST remain unchanged

#### Scenario: A different customer cannot cancel someone else's order

- GIVEN an order in state `placed` belonging to customer C1
- WHEN a different customer C2 attempts to cancel that order
- THEN the system MUST reject the attempt

#### Scenario: Merchant cannot progress an order out of sequence

- GIVEN an order in state `placed`
- WHEN the owning merchant attempts to mark it `ready` directly (skipping `accepted` and `preparing`)
- THEN the system MUST reject the transition

#### Scenario: Real-time visibility of state changes

- GIVEN an order whose state changes on one actor's device
- WHEN the change is persisted
- THEN every other actor authorized to view that order (its customer, its merchant, and, once claimed, its courier; Admin at all times) MUST see the updated state without a manual refresh
