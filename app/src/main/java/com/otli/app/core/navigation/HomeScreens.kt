package com.otli.app.core.navigation

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.otli.app.R
import com.otli.app.auth.domain.Role

/** Semantics tags that let UI tests tell the root destinations apart. */
object RootTags {
    const val LOADING = "root-loading"
    const val SIGNED_OUT = "root-signed-out"
    const val PROFILE_INCOMPLETE = "root-profile-incomplete"
    const val STOREFRONT = "root-storefront"
    const val CART = "root-cart"
    const val CHECKOUT = "root-checkout"
    const val CUSTOMER_ORDERS = "root-customer-orders"
    const val ORDER_TRACKING = "root-order-tracking"
    const val ADMIN_ORDER = "root-admin-order"

    fun pending(role: Role) = "root-pending-${role.name.lowercase()}"

    fun suspended(role: Role) = "root-suspended-${role.name.lowercase()}"

    fun home(role: Role) = "root-home-${role.name.lowercase()}"
}

@Composable
fun LoadingScreen(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().testTag(RootTags.LOADING), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
fun CustomerHomeScreen(modifier: Modifier = Modifier) =
    HomePlaceholder(R.string.home_customer, RootTags.home(Role.CUSTOMER), modifier)

@Composable
fun MerchantHomeScreen(modifier: Modifier = Modifier) =
    HomePlaceholder(R.string.home_merchant, RootTags.home(Role.MERCHANT), modifier)

@Composable
fun CourierHomeScreen(modifier: Modifier = Modifier) =
    HomePlaceholder(R.string.home_courier, RootTags.home(Role.COURIER), modifier)

@Composable
fun AdminHomeScreen(modifier: Modifier = Modifier) =
    HomePlaceholder(R.string.home_admin, RootTags.home(Role.ADMIN), modifier)

/** Empty role home; later slices replace the body with the role's real screens. */
@Composable
internal fun HomePlaceholder(@StringRes title: Int, tag: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().testTag(tag), contentAlignment = Alignment.Center) {
        Text(stringResource(title), style = MaterialTheme.typography.headlineSmall)
    }
}
