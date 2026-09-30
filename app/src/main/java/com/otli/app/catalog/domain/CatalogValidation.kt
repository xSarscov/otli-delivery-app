package com.otli.app.catalog.domain

import com.otli.app.core.money.Money

enum class CatalogValidationError { BLANK_NAME, NON_POSITIVE_PRICE }

/**
 * Pure client-side checks run before anything is written. The Firestore rules enforce the
 * price floor server-side (`priceCents > 0`); keep both in sync.
 */
object CatalogValidation {
    fun validateName(name: String): CatalogValidationError? =
        if (name.isBlank()) CatalogValidationError.BLANK_NAME else null

    fun validatePrice(price: Money): CatalogValidationError? =
        if (price.centavos > 0) null else CatalogValidationError.NON_POSITIVE_PRICE

    /** Returns every problem found; an empty list means the product is valid. */
    fun validateProduct(name: String, price: Money): List<CatalogValidationError> =
        listOfNotNull(validateName(name), validatePrice(price))
}
