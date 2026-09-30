package com.otli.app.catalog.adapters.ui

import com.google.common.truth.Truth.assertThat
import com.otli.app.catalog.domain.Product
import com.otli.app.core.money.Money
import org.junit.Test

class ProductEditorStateTest {
    private val filled = ProductEditorState.forNew(categoryId = "cat-1")
        .copy(name = "  Nacatamal ", description = " Cerdo ", priceText = "120,50")

    @Test
    fun aNewEditorStartsBlankAvailableAndTiedToItsCategory() {
        val editor = ProductEditorState.forNew("cat-1")

        assertThat(editor.productId).isEmpty()
        assertThat(editor.categoryId).isEqualTo("cat-1")
        assertThat(editor.isAvailable).isTrue()
        assertThat(editor.photoVersion).isEqualTo(0)
        assertThat(editor.isNew).isTrue()
    }

    @Test
    fun editingAnExistingProductPrefillsEveryField() {
        val product = Product("p1", "cat-2", "Vigoron", "Yuca", Money(8000), false, 3)

        val editor = ProductEditorState.from(product)

        assertThat(editor.productId).isEqualTo("p1")
        assertThat(editor.categoryId).isEqualTo("cat-2")
        assertThat(editor.name).isEqualTo("Vigoron")
        assertThat(editor.description).isEqualTo("Yuca")
        assertThat(editor.priceText).isEqualTo("80.00")
        assertThat(editor.isAvailable).isFalse()
        assertThat(editor.photoVersion).isEqualTo(3)
        assertThat(editor.isNew).isFalse()
    }

    @Test
    fun aValidEditorBuildsATrimmedProductWithTheParsedPrice() {
        val result = filled.validate()

        assertThat(result).isInstanceOf(ProductEditorResult.Valid::class.java)
        val product = (result as ProductEditorResult.Valid).product
        assertThat(product).isEqualTo(Product("", "cat-1", "Nacatamal", "Cerdo", Money(12050), true, 0))
    }

    @Test
    fun editingKeepsTheIdAvailabilityAndPhotoVersionOfTheExistingProduct() {
        val existing = Product("p1", "cat-2", "Vigoron", "Yuca", Money(8000), false, 3)

        val result = ProductEditorState.from(existing).copy(name = "Vigoron grande").validate()

        assertThat((result as ProductEditorResult.Valid).product)
            .isEqualTo(existing.copy(name = "Vigoron grande"))
    }

    @Test
    fun aBlankNameIsReported() {
        val result = filled.copy(name = "   ").validate()

        assertThat((result as ProductEditorResult.Invalid).errors).containsExactly(ProductEditorError.NAME_REQUIRED)
    }

    @Test
    fun anUnparseableOrZeroPriceIsReported() {
        for (price in listOf("", "abc", "0", "0.00", "-3")) {
            val result = filled.copy(priceText = price).validate()

            assertThat((result as ProductEditorResult.Invalid).errors).containsExactly(ProductEditorError.PRICE_INVALID)
        }
    }

    @Test
    fun aMissingCategoryIsReported() {
        val result = filled.copy(categoryId = "").validate()

        assertThat((result as ProductEditorResult.Invalid).errors).containsExactly(ProductEditorError.CATEGORY_REQUIRED)
    }

    @Test
    fun everyProblemIsReportedAtOnce() {
        val result = ProductEditorState().validate()

        assertThat((result as ProductEditorResult.Invalid).errors).containsExactly(
            ProductEditorError.NAME_REQUIRED,
            ProductEditorError.PRICE_INVALID,
            ProductEditorError.CATEGORY_REQUIRED,
        )
    }
}
