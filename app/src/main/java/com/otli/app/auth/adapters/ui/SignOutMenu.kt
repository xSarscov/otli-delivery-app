package com.otli.app.auth.adapters.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.otli.app.R
import com.otli.app.core.theme.OtliTheme
import com.otli.app.core.ui.OtliTopBar

/** Container: the app bar overflow menu for signed-in role homes, wired to the real logout. */
@Composable
fun SignOutMenu(modifier: Modifier = Modifier, viewModel: GateViewModel = hiltViewModel()) =
    SignOutMenuContent(onSignOut = viewModel::logout, modifier = modifier)

/** Overflow button for an app bar `actions` slot; its only entry is Sign out. */
@Composable
fun SignOutMenuContent(onSignOut: () -> Unit, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    IconButton(onClick = { expanded = true }, modifier = modifier) {
        Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.action_more_options))
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.action_logout)) },
            onClick = {
                expanded = false
                onSignOut()
            },
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SignOutMenuPreview() {
    OtliTheme { OtliTopBar(title = "Otli", actions = { SignOutMenuContent(onSignOut = {}) }) }
}
