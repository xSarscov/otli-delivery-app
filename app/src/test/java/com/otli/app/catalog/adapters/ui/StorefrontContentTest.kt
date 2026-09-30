package com.otli.app.catalog.adapters.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import com.otli.app.R
import com.otli.app.catalog.domain.Product
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2400dp")
class StorefrontContentTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun text(id: Int) = compose.activity.getString(id)

    private val platos = aCategory("c1", "Platos", 1)
    private val bebidas = aCategory("c2", "Bebidas", 2)
    private val nacatamal = aProduct("p1", "c1", "Nacatamal", 12050)
    private val vigoron = aProduct("p2", "c1", "Vigoron", 8000, isAvailable = false)
    private val fresco = aProduct("p3", "c2", "Fresco", 2500)

    private fun stateOf(open: Boolean) = StorefrontUiState(
        isLoading = false,
        merchant = aMerchant("m1", "Comedor Marta", "Comida tipica", isOpen = open),
        sections = listOf(
            StorefrontSection(platos, listOf(item(nacatamal, open), item(vigoron, open))),
            StorefrontSection(bebidas, listOf(item(fresco, open))),
        ),
    )

    private fun item(product: Product, storeOpen: Boolean) =
        StorefrontItem(product, canAddToCart = storeOpen && product.isAvailable)

    private fun show(state: StorefrontUiState, onAdd: (Product) -> Unit = {}) {
        compose.setContent { StorefrontContent(state = state, onAddToCart = onAdd) }
    }

    @Test
    fun showsTheMerchantCategoriesAndProductsWithTheirPrices() {
        show(stateOf(open = true))

        compose.onNodeWithText("Comedor Marta").assertIsDisplayed()
        compose.onNodeWithText("Comida tipica").assertIsDisplayed()
        compose.onNodeWithText("Platos").assertIsDisplayed()
        compose.onNodeWithText("Bebidas").assertIsDisplayed()
        compose.onNodeWithText("Nacatamal").assertIsDisplayed()
        compose.onNodeWithText("Fresco").assertIsDisplayed()
        compose.onNodeWithText(compose.activity.getString(R.string.price_nio, "120.50")).assertIsDisplayed()
        compose.onNodeWithText(compose.activity.getString(R.string.price_nio, "25.00")).assertIsDisplayed()
    }

    @Test
    fun anOpenStoreShowsNoClosedBannerAndAvailableProductsCanBeAdded() {
        val added = mutableListOf<Product>()
        show(stateOf(open = true), onAdd = { added += it })

        compose.onNodeWithTag(StorefrontTags.CLOSED_BANNER).assertDoesNotExist()
        compose.onNodeWithTag(StorefrontTags.add("p1")).assertIsEnabled().performClick()
        compose.onNodeWithTag(StorefrontTags.add("p3")).assertIsEnabled()

        assertThat(added).containsExactly(nacatamal)
    }

    @Test
    fun anUnavailableProductIsLabelledAndCannotBeAdded() {
        val added = mutableListOf<Product>()
        show(stateOf(open = true), onAdd = { added += it })

        assertThat(compose.onAllNodesWithText(text(R.string.product_unavailable)).fetchSemanticsNodes()).hasSize(1)
        compose.onNodeWithText("Vigoron").assertIsDisplayed()
        compose.onNodeWithTag(StorefrontTags.add("p2")).assertIsNotEnabled().performClick()

        assertThat(added).isEmpty()
    }

    @Test
    fun aClosedStoreShowsTheBannerAndBlocksEveryAddButton() {
        show(stateOf(open = false))

        compose.onNodeWithTag(StorefrontTags.CLOSED_BANNER).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.storefront_closed_banner)).assertIsDisplayed()
        for (id in listOf("p1", "p2", "p3")) {
            compose.onNodeWithTag(StorefrontTags.add(id)).assertIsNotEnabled()
        }
    }

    @Test
    fun showsAProgressIndicatorWhileLoading() {
        show(StorefrontUiState(isLoading = true))

        compose.onNodeWithTag(StorefrontTags.LOADING).assertIsDisplayed()
    }

    @Test
    fun explainsAMissingStore() {
        show(StorefrontUiState(isLoading = false, notFound = true))

        compose.onNodeWithText(text(R.string.storefront_not_found)).assertIsDisplayed()
        compose.onNodeWithTag(StorefrontTags.LOADING).assertDoesNotExist()
    }

    @Test
    fun explainsALoadFailure() {
        show(StorefrontUiState(isLoading = false, loadFailed = true))

        compose.onNodeWithText(text(R.string.storefront_error)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.storefront_not_found)).assertDoesNotExist()
    }
}
