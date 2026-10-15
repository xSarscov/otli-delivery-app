# Live Tracking Specification

## Purpose

Defines throttled courier-location publishing during an active delivery and
its real-time, access-restricted display on a map. Depends on
`courier-dispatch` for the `claimed`/`picked_up` order states that bound
when tracking is active, and on `auth-roles` for viewer identity.

## Requirements

### Requirement: Location Publishing Bounded to Active Delivery

The system MUST publish a courier's location only while that courier has an
order in state `claimed` or `picked_up`. The system MUST stop publishing
location for that order once it reaches `delivered`.

#### Scenario: Location publishes while claimed and picked up

- GIVEN a courier with an order in state `claimed`
- WHEN the courier's device reports its position
- THEN the system MUST accept and store the location update as associated with that order/courier

#### Scenario: Location stops at delivery

- GIVEN a courier's order transitions to `delivered`
- WHEN the courier's device reports a subsequent position
- THEN the system MUST NOT continue publishing that position as live tracking data for the completed order

#### Scenario: No tracking data before claim

- GIVEN an order still in state `ready` (not yet claimed)
- WHEN any courier's device reports position
- THEN the system MUST NOT associate that position with the unclaimed order

### Requirement: Throttled Update Frequency

The system MUST throttle location update publication to protect backend
write capacity, limiting effective publish frequency to no more than once
per configured throttle interval (on the order of 5-10 seconds) per active
delivery.

#### Scenario: Rapid successive location reports are throttled

- GIVEN a courier's device attempts to report location more frequently than the configured throttle interval
- WHEN multiple reports occur within one interval window
- THEN the system MUST publish at most one location update per interval for that courier's active delivery

### Requirement: Visibility Restricted to the Order's Customer and Admin

The system MUST restrict live location visibility for a given order to that
order's customer and to Admin. The system MUST NOT expose a courier's live
location to any other customer, merchant, or courier.

#### Scenario: The order's own customer sees the courier's live position

- GIVEN an order in state `claimed` or `picked_up`, belonging to customer C
- WHEN customer C opens the order tracking screen
- THEN the system MUST display the assigned courier's current live location on a map

#### Scenario: A different customer cannot see the courier's location

- GIVEN an order in state `picked_up`, belonging to customer C1
- WHEN a different customer C2 attempts to view live location for that order
- THEN the system MUST NOT expose the courier's location to customer C2

#### Scenario: Admin can view live location for oversight

- GIVEN an order in state `claimed` or `picked_up`
- WHEN an Admin views that order
- THEN the system MUST display the assigned courier's current live location

#### Scenario: The merchant cannot see courier live location

- GIVEN an order in state `picked_up`, prepared by merchant M
- WHEN merchant M views the order
- THEN the system MUST NOT expose the courier's live location to merchant M

### Requirement: Location Services Prompt and Warning for the Courier

Holding the location permission is not enough to publish a position: the
device location services MUST also be on. The system MUST detect when they
are off and MUST ask the courier to turn them on (the system "Turn on
location?" dialog) when the courier goes online and when they go off during
an active delivery. While they are off during an active delivery, the system
MUST show a visible warning with an action that re-opens the dialog. Claiming
and delivering MUST stay allowed, as with a denied permission.

#### Scenario: Going online with the location services off

- GIVEN a courier who holds the location permission and has the device location services off
- WHEN the courier goes online
- THEN the system MUST show the "Turn on location?" dialog, and MUST start publishing once the courier accepts

#### Scenario: Location turned off mid-delivery

- GIVEN a courier delivering an order in state `claimed` or `picked_up`
- WHEN the device location services are turned off
- THEN the system MUST ask the courier to turn them on and MUST keep showing a warning with a button that re-opens the dialog until they are on again, while the courier can still pick up and deliver

#### Scenario: Location restored

- GIVEN the warning is showing
- WHEN the device location services come back on
- THEN the system MUST remove the warning and MUST resume publishing without the courier restarting anything

### Requirement: Courier Delivery Map

The courier's active-delivery screen MUST show on one map the courier's own
current position, the pickup (store) pin and the dropoff pin. Before
`picked_up` the pickup MUST be emphasized as the destination (labelled "Pick
up here") and the camera MUST fit the courier and the pickup; from `picked_up`
the dropoff MUST be emphasized and the camera MUST fit the courier and the
dropoff. The map MUST NOT draw routes or give turn-by-turn navigation.

#### Scenario: Heading to the store

- GIVEN a courier with an order in state `claimed` and a known own position
- WHEN the courier views the active delivery
- THEN the map MUST show the courier, the pickup pin emphasized as "Pick up here" and the dropoff pin, framed around the courier and the pickup

#### Scenario: Heading to the customer

- GIVEN the courier marks the order `picked_up`
- WHEN the active delivery is shown
- THEN the dropoff pin MUST be emphasized, the pickup pin MUST no longer be, and the camera MUST fit the courier and the dropoff

#### Scenario: Own position unknown

- GIVEN the courier's position is unavailable (permission denied or no fix yet)
- WHEN the active delivery is shown
- THEN the map MUST still show the pickup and dropoff pins, with the destination emphasized, and no courier marker
