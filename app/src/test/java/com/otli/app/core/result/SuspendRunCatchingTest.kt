package com.otli.app.core.result

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SuspendRunCatchingTest {

    @Test
    fun wrapsTheValueOfASuccessfulBlock() = runTest {
        val result = suspendRunCatching { CompletableDeferred(42).await() }

        assertThat(result.getOrNull()).isEqualTo(42)
    }

    @Test
    fun capturesOrdinaryFailuresAsFailedResult() = runTest {
        val boom = IllegalStateException("boom")

        val result = suspendRunCatching<Int> { throw boom }

        assertThat(result.exceptionOrNull()).isSameInstanceAs(boom)
    }

    @Test
    fun rethrowsCancellationInsteadOfCapturingIt() = runTest {
        val failure = runCatching { suspendRunCatching<Int> { throw CancellationException("cancelled") } }

        assertThat(failure.exceptionOrNull()).isInstanceOf(CancellationException::class.java)
    }

    @Test
    fun cancellingTheCallerDoesNotProduceAResult() = runTest {
        val gate = CompletableDeferred<Int>()
        var captured: Result<Int>? = null
        val job = launch { captured = suspendRunCatching { gate.await() } }
        testScheduler.runCurrent()

        job.cancel()
        job.join()

        assertThat(job.isCancelled).isTrue()
        assertThat(captured).isNull()
    }
}
