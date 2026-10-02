package com.otli.app.tracking.adapters.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.otli.app.R
import com.otli.app.core.theme.OtliTheme
import com.otli.app.tracking.domain.TrackingPlan

/**
 * Stateless line under the availability switch: tells the courier the location is being shared with the
 * customer, or warns that it is off (claiming and delivering still work) and offers to allow it.
 * Shows nothing without a delivery.
 */
@Composable
fun TrackingStatusContent(
    plan: TrackingPlan,
    onAllowLocation: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (plan) {
        TrackingPlan.Idle -> Unit
        is TrackingPlan.Share -> Text(
            stringResource(R.string.tracking_sharing),
            modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            style = MaterialTheme.typography.bodySmall,
        )
        is TrackingPlan.NeedsPermission -> Column(
            modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(stringResource(R.string.tracking_permission_missing), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            Button(onClick = onAllowLocation) { Text(stringResource(R.string.tracking_allow_location)) }
        }
    }
}

/** Container: runs the tracking coordinator and feeds it the location [permission] owned by the courier home. */
@Composable
fun TrackingStatusScreen(
    permission: LocationPermission,
    modifier: Modifier = Modifier,
    viewModel: TrackingViewModel = hiltViewModel(),
) {
    val plan by viewModel.plan.collectAsStateWithLifecycle()
    LaunchedEffect(permission.granted) { viewModel.onPermissionChanged(permission.granted) }
    TrackingStatusContent(plan = plan, onAllowLocation = permission::request, modifier = modifier)
}

@Preview(showBackground = true)
@Composable
private fun TrackingSharingPreview() {
    OtliTheme { TrackingStatusContent(TrackingPlan.Share("o1", "courier-1"), onAllowLocation = {}) }
}

@Preview(showBackground = true)
@Composable
private fun TrackingPermissionMissingPreview() {
    OtliTheme { TrackingStatusContent(TrackingPlan.NeedsPermission("o1"), onAllowLocation = {}) }
}
