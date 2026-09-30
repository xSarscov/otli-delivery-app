package com.otli.app.catalog.adapters.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import com.otli.app.R
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// Tall screen: the form scrolls, and off-screen rows are not "displayed" for the assertions.
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h1600dp")
class ProductEditorFormTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun text(id: Int) = compose.activity.getString(id)

    private val categories = listOf(aCategory("c1", "Platos", 1), aCategory("c2", "Bebidas", 2))
    private val editing = ProductEditorState(
        productId = "p1",
        categoryId = "c1",
        name = "Nacatamal",
        description = "Cerdo y arroz",
        priceText = "120.50",
    )

    // A test may show several states in a row, so content is set once and driven by this state.
    private val current = mutableStateOf(MerchantCatalogUiState(isLoading = false, categories = categories, productEditor = editing))
    private var actions = MerchantCatalogActions()
    private var contentSet = false

    private fun show(
        editor: ProductEditorState = editing,
        isSaving: Boolean = false,
        actions: MerchantCatalogActions = MerchantCatalogActions(),
    ) {
        this.actions = actions
        current.value = MerchantCatalogUiState(isLoading = false, categories = categories, productEditor = editor, isSaving = isSaving)
        if (contentSet) return
        contentSet = true
        compose.setContent {
            MerchantCatalogContent(
                state = current.value,
                actions = MerchantCatalogActions(
                    onProductNameChange = { this.actions.onProductNameChange(it) },
                    onProductDescriptionChange = { this.actions.onProductDescriptionChange(it) },
                    onProductPriceChange = { this.actions.onProductPriceChange(it) },
                    onProductCategoryChange = { this.actions.onProductCategoryChange(it) },
                    onPickProductPhoto = { this.actions.onPickProductPhoto() },
                    onSaveProduct = { this.actions.onSaveProduct() },
                    onDismissProductEditor = { this.actions.onDismissProductEditor() },
                ),
            )
        }
    }

    @Test
    fun anExistingProductPrefillsTheFormAndReplacesTheList() {
        show()

        compose.onNodeWithText(text(R.string.product_editor_title_edit)).assertIsDisplayed()
        compose.onNodeWithText("Nacatamal").assertIsDisplayed()
        compose.onNodeWithText("Cerdo y arroz").assertIsDisplayed()
        compose.onNodeWithText("120.50").assertIsDisplayed()
        compose.onNodeWithText(text(R.string.action_add_category)).assertDoesNotExist()
    }

    @Test
    fun aNewProductShowsTheNewTitle() {
        show(ProductEditorState.forNew("c2"))

        compose.onNodeWithText(text(R.string.product_editor_title_new)).assertIsDisplayed()
    }

    @Test
    fun everyCategoryIsOfferedAndChoosingOneIsReported() {
        var chosen: String? = null
        show(actions = MerchantCatalogActions(onProductCategoryChange = { chosen = it }))

        compose.onNodeWithText("Platos").assertIsDisplayed()
        compose.onNodeWithText("Bebidas").performClick()

        assertThat(chosen).isEqualTo("c2")
    }

    @Test
    fun saveCancelAndPhotoInvokeTheirCallbacks() {
        var saved = false
        var cancelled = false
        var picked = false
        show(
            actions = MerchantCatalogActions(
                onSaveProduct = { saved = true },
                onDismissProductEditor = { cancelled = true },
                onPickProductPhoto = { picked = true },
            ),
        )

        compose.onNodeWithText(text(R.string.action_save)).performClick()
        compose.onNodeWithText(text(R.string.action_cancel)).performClick()
        compose.onNodeWithText(text(R.string.action_choose_photo)).performClick()

        assertThat(saved).isTrue()
        assertThat(cancelled).isTrue()
        assertThat(picked).isTrue()
    }

    @Test
    fun everyValidationErrorIsShownNextToTheForm() {
        show(editing.copy(errors = ProductEditorError.entries.toSet()))

        compose.onNodeWithText(text(R.string.product_error_name)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.product_error_price)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.product_error_category)).assertIsDisplayed()
    }

    @Test
    fun theFormShowsNoErrorTextWhenThereAreNone() {
        show()

        compose.onNodeWithText(text(R.string.product_error_name)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.product_error_price)).assertDoesNotExist()
    }

    @Test
    fun thePhotoStatusFollowsThePendingAndSavedPhoto() {
        show(editing)
        compose.onNodeWithText(text(R.string.merchant_photo_none)).assertIsDisplayed()

        show(editing.copy(photoVersion = 2))
        compose.onNodeWithText(text(R.string.product_photo_saved)).assertIsDisplayed()

        show(editing.copy(photoVersion = 2, pendingPhoto = byteArrayOf(1)))
        compose.onNodeWithText(text(R.string.product_photo_ready)).assertIsDisplayed()
    }

    @Test
    fun saveIsDisabledWhileSaving() {
        show(isSaving = true)

        compose.onNodeWithText(text(R.string.action_save)).assertIsNotEnabled()
    }
}
