package com.otli.app.ordering.application

import com.otli.app.core.money.Money

/** The configured fee; [beforeRead] lets a test hold the read open, [reads] counts how often it happened. */
class FakeSettingsRepository(var fee: Result<Money> = Result.success(Money(3000))) : SettingsRepository {
    var reads = 0
        private set
    var beforeRead: suspend () -> Unit = {}

    override suspend fun deliveryFee(): Result<Money> {
        reads++
        beforeRead()
        return fee
    }
}
