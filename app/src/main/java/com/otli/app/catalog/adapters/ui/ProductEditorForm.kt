package com.otli.app.catalog.adapters.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.otli.app.R
import com.otli.app.catalog.domain.Category

/** Full-screen product form shown in place of the list; inline rather than a dialog so it composes like any other screen. */
@Composable
internal fun ProductEditorForm(
    editor: ProductEditorState,
    categories: List<Category>,
    isSaving: Boolean,
    actions: MerchantCatalogActions,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val title = if (editor.isNew) R.string.product_editor_title_new else R.string.product_editor_title_edit
        Text(stringResource(title), style = MaterialTheme.typography.headlineMedium)
        OutlinedTextField(
            value = editor.name,
            onValueChange = actions.onProductNameChange,
            label = { Text(stringResource(R.string.label_product_name)) },
            isError = ProductEditorError.NAME_REQUIRED in editor.errors,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        ErrorLine(ProductEditorError.NAME_REQUIRED in editor.errors, R.string.product_error_name)
        OutlinedTextField(
            value = editor.description,
            onValueChange = actions.onProductDescriptionChange,
            label = { Text(stringResource(R.string.label_store_description)) },
            minLines = 2,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = editor.priceText,
            onValueChange = actions.onProductPriceChange,
            label = { Text(stringResource(R.string.label_product_price)) },
            isError = ProductEditorError.PRICE_INVALID in editor.errors,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        ErrorLine(ProductEditorError.PRICE_INVALID in editor.errors, R.string.product_error_price)
        Text(stringResource(R.string.label_product_category), style = MaterialTheme.typography.titleSmall)
        Column(Modifier.selectableGroup()) {
            for (category in categories) {
                Row(
                    Modifier.fillMaxWidth().selectable(
                        selected = category.id == editor.categoryId,
                        onClick = { actions.onProductCategoryChange(category.id) },
                        role = Role.RadioButton,
                    ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = category.id == editor.categoryId, onClick = null)
                    Text(category.name, modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
        ErrorLine(ProductEditorError.CATEGORY_REQUIRED in editor.errors, R.string.product_error_category)
        val photoStatus = when {
            editor.pendingPhoto != null -> R.string.product_photo_ready
            editor.photoVersion > 0 -> R.string.product_photo_saved
            else -> R.string.merchant_photo_none
        }
        Text(stringResource(photoStatus))
        OutlinedButton(onClick = actions.onPickProductPhoto, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.action_choose_photo))
        }
        Button(onClick = actions.onSaveProduct, enabled = !isSaving, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.action_save))
        }
        TextButton(onClick = actions.onDismissProductEditor, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.action_cancel))
        }
    }
}

@Composable
private fun ErrorLine(visible: Boolean, message: Int) {
    if (visible) Text(stringResource(message), color = MaterialTheme.colorScheme.error)
}
