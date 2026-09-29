# Merchant Catalog Specification

## Purpose

Defines merchant profile, category, and product management, store
open/closed availability, and the customer-facing browsing surface built on
top of that catalog. Depends on `auth-roles` for merchant identity and
account-status gating.

## Requirements

### Requirement: Merchant Profile Management

The system MUST allow an `active` Merchant to manage exactly one merchant
profile (name, description, photo) that identifies their storefront to
customers.

#### Scenario: Active merchant edits their profile

- GIVEN an `active` Merchant account
- WHEN the merchant updates their profile name, description, or photo
- THEN the system MUST persist the change
- AND the updated profile MUST be visible to customers browsing that merchant

### Requirement: Category and Product Management

The system MUST allow an `active` Merchant to create, edit, and remove
categories and products scoped to their own merchant profile. Each product
MUST have a name, a price expressed in NIO (Nicaraguan Córdoba), and an
availability flag. A product MAY have a photo.

#### Scenario: Merchant creates a product with a NIO price

- GIVEN an `active` Merchant managing their catalog
- WHEN the merchant creates a product with a name and a price in NIO
- THEN the system MUST persist the product under the merchant's catalog
- AND the price MUST be stored and displayed as a NIO amount

#### Scenario: Merchant marks a product unavailable

- GIVEN an existing product belonging to an `active` Merchant
- WHEN the merchant toggles the product's availability to unavailable
- THEN the system MUST reflect the unavailable state immediately to any customer browsing that catalog
- AND the product MUST NOT be addable to a cart while unavailable

#### Scenario: Merchant cannot manage another merchant's catalog

- GIVEN an `active` Merchant account
- WHEN that merchant attempts to create, edit, or remove a category or product belonging to a different merchant
- THEN the system MUST reject the action

### Requirement: Store Open/Closed Toggle

The system MUST allow an `active` Merchant to toggle their store between
`open` and `closed`. While `closed`, the merchant MUST NOT receive new
orders.

#### Scenario: Merchant closes the store

- GIVEN an `active` Merchant with an `open` store
- WHEN the merchant toggles the store to `closed`
- THEN the system MUST mark the store as closed for all customers immediately
- AND no new order MUST be placeable against that merchant while closed

#### Scenario: Customer sees a closed store is unavailable for ordering

- GIVEN a merchant whose store is `closed`
- WHEN a customer opens that merchant's storefront
- THEN the system MUST clearly indicate the store is closed
- AND the system MUST prevent adding any of that merchant's products to a cart while closed

### Requirement: Customer Browsing

The system MUST allow an authenticated Customer to browse merchants and
their available products. The system MUST NOT present unavailable products
as addable to a cart.

#### Scenario: Customer browses an open merchant's available products

- GIVEN a merchant with status `open` and at least one available product
- WHEN a customer opens that merchant's storefront
- THEN the system MUST list the merchant's categories and available products
- AND unavailable products, if shown at all, MUST be visually distinguished and MUST NOT be addable to the cart

#### Scenario: Customer browses merchants list

- GIVEN one or more merchants exist in the system
- WHEN a customer opens the browsing screen
- THEN the system MUST list merchants
- AND the system MUST indicate each merchant's open/closed status
