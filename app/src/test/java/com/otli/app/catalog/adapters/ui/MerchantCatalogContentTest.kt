package com.otli.app.catalog.adapters.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import com.otli.app.R
import com.otli.app.catalog.domain.Category
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// Tall screen: the list scrolls, and off-screen rows are not "displayed" for the assertions.
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h1600dp")
class MerchantCatalogContentTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun text(id: Int) = compose.activity.getString(id)

    private val loaded = MerchantCatalogUiState(
        isLoading = false,
        categories = listOf(aCategory("c1", "Platos", 1), aCategory("c2", "Bebidas", 2)),
        products = listOf(
            aProduct("p1", "c1", "Nacatamal", 12050),
            aProduct("p2", "c2", "Fresco", 2500, isAvailable = false),
        ),
    )

    // A test may show several states in a row, so content is set once and driven by this state.
    private val current = mutableStateOf(loaded)
    private var actions = MerchantCatalogActions()
    private var contentSet = false

    private fun show(state: MerchantCatalogUiState, actions: MerchantCatalogActions = MerchantCatalogActions()) {
        this.actions = actions
        current.value = state
        if (contentSet) return
        contentSet = true
        compose.setContent {
            MerchantCatalogContent(state = current.value, actions = MerchantCatalogActions(
                onAddCategory = { this.actions.onAddCategory() },
                onEditCategory = { this.actions.onEditCategory(it) },
                onRemoveCategory = { this.actions.onRemoveCategory(it) },
                onAddProduct = { this.actions.onAddProduct(it) },
                onEditProduct = { this.actions.onEditProduct(it) },
                onRemoveProduct = { this.actions.onRemoveProduct(it) },
                onAvailabilityChange = { id, available -> this.actions.onAvailabilityChange(id, available) },
                onDismissError = { this.actions.onDismissError() },
                onCategoryNameChange = { this.actions.onCategoryNameChange(it) },
                onSaveCategory = { this.actions.onSaveCategory() },
                onDismissCategoryEditor = { this.actions.onDismissCategoryEditor() },
            ))
        }
    }

    @Test
    fun groupsProductsUnderTheirCategoriesWithNioPrices() {
        show(loaded)

        compose.onNodeWithText("Platos").assertIsDisplayed()
        compose.onNodeWithText("Bebidas").assertIsDisplayed()
        compose.onNodeWithText("Nacatamal").assertIsDisplayed()
        compose.onNodeWithText("C$ 120.50").assertIsDisplayed()
        compose.onNodeWithText("C$ 25.00").assertIsDisplayed()
    }

    @Test
    fun aCategoryWithoutProductsSaysSo() {
        show(loaded.copy(products = emptyList()))

        assertThat(compose.onAllNodesWithText(text(R.string.category_empty)).fetchSemanticsNodes()).hasSize(2)
    }

    @Test
    fun withoutCategoriesItInvitesTheMerchantToCreateOne() {
        show(loaded.copy(categories = emptyList(), products = emptyList()))

        compose.onNodeWithText(text(R.string.merchant_catalog_empty)).assertIsDisplayed()
    }

    @Test
    fun whileLoadingItShowsNoCatalogControls() {
        show(MerchantCatalogUiState(isLoading = true))

        compose.onNodeWithText(text(R.string.action_add_category)).assertDoesNotExist()
    }

    @Test
    fun theAvailabilitySwitchReportsTheOppositeOfTheCurrentValue() {
        val changes = mutableListOf<Pair<String, Boolean>>()
        show(loaded, MerchantCatalogActions(onAvailabilityChange = { id, on -> changes += id to on }))

        compose.onNodeWithContentDescription(text(R.string.label_available) + " Nacatamal").performClick()
        compose.onNodeWithContentDescription(text(R.string.label_available) + " Fresco").performClick()

        assertThat(changes).containsExactly("p1" to false, "p2" to true).inOrder()
    }

    @Test
    fun addAndEditAndDeleteActionsCarryTheRightTargets() {
        var addedCategory = false
        var editedCategory: Category? = null
        var removedCategory: String? = null
        var addedProductIn: String? = null
        var editedProduct: String? = null
        var removedProduct: String? = null
        show(
            loaded,
            MerchantCatalogActions(
                onAddCategory = { addedCategory = true },
                onEditCategory = { editedCategory = it },
                onRemoveCategory = { removedCategory = it },
                onAddProduct = { addedProductIn = it },
                onEditProduct = { editedProduct = it.id },
                onRemoveProduct = { removedProduct = it },
            ),
        )

        compose.onNodeWithText(text(R.string.action_add_category)).performClick()
        compose.onNodeWithContentDescription(text(R.string.action_edit) + " Bebidas").performClick()
        compose.onNodeWithContentDescription(text(R.string.action_delete) + " Bebidas").performClick()
        compose.onNodeWithContentDescription(text(R.string.action_add_product) + " Platos").performClick()
        compose.onNodeWithContentDescription(text(R.string.action_edit) + " Nacatamal").performClick()
        compose.onNodeWithContentDescription(text(R.string.action_delete) + " Nacatamal").performClick()

        assertThat(addedCategory).isTrue()
        assertThat(editedCategory?.id).isEqualTo("c2")
        assertThat(removedCategory).isEqualTo("c2")
        assertThat(addedProductIn).isEqualTo("c1")
        assertThat(editedProduct).isEqualTo("p1")
        assertThat(removedProduct).isEqualTo("p1")
    }

    @Test
    fun showsAMessageForEachErrorAndDismissingItInvokesTheCallback() {
        var dismissed = false
        val cases = mapOf(
            MerchantCatalogError.NEEDS_CATEGORY to R.string.merchant_catalog_error_needs_category,
            MerchantCatalogError.CATEGORY_NOT_EMPTY to R.string.merchant_catalog_error_category_not_empty,
            MerchantCatalogError.SAVE_FAILED to R.string.merchant_catalog_error_save,
            MerchantCatalogError.DELETE_FAILED to R.string.merchant_catalog_error_delete,
            MerchantCatalogError.AVAILABILITY_FAILED to R.string.merchant_catalog_error_availability,
            MerchantCatalogError.PHOTO_FAILED to R.string.merchant_catalog_error_photo,
            MerchantCatalogError.LOAD_FAILED to R.string.merchant_catalog_error_load,
        )
        for ((error, message) in cases) {
            show(loaded.copy(error = error), MerchantCatalogActions(onDismissError = { dismissed = true }))
            compose.onNodeWithText(text(message)).assertIsDisplayed()
        }

        compose.onNodeWithText(text(R.string.action_dismiss)).performClick()
        assertThat(dismissed).isTrue()
    }

    @Test
    fun theCategoryEditorShowsTheNameAndReportsSaveCancelAndMissingName() {
        var saved = false
        var cancelled = false
        show(
            loaded.copy(categoryEditor = CategoryEditorState(name = "Postres", nameMissing = true)),
            MerchantCatalogActions(onSaveCategory = { saved = true }, onDismissCategoryEditor = { cancelled = true }),
        )

        compose.onNodeWithText("Postres").assertIsDisplayed()
        compose.onNodeWithText(text(R.string.category_name_required)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.action_save)).performClick()
        compose.onNodeWithText(text(R.string.action_cancel)).performClick()

        assertThat(saved).isTrue()
        assertThat(cancelled).isTrue()
    }
}
