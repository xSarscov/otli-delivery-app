package com.otli.app.core.ui

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import com.otli.app.R
import com.otli.app.core.theme.OtliTheme

/**
 * The top app bar every screen that needs a title, up navigation or overflow actions reuses.
 *
 * The up arrow only exists when [onBack] is given. The bar takes no window insets on purpose:
 * `RootNavHost` already keeps every screen clear of the system bars, so padding here would double it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OtliTopBar(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    TopAppBar(
        title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        modifier = modifier,
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                }
            }
        },
        actions = actions,
        windowInsets = WindowInsets(0, 0, 0, 0),
    )
}

@Preview(showBackground = true)
@Composable
private fun OtliTopBarPreview() {
    OtliTheme { OtliTopBar(title = "Comedor Marta", onBack = {}) }
}
