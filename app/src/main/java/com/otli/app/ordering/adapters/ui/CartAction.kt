package com.otli.app.ordering.adapters.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.otli.app.R

/** Top-bar cart button with the number of units in the cart as a badge (none when the cart is empty). */
@Composable
fun CartAction(itemCount: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val description = if (itemCount > 0) {
        stringResource(R.string.cart_open_with_count, itemCount)
    } else {
        stringResource(R.string.cart_open)
    }
    IconButton(onClick = onClick, modifier = modifier) {
        BadgedBox(badge = { if (itemCount > 0) Badge { Text(itemCount.toString()) } }) {
            Icon(Icons.Filled.ShoppingCart, contentDescription = description)
        }
    }
}
