package com.otli.app.core.result

import kotlin.coroutines.cancellation.CancellationException

/**
 * Like [runCatching], but a [CancellationException] is rethrown instead of captured, so
 * cancelling the calling coroutine still cancels it. Use this around suspending calls.
 */
inline fun <T> suspendRunCatching(block: () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (failure: Exception) {
        Result.failure(failure)
    }
