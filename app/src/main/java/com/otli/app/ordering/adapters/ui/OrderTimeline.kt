package com.otli.app.ordering.adapters.ui

import com.otli.app.ordering.domain.OrderStatus

enum class StepProgress { DONE, CURRENT, UPCOMING }

data class TimelineStep(val status: OrderStatus, val progress: StepProgress)

/** The steps an order goes through, as the customer sees them while tracking it. */
object OrderTimeline {
    private val journey = listOf(
        OrderStatus.PLACED,
        OrderStatus.ACCEPTED,
        OrderStatus.PREPARING,
        OrderStatus.READY,
        OrderStatus.CLAIMED,
        OrderStatus.PICKED_UP,
        OrderStatus.DELIVERED,
    )

    /** A rejected or cancelled order leaves the journey right after being placed. */
    fun of(status: OrderStatus): List<TimelineStep> {
        if (status == OrderStatus.REJECTED || status == OrderStatus.CANCELLED) {
            return listOf(TimelineStep(OrderStatus.PLACED, StepProgress.DONE), TimelineStep(status, StepProgress.CURRENT))
        }
        val current = journey.indexOf(status)
        return journey.mapIndexed { index, step ->
            TimelineStep(
                step,
                when {
                    index < current -> StepProgress.DONE
                    index == current -> StepProgress.CURRENT
                    else -> StepProgress.UPCOMING
                },
            )
        }
    }
}
