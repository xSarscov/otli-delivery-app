package com.otli.app.catalog.adapters.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.otli.app.R
import com.otli.app.auth.domain.AccountStatus
import com.otli.app.catalog.application.PhotoKey
import com.otli.app.catalog.domain.Category
import com.otli.app.catalog.domain.Merchant
import com.otli.app.catalog.domain.PriceInput
import com.otli.app.catalog.domain.Product
import com.otli.app.core.money.Money
import com.otli.app.core.theme.OtliTheme
import com.otli.app.core.ui.OtliTopBar

object StorefrontTags {
    const val LOADING = "storefront-loading"
    const val CLOSED_BANNER = "storefront-closed-banner"

    fun add(productId: String) = "storefront-add-$productId"
}

private const val UNAVAILABLE_ALPHA = 0.5f

/**
 * Stateless storefront. The add buttons obey [StorefrontItem.canAddToCart] only; the cart itself
 * arrives in Slice 3 and simply supplies [onAddToCart].
 */
@Composable
fun StorefrontContent(
    state: StorefrontUiState,
    onAddToCart: (Product) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val merchant = state.merchant
    Column(modifier.fillMaxSize()) {
        // The bar is always there, so the user can leave while loading or after an error.
        OtliTopBar(title = merchant?.name.orEmpty(), onBack = onBack, actions = actions)
        when {
            state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.testTag(StorefrontTags.LOADING))
            }
            state.loadFailed -> Message(R.string.storefront_error)
            state.notFound || merchant == null -> Message(R.string.storefront_not_found)
            else -> LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item { Header(merchant) }
                if (state.isClosed) item { ClosedBanner() }
                for (section in state.sections) {
                    item(key = "category-${section.category.id}") {
                        Text(section.category.name, style = MaterialTheme.typography.titleLarge)
                    }
                    items(section.items.size, key = { section.items[it].product.id }) { index ->
                        ProductRow(merchant.id, section.items[index], onAddToCart)
                    }
                }
            }
        }
    }
}

@Composable
private fun Message(message: Int) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(stringResource(message), style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun Header(merchant: Merchant) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        StorePhoto(
            key = PhotoKey.merchantProfile(merchant.id, merchant.photoVersion),
            modifier = Modifier.fillMaxWidth().height(160.dp).clip(RoundedCornerShape(12.dp)),
        )
        if (merchant.description.isNotBlank()) {
            Text(merchant.description, style = MaterialTheme.typography.bodyMedium)
        }
        OpenClosedLabel(merchant.isOpen)
    }
}

@Composable
private fun ClosedBanner() {
    Card(
        Modifier.fillMaxWidth().testTag(StorefrontTags.CLOSED_BANNER),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Text(
            stringResource(R.string.storefront_closed_banner),
            Modifier.padding(12.dp),
            color = MaterialTheme.colorScheme.onErrorContainer,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun ProductRow(merchantId: String, item: StorefrontItem, onAddToCart: (Product) -> Unit) {
    val product = item.product
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            val dim = if (product.isAvailable) 1f else UNAVAILABLE_ALPHA
            StorePhoto(
                key = PhotoKey.product(merchantId, product.id, product.photoVersion),
                modifier = Modifier.size(72.dp).clip(RoundedCornerShape(8.dp)).alpha(dim),
            )
            Column(Modifier.weight(1f).alpha(dim), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(product.name, style = MaterialTheme.typography.titleMedium)
                if (product.description.isNotBlank()) {
                    Text(product.description, style = MaterialTheme.typography.bodyMedium)
                }
                Text(stringResource(R.string.price_nio, PriceInput.format(product.price)))
                if (!product.isAvailable) {
                    Text(
                        stringResource(R.string.product_unavailable),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            Button(
                onClick = { onAddToCart(product) },
                enabled = item.canAddToCart,
                modifier = Modifier.testTag(StorefrontTags.add(product.id)),
            ) { Text(stringResource(R.string.storefront_add)) }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun StorefrontClosedPreview() {
    val platos = Category("c1", "Platos", 1)
    OtliTheme {
        StorefrontContent(
            state = StorefrontUiState(
                isLoading = false,
                merchant = Merchant("m1", "Comedor Marta", "Comida tipica", "", AccountStatus.ACTIVE, false, 0, null),
                sections = listOf(
                    StorefrontSection(
                        platos,
                        listOf(
                            StorefrontItem(Product("p1", "c1", "Nacatamal", "", Money(12000), true, 0), false),
                            StorefrontItem(Product("p2", "c1", "Vigoron", "", Money(8000), false, 0), false),
                        ),
                    ),
                ),
            ),
            onAddToCart = {},
            onBack = {},
        )
    }
}
