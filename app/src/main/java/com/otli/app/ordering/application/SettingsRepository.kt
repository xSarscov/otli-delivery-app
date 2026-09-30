package com.otli.app.ordering.application

import com.otli.app.core.money.Money

/** Port over `settings/app`, the platform configuration Admin maintains. */
interface SettingsRepository {
    /** The flat delivery fee configured right now; orders snapshot it at placement (ADR-10). */
    suspend fun deliveryFee(): Result<Money>
}
