package com.otli.app.catalog.adapters.ui

import androidx.compose.ui.graphics.ImageBitmap
import com.google.common.truth.Truth.assertThat
import com.otli.app.catalog.application.PhotoKey
import com.otli.app.catalog.application.PhotoSource
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.launch
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/** Native graphics only so [ImageBitmap] can be created; the source and decoder are fakes. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PhotoLoaderTest {
    private class FakeSource(var outcome: (String, String) -> ByteArray? = { _, _ -> byteArrayOf(1, 2, 3) }) : PhotoSource {
        val fetches = mutableListOf<Pair<String, String>>()

        override suspend fun fetch(merchantId: String, photoId: String): ByteArray? {
            fetches += merchantId to photoId
            return outcome(merchantId, photoId)
        }
    }

    /** Every decode yields a 2x2 bitmap (16 bytes) so cache budgets are easy to reason about. */
    private class FakeDecoder(var outcome: (ByteArray) -> ImageBitmap? = { ImageBitmap(2, 2) }) : PhotoDecoder {
        var decodes = 0

        override fun decode(bytes: ByteArray): ImageBitmap? {
            decodes++
            return outcome(bytes)
        }
    }

    private val source = FakeSource()
    private val decoder = FakeDecoder()

    private fun loader(cacheBytes: Long = 1_000L, testScope: kotlinx.coroutines.test.TestScope) =
        PhotoLoader(source, decoder, StandardTestDispatcher(testScope.testScheduler), cacheBytes)

    private val key = PhotoKey.product("m1", "p1", version = 1)

    @Test
    fun fetchesTheBytesOfTheKeyedPhotoAndDecodesThem() = runTest {
        val loader = loader(testScope = this)

        val image = loader.load(key)

        assertThat(image).isNotNull()
        assertThat(image!!.width).isEqualTo(2)
        assertThat(source.fetches).containsExactly("m1" to "p1")
    }

    @Test
    fun aMerchantProfilePhotoIsFetchedUnderTheFixedProfileId() = runTest {
        loader(testScope = this).load(PhotoKey.merchantProfile("m9", version = 3))

        assertThat(source.fetches).containsExactly("m9" to PhotoKey.PROFILE_PHOTO_ID)
    }

    @Test
    fun theSecondLoadOfTheSameKeyIsServedFromMemory() = runTest {
        val loader = loader(testScope = this)

        val first = loader.load(key)
        val second = loader.load(key)

        assertThat(second).isSameInstanceAs(first)
        assertThat(source.fetches).hasSize(1)
        assertThat(decoder.decodes).isEqualTo(1)
    }

    @Test
    fun aDifferentPhotoIsFetchedSeparately() = runTest {
        val loader = loader(testScope = this)

        loader.load(key)
        loader.load(PhotoKey.product("m1", "p2", version = 1))

        assertThat(source.fetches).containsExactly("m1" to "p1", "m1" to "p2").inOrder()
    }

    @Test
    fun aNewPhotoVersionInvalidatesTheCachedPicture() = runTest {
        val loader = loader(testScope = this)

        loader.load(key)
        loader.load(key.copy(version = 2))

        assertThat(source.fetches).hasSize(2)
    }

    @Test
    fun versionZeroMeansNoPhotoAndNeverTouchesTheNetwork() = runTest {
        val image = loader(testScope = this).load(PhotoKey.product("m1", "p1", version = 0))

        assertThat(image).isNull()
        assertThat(source.fetches).isEmpty()
    }

    @Test
    fun peekReturnsOnlyWhatIsAlreadyInMemory() = runTest {
        val loader = loader(testScope = this)
        assertThat(loader.peek(key)).isNull()

        val loaded = loader.load(key)

        assertThat(loader.peek(key)).isSameInstanceAs(loaded)
        assertThat(source.fetches).hasSize(1)
    }

    @Test
    fun aMissingPhotoYieldsNullAndIsRetriedNextTime() = runTest {
        source.outcome = { _, _ -> null }
        val loader = loader(testScope = this)

        assertThat(loader.load(key)).isNull()
        source.outcome = { _, _ -> byteArrayOf(9) }

        assertThat(loader.load(key)).isNotNull()
        assertThat(source.fetches).hasSize(2)
    }

    @Test
    fun aFailingSourceYieldsNullInsteadOfCrashing() = runTest {
        source.outcome = { _, _ -> error("offline") }

        assertThat(loader(testScope = this).load(key)).isNull()
    }

    @Test
    fun undecodableBytesYieldNullAndAreNotCached() = runTest {
        decoder.outcome = { null }
        val loader = loader(testScope = this)

        assertThat(loader.load(key)).isNull()
        assertThat(loader.peek(key)).isNull()
    }

    @Test
    fun theLeastRecentlyUsedPictureIsEvictedWhenTheByteBudgetIsExceeded() = runTest {
        val loader = loader(cacheBytes = 32L, testScope = this)
        val a = PhotoKey.product("m1", "a", 1)
        val b = PhotoKey.product("m1", "b", 1)
        val c = PhotoKey.product("m1", "c", 1)

        loader.load(a)
        loader.load(b)
        loader.peek(a)
        loader.load(c)

        assertThat(loader.peek(b)).isNull()
        assertThat(loader.peek(a)).isNotNull()
        assertThat(loader.peek(c)).isNotNull()
    }

    @Test
    fun cancellingALoadPropagatesInsteadOfBeingSwallowedAsNull() = runTest {
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        val slow = object : PhotoSource {
            override suspend fun fetch(merchantId: String, photoId: String): ByteArray? {
                gate.await()
                return byteArrayOf(1)
            }
        }
        val loader = PhotoLoader(slow, decoder, StandardTestDispatcher(testScheduler), 1_000L)
        var completed = false
        val job: Job = launch {
            loader.load(key)
            completed = true
        }
        advanceUntilIdle()

        job.cancel()
        advanceUntilIdle()

        assertThat(completed).isFalse()
        assertThat(job.isCancelled).isTrue()
    }
}
