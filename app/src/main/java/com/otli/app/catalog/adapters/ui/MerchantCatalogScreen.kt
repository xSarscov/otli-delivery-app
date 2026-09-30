package com.otli.app.catalog.adapters.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Container: wires [MerchantCatalogViewModel] and the system image picker to the stateless content. */
@Composable
fun MerchantCatalogScreen(
    modifier: Modifier = Modifier,
    viewModel: MerchantCatalogViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            scope.launch {
                val bytes = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                }
                if (bytes != null) viewModel.onProductPhotoPicked(bytes)
            }
        }
    }
    MerchantCatalogContent(
        state = state,
        actions = MerchantCatalogActions(
            onAddCategory = viewModel::onAddCategory,
            onEditCategory = viewModel::onEditCategory,
            onRemoveCategory = viewModel::onRemoveCategory,
            onAddProduct = viewModel::onAddProduct,
            onEditProduct = viewModel::onEditProduct,
            onRemoveProduct = viewModel::onRemoveProduct,
            onAvailabilityChange = viewModel::onAvailabilityChange,
            onDismissError = viewModel::onDismissError,
            onCategoryNameChange = viewModel::onCategoryNameChange,
            onSaveCategory = viewModel::onSaveCategory,
            onDismissCategoryEditor = viewModel::onDismissCategoryEditor,
            onProductNameChange = viewModel::onProductNameChange,
            onProductDescriptionChange = viewModel::onProductDescriptionChange,
            onProductPriceChange = viewModel::onProductPriceChange,
            onProductCategoryChange = viewModel::onProductCategoryChange,
            onPickProductPhoto = { picker.launch("image/*") },
            onSaveProduct = viewModel::onSaveProduct,
            onDismissProductEditor = viewModel::onDismissProductEditor,
        ),
        modifier = modifier,
    )
}
