package com.otli.app.admin.adapters.ui

import com.google.common.truth.Truth.assertThat
import com.otli.app.admin.application.FakeAdminRepository
import com.otli.app.admin.application.UpdateFee
import com.otli.app.core.money.Money
import com.otli.app.core.testing.MainDispatcherRule
import com.otli.app.ordering.application.FakeSettingsRepository
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import org.junit.Rule
import org.junit.Test

class FeeSettingsViewModelTest {
    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val settings = FakeSettingsRepository(Result.success(Money(3000)))
    private val admin = FakeAdminRepository()

    private fun viewModel() = FeeSettingsViewModel(settings, UpdateFee(admin))

    // --- loading the current fee ---

    @Test
    fun itLoadsTheCurrentFeeIntoTheFieldInTheFormItIsTyped() {
        val state = viewModel().uiState.value

        assertThat(state.isLoading).isFalse()
        assertThat(state.currentFee).isEqualTo(Money(3000))
        assertThat(state.input).isEqualTo("30.00")
    }

    @Test
    fun itIsLoadingUntilTheFeeArrives() {
        val gate = CompletableDeferred<Unit>()
        settings.beforeRead = { gate.await() }

        val viewModel = viewModel()

        assertThat(viewModel.uiState.value.isLoading).isTrue()
        gate.complete(Unit)
        assertThat(viewModel.uiState.value.currentFee).isEqualTo(Money(3000))
    }

    @Test
    fun aFailedLoadIsReportedAndRetryingLoadsTheFee() {
        settings.fee = Result.failure(IOException("offline"))
        val viewModel = viewModel()

        assertThat(viewModel.uiState.value.loadFailed).isTrue()
        assertThat(viewModel.uiState.value.isLoading).isFalse()

        settings.fee = Result.success(Money(4500))
        viewModel.retry()

        assertThat(viewModel.uiState.value.loadFailed).isFalse()
        assertThat(viewModel.uiState.value.currentFee).isEqualTo(Money(4500))
        assertThat(viewModel.uiState.value.input).isEqualTo("45.00")
    }

    // --- saving ---

    @Test
    fun savingATypedAmountWritesItInCentavosAndShowsItAsTheCurrentFee() {
        val viewModel = viewModel()

        viewModel.onInputChange("45.5")
        viewModel.save()

        assertThat(admin.fees).containsExactly(Money(4550))
        assertThat(viewModel.uiState.value.currentFee).isEqualTo(Money(4550))
        assertThat(viewModel.uiState.value.input).isEqualTo("45.50")
        assertThat(viewModel.uiState.value.result).isEqualTo(FeeSaveResult.SAVED)
        assertThat(viewModel.uiState.value.isSaving).isFalse()
    }

    @Test
    fun aWholeAmountAndACommaDecimalAreUnderstood() {
        val viewModel = viewModel()

        viewModel.onInputChange("20")
        viewModel.save()
        viewModel.onInputChange("32,5")
        viewModel.save()

        assertThat(admin.fees).containsExactly(Money(2000), Money(3250)).inOrder()
    }

    @Test
    fun textThatIsNotAnAmountIsRefusedAndNothingIsWritten() {
        val viewModel = viewModel()

        for (text in listOf("abc", "-5", "1.234", "")) {
            viewModel.onInputChange(text)
            viewModel.save()
            assertThat(viewModel.uiState.value.result).isEqualTo(FeeSaveResult.INVALID_AMOUNT)
        }

        assertThat(admin.fees).isEmpty()
        assertThat(viewModel.uiState.value.currentFee).isEqualTo(Money(3000))
    }

    @Test
    fun aZeroFeeIsRefusedAsNotPositive() {
        val viewModel = viewModel()

        viewModel.onInputChange("0")
        viewModel.save()

        assertThat(viewModel.uiState.value.result).isEqualTo(FeeSaveResult.NOT_POSITIVE)
        assertThat(admin.fees).isEmpty()
    }

    @Test
    fun aFailedWriteKeepsTheOldFeeAndTheTypedAmount() {
        admin.writeFailure = IOException("denied")
        val viewModel = viewModel()

        viewModel.onInputChange("50")
        viewModel.save()

        assertThat(viewModel.uiState.value.result).isEqualTo(FeeSaveResult.SAVE_FAILED)
        assertThat(viewModel.uiState.value.isSaving).isFalse()
        assertThat(viewModel.uiState.value.currentFee).isEqualTo(Money(3000))
        assertThat(viewModel.uiState.value.input).isEqualTo("50")
    }

    @Test
    fun editingTheFieldClearsTheLastResult() {
        val viewModel = viewModel()
        viewModel.onInputChange("abc")
        viewModel.save()

        viewModel.onInputChange("4")

        assertThat(viewModel.uiState.value.result).isNull()
    }

    @Test
    fun aSecondSaveWhileOneIsRunningIsIgnored() {
        val viewModel = viewModel()
        val gate = CompletableDeferred<Unit>()
        admin.beforeWrite = { gate.await() }
        viewModel.onInputChange("40")

        viewModel.save()
        viewModel.save()

        assertThat(viewModel.uiState.value.isSaving).isTrue()
        gate.complete(Unit)
        assertThat(admin.fees).containsExactly(Money(4000))
        assertThat(viewModel.uiState.value.isSaving).isFalse()
    }

    @Test
    fun theFieldCannotBeSavedWhileTheFeeIsStillLoading() {
        val gate = CompletableDeferred<Unit>()
        settings.beforeRead = { gate.await() }
        val viewModel = viewModel()
        viewModel.onInputChange("40")

        viewModel.save()

        assertThat(admin.fees).isEmpty()
        assertThat(viewModel.uiState.value.canSave).isFalse()
    }
}
