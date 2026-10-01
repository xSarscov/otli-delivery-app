package com.otli.app.ordering.adapters.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import com.otli.app.R
import com.otli.app.ordering.domain.Order
import com.otli.app.core.time.Clock
import com.otli.app.core.time.SystemClock
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** When an order was placed, in local time. [date] is only set when the order was not placed today. */
data class PlacedAt(val time: String, val date: String?)

/** Formats an order's placement time for its card; "today" is judged by [clock] in [zone]. */
class PlacedAtFormatter(
    private val clock: Clock = SystemClock(),
    private val zone: ZoneId = ZoneId.systemDefault(),
    locale: Locale = Locale.getDefault(),
) {
    private val timeFormat = DateTimeFormatter.ofPattern("HH:mm", locale)
    private val dateFormat = DateTimeFormatter.ofPattern("d MMM", locale)

    /** Null while the server timestamp is still pending (epoch millis zero). */
    fun format(createdAtMillis: Long): PlacedAt? {
        if (createdAtMillis == 0L) return null
        val placed = Instant.ofEpochMilli(createdAtMillis).atZone(zone)
        val today = Instant.ofEpochMilli(clock.nowMillis()).atZone(zone).toLocalDate()
        return PlacedAt(
            time = timeFormat.format(placed),
            date = if (placed.toLocalDate() == today) null else dateFormat.format(placed),
        )
    }
}

@Composable
internal fun rememberPlacedAtFormatter(): PlacedAtFormatter = remember { PlacedAtFormatter() }

/** The "Placed 11:04" line of an order card. */
@Composable
internal fun placedAtText(order: Order, formatter: PlacedAtFormatter): String {
    val placed = formatter.format(order.createdAtMillis) ?: return stringResource(R.string.order_placed_just_now)
    return if (placed.date == null) {
        stringResource(R.string.order_placed_at, placed.time)
    } else {
        stringResource(R.string.order_placed_at_date, placed.date, placed.time)
    }
}
