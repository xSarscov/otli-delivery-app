package com.otli.app.catalog.adapters.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.otli.app.R
import com.otli.app.auth.domain.AccountStatus
import com.otli.app.catalog.application.PhotoKey
import com.otli.app.catalog.domain.Merchant
import com.otli.app.core.theme.OtliTheme

object MerchantListTags {
    const val LOADING = "merchant-list-loading"
}

/** Stateless customer merchant list; tapping a row opens that merchant's storefront. */
@Composable
fun MerchantListContent(
    state: MerchantListUiState,
    onMerchantClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    when {
        state.isLoading -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(Modifier.testTag(MerchantListTags.LOADING))
        }
        state.loadFailed -> Message(R.string.merchant_list_error, modifier)
        state.merchants.isEmpty() -> Message(R.string.merchant_list_empty, modifier)
        else -> LazyColumn(
            modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(stringResource(R.string.merchant_list_title), style = MaterialTheme.typography.headlineMedium)
            }
            items(state.merchants, key = { it.id }) { merchant ->
                MerchantRow(merchant, onClick = { onMerchantClick(merchant.id) })
            }
        }
    }
}

@Composable
private fun Message(message: Int, modifier: Modifier) {
    Box(modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(stringResource(message), style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun MerchantRow(merchant: Merchant, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StorePhoto(
                key = PhotoKey.merchantProfile(merchant.id, merchant.photoVersion),
                modifier = Modifier.size(64.dp).clip(RoundedCornerShape(8.dp)),
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(merchant.name, style = MaterialTheme.typography.titleMedium)
                if (merchant.description.isNotBlank()) {
                    Text(
                        merchant.description,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                OpenClosedLabel(merchant.isOpen)
            }
        }
    }
}

/** The text carries the state (color only reinforces it), so it stays readable for everyone. */
@Composable
internal fun OpenClosedLabel(isOpen: Boolean, modifier: Modifier = Modifier) {
    Text(
        stringResource(if (isOpen) R.string.merchant_status_open else R.string.merchant_status_closed),
        modifier = modifier,
        style = MaterialTheme.typography.labelLarge,
        color = if (isOpen) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error,
    )
}

@Preview(showBackground = true)
@Composable
private fun MerchantListPreview() {
    OtliTheme {
        MerchantListContent(
            state = MerchantListUiState(
                isLoading = false,
                merchants = listOf(
                    Merchant("m1", "Comedor Marta", "Comida tipica", "", AccountStatus.ACTIVE, true, 0, null),
                    Merchant("m2", "Pulperia Sol", "Abarrotes", "", AccountStatus.ACTIVE, false, 0, null),
                ),
            ),
            onMerchantClick = {},
        )
    }
}
