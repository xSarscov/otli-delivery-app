package com.otli.app.catalog.adapters.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import com.otli.app.R
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h1200dp")
class MerchantListContentTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun text(id: Int) = compose.activity.getString(id)

    private fun show(state: MerchantListUiState, onOpen: (String) -> Unit = {}) {
        compose.setContent { MerchantListContent(state = state, onMerchantClick = onOpen) }
    }

    private val loaded = MerchantListUiState(
        isLoading = false,
        merchants = listOf(
            aMerchant("m1", "Comedor Marta", description = "Comida tipica", isOpen = true),
            aMerchant("m2", "Pulperia Sol", description = "Abarrotes", isOpen = false),
        ),
    )

    @Test
    fun showsEachMerchantNameAndDescription() {
        show(loaded)

        compose.onNodeWithText("Comedor Marta").assertIsDisplayed()
        compose.onNodeWithText("Comida tipica").assertIsDisplayed()
        compose.onNodeWithText("Pulperia Sol").assertIsDisplayed()
        compose.onNodeWithText("Abarrotes").assertIsDisplayed()
    }

    @Test
    fun marksOpenAndClosedMerchantsDifferently() {
        show(loaded)

        assertThat(compose.onAllNodesWithText(text(R.string.merchant_status_open)).fetchSemanticsNodes()).hasSize(1)
        assertThat(compose.onAllNodesWithText(text(R.string.merchant_status_closed)).fetchSemanticsNodes()).hasSize(1)
    }

    @Test
    fun tappingAMerchantOpensItsStorefront() {
        val opened = mutableListOf<String>()
        show(loaded, onOpen = { opened += it })

        compose.onNodeWithText("Pulperia Sol").performClick()

        assertThat(opened).containsExactly("m2")
    }

    @Test
    fun showsAProgressIndicatorWhileLoading() {
        show(MerchantListUiState(isLoading = true))

        compose.onNodeWithTag(MerchantListTags.LOADING).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.merchant_list_empty)).assertDoesNotExist()
    }

    @Test
    fun explainsAnEmptyListOnceLoaded() {
        show(MerchantListUiState(isLoading = false))

        compose.onNodeWithText(text(R.string.merchant_list_empty)).assertIsDisplayed()
        compose.onNodeWithTag(MerchantListTags.LOADING).assertDoesNotExist()
    }

    @Test
    fun explainsALoadFailure() {
        show(MerchantListUiState(isLoading = false, loadFailed = true))

        compose.onNodeWithText(text(R.string.merchant_list_error)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.merchant_list_empty)).assertDoesNotExist()
    }
}
