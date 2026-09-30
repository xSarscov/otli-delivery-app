package com.otli.app.catalog.adapters.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.otli.app.R
import com.otli.app.catalog.domain.Category
import com.otli.app.catalog.domain.PriceInput
import com.otli.app.catalog.domain.Product

/** Everything the catalog screen can ask for; all default to no-ops so previews and tests override only what they need. */
data class MerchantCatalogActions(
    val onAddCategory: () -> Unit = {},
    val onEditCategory: (Category) -> Unit = {},
    val onRemoveCategory: (String) -> Unit = {},
    val onAddProduct: (String) -> Unit = {},
    val onEditProduct: (Product) -> Unit = {},
    val onRemoveProduct: (String) -> Unit = {},
    val onAvailabilityChange: (String, Boolean) -> Unit = { _, _ -> },
    val onDismissError: () -> Unit = {},
    val onCategoryNameChange: (String) -> Unit = {},
    val onSaveCategory: () -> Unit = {},
    val onDismissCategoryEditor: () -> Unit = {},
    val onProductNameChange: (String) -> Unit = {},
    val onProductDescriptionChange: (String) -> Unit = {},
    val onProductPriceChange: (String) -> Unit = {},
    val onProductCategoryChange: (String) -> Unit = {},
    val onPickProductPhoto: () -> Unit = {},
    val onSaveProduct: () -> Unit = {},
    val onDismissProductEditor: () -> Unit = {},
)

/** Stateless catalog manager: categories with their products, availability switches and the category and product editors. */
@Composable
fun MerchantCatalogContent(
    state: MerchantCatalogUiState,
    actions: MerchantCatalogActions,
    modifier: Modifier = Modifier,
) {
    if (state.isLoading) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    state.productEditor?.let { editor ->
        ProductEditorForm(editor, state.categories, state.isSaving, actions, modifier)
        return
    }
    LazyColumn(modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Text(
                stringResource(R.string.merchant_catalog_title),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(top = 24.dp),
            )
        }
        state.error?.let { error ->
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(error.messageRes()),
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = actions.onDismissError) { Text(stringResource(R.string.action_dismiss)) }
                }
            }
        }
        if (state.categoryEditor == null) {
            item {
                OutlinedButton(onClick = actions.onAddCategory, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.action_add_category))
                }
            }
        } else {
            item(key = "category-editor") { CategoryEditor(state.categoryEditor, actions) }
        }
        if (state.categories.isEmpty()) {
            item { Text(stringResource(R.string.merchant_catalog_empty)) }
        }
        for (category in state.categories) {
            val products = state.products.filter { it.categoryId == category.id }
            item(key = "category-${category.id}") { CategoryHeader(category, actions) }
            if (products.isEmpty()) {
                item(key = "empty-${category.id}") { Text(stringResource(R.string.category_empty)) }
            }
            items(products, key = { "product-${it.id}" }) { product -> ProductRow(product, actions) }
            item(key = "divider-${category.id}") { HorizontalDivider() }
        }
    }
}

/** Gives a row action a screen-reader label that names its target, e.g. "Edit Bebidas". */
@Composable
private fun describedAs(@StringRes action: Int, target: String): Modifier {
    val description = "${stringResource(action)} $target"
    return Modifier.semantics { contentDescription = description }
}

@Composable
private fun CategoryHeader(category: Category, actions: MerchantCatalogActions) {
    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Text(category.name, style = MaterialTheme.typography.titleLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(
                onClick = { actions.onAddProduct(category.id) },
                modifier = describedAs(R.string.action_add_product, category.name),
            ) { Text(stringResource(R.string.action_add_product)) }
            TextButton(
                onClick = { actions.onEditCategory(category) },
                modifier = describedAs(R.string.action_edit, category.name),
            ) { Text(stringResource(R.string.action_edit)) }
            TextButton(
                onClick = { actions.onRemoveCategory(category.id) },
                modifier = describedAs(R.string.action_delete, category.name),
            ) { Text(stringResource(R.string.action_delete)) }
        }
    }
}

@Composable
private fun ProductRow(product: Product, actions: MerchantCatalogActions) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(product.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text(stringResource(R.string.price_nio, PriceInput.format(product.price)))
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.label_available))
            Switch(
                checked = product.isAvailable,
                onCheckedChange = { actions.onAvailabilityChange(product.id, it) },
                modifier = describedAs(R.string.label_available, product.name),
            )
            TextButton(
                onClick = { actions.onEditProduct(product) },
                modifier = describedAs(R.string.action_edit, product.name),
            ) { Text(stringResource(R.string.action_edit)) }
            TextButton(
                onClick = { actions.onRemoveProduct(product.id) },
                modifier = describedAs(R.string.action_delete, product.name),
            ) { Text(stringResource(R.string.action_delete)) }
        }
    }
}

/** Inline form shown in place of the "add category" button; a window dialog is avoided so it composes like the rest of the list. */
@Composable
private fun CategoryEditor(editor: CategoryEditorState, actions: MerchantCatalogActions) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.category_editor_title), style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = editor.name,
            onValueChange = actions.onCategoryNameChange,
            label = { Text(stringResource(R.string.label_category_name)) },
            singleLine = true,
            isError = editor.nameMissing,
            modifier = Modifier.fillMaxWidth(),
        )
        if (editor.nameMissing) {
            Text(stringResource(R.string.category_name_required), color = MaterialTheme.colorScheme.error)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = actions.onSaveCategory) { Text(stringResource(R.string.action_save)) }
            TextButton(onClick = actions.onDismissCategoryEditor) { Text(stringResource(R.string.action_cancel)) }
        }
    }
}

private fun MerchantCatalogError.messageRes(): Int = when (this) {
    MerchantCatalogError.NEEDS_CATEGORY -> R.string.merchant_catalog_error_needs_category
    MerchantCatalogError.CATEGORY_NOT_EMPTY -> R.string.merchant_catalog_error_category_not_empty
    MerchantCatalogError.SAVE_FAILED -> R.string.merchant_catalog_error_save
    MerchantCatalogError.DELETE_FAILED -> R.string.merchant_catalog_error_delete
    MerchantCatalogError.AVAILABILITY_FAILED -> R.string.merchant_catalog_error_availability
    MerchantCatalogError.PHOTO_FAILED -> R.string.merchant_catalog_error_photo
}
