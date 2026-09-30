package com.otli.app.catalog.adapters.ui

import com.otli.app.catalog.domain.CatalogValidation
import com.otli.app.catalog.domain.PriceInput
import com.otli.app.catalog.domain.Product

enum class ProductEditorError { NAME_REQUIRED, PRICE_INVALID, CATEGORY_REQUIRED }

sealed interface ProductEditorResult {
    data class Valid(val product: Product) : ProductEditorResult

    data class Invalid(val errors: Set<ProductEditorError>) : ProductEditorResult
}

/**
 * The product form being edited. [productId] is blank for a new product; [photoVersion] and
 * [isAvailable] carry over from an existing product so an edit never resets them.
 * [pendingPhoto] holds the raw picked image until the product is saved.
 */
data class ProductEditorState(
    val productId: String = "",
    val categoryId: String = "",
    val name: String = "",
    val description: String = "",
    val priceText: String = "",
    val isAvailable: Boolean = true,
    val photoVersion: Int = 0,
    val pendingPhoto: ByteArray? = null,
    val errors: Set<ProductEditorError> = emptySet(),
) {
    val isNew: Boolean get() = productId.isBlank()

    /** Checks every field and either builds the product to save or lists every problem found. */
    fun validate(): ProductEditorResult {
        val price = PriceInput.parse(priceText)
        val problems = buildSet {
            if (CatalogValidation.validateName(name) != null) add(ProductEditorError.NAME_REQUIRED)
            if (price == null || CatalogValidation.validatePrice(price) != null) add(ProductEditorError.PRICE_INVALID)
            if (categoryId.isBlank()) add(ProductEditorError.CATEGORY_REQUIRED)
        }
        if (problems.isNotEmpty() || price == null) return ProductEditorResult.Invalid(problems)
        return ProductEditorResult.Valid(
            Product(productId, categoryId, name.trim(), description.trim(), price, isAvailable, photoVersion),
        )
    }

    companion object {
        fun forNew(categoryId: String) = ProductEditorState(categoryId = categoryId)

        fun from(product: Product) = ProductEditorState(
            productId = product.id,
            categoryId = product.categoryId,
            name = product.name,
            description = product.description,
            priceText = PriceInput.format(product.price),
            isAvailable = product.isAvailable,
            photoVersion = product.photoVersion,
        )
    }
}
