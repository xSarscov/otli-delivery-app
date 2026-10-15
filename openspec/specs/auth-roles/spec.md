# Auth & Roles Specification

## Purpose

Defines registration, login, role resolution, account status, and role-scoped
access for the four Otli actors: Customer, Merchant, Courier, Admin. This
capability is the entry gate for every other capability: no action in
merchant-catalog, ordering, courier-dispatch, live-tracking, or admin is
reachable without first resolving an authenticated, appropriately-statused
role.

## Requirements

### Requirement: Role-Scoped Registration

The system MUST allow a new user to register as exactly one role: Customer,
Merchant, or Courier. The system MUST NOT allow self-registration as Admin.

#### Scenario: Customer registers and becomes active immediately

- GIVEN a new user registers choosing the Customer role
- WHEN registration completes successfully
- THEN the account status is set to `active`
- AND the customer can immediately log in and use customer-facing features

#### Scenario: Merchant registers and starts pending

- GIVEN a new user registers choosing the Merchant role
- WHEN registration completes successfully
- THEN the account status is set to `pending`
- AND the merchant cannot access merchant-catalog management features until an Admin approves the account

#### Scenario: Merchant registers with store details

- GIVEN a new user registers choosing the Merchant role
- WHEN the user submits a store name, a contact phone (8 digits, optional +505 prefix) and a location pin on the map
- THEN the system MUST create the account and the merchant profile together, so the profile exists with its status `pending` and the store closed
- AND description, photo and hours are NOT collected at registration; the merchant edits them later in the merchant profile

#### Scenario: Merchant registration is refused without complete store details

- GIVEN a new user registering as a Merchant
- WHEN the store name, the contact phone or the map pin is missing, or the phone is not a valid Nicaraguan number
- THEN the system MUST NOT create the account
- AND the system MUST tell the user which detail to fix

#### Scenario: Courier registers and starts pending

- GIVEN a new user registers choosing the Courier role
- WHEN registration completes successfully
- THEN the account status is set to `pending`
- AND the courier cannot go online or view the courier pool until an Admin approves the account

#### Scenario: Admin accounts are never created through self-registration

- GIVEN the public registration flow
- WHEN a user attempts to register
- THEN the Admin role is not an offered or acceptable choice
- AND no request through the public registration flow MUST be able to create an `admin` role account

### Requirement: Role Resolution at Login

The system MUST resolve exactly one role for each authenticated session and
route the user to that role's home experience. The system MUST NOT expose UI
or actions belonging to a role other than the authenticated user's own role.

#### Scenario: Each role reaches its own home

- GIVEN a user with valid credentials and role R (Customer, Merchant, Courier, or Admin)
- WHEN the user logs in successfully
- THEN the system routes the user to the home experience defined for role R
- AND no actions or screens belonging to a different role are reachable from that session

#### Scenario: Invalid credentials are rejected

- GIVEN a user submits a login with incorrect credentials
- WHEN the login is attempted
- THEN the system MUST reject the attempt
- AND the system MUST NOT resolve or expose any role for that attempt

### Requirement: Account Status Gating

The system MUST enforce three account statuses — `active`, `pending`,
`suspended` — for Merchant and Courier roles, and MUST block role-specific
actions for any account that is not `active`. Customer accounts MUST be
`active` upon registration and are not subject to `pending` approval.

#### Scenario: Pending merchant or courier is blocked with a clear message

- GIVEN a Merchant or Courier account with status `pending`
- WHEN that user attempts to log in
- THEN the system MUST allow authentication to succeed
- AND the system MUST block access to role-specific actions (catalog management, going online, viewing the pool)
- AND the system MUST present a clear, role-appropriate message explaining the account is awaiting Admin approval

#### Scenario: Suspended account is blocked with a clear message

- GIVEN a Merchant or Courier account with status `suspended`
- WHEN that user attempts to log in
- THEN the system MUST block access to role-specific actions
- AND the system MUST present a clear message explaining the account has been suspended

#### Scenario: Active account has unrestricted role access

- GIVEN a Merchant or Courier account with status `active`
- WHEN that user logs in
- THEN the system MUST grant full access to that role's scoped actions

### Requirement: Role-Scoped Action Authorization

The system MUST authorize every state-changing action against the acting
user's role and, where applicable, ownership of the affected resource (for
example, a merchant may only manage their own catalog; a courier may only
progress an order they claimed).

#### Scenario: A user cannot perform another role's action

- GIVEN an authenticated user with role R
- WHEN that user attempts an action reserved for a different role
- THEN the system MUST reject the action
- AND the system MUST NOT apply any state change as a result of the attempt

#### Scenario: A user cannot act on a resource they do not own

- GIVEN an authenticated Merchant or Courier
- WHEN that user attempts to modify a resource (catalog item, order) that belongs to a different merchant or courier
- THEN the system MUST reject the action
