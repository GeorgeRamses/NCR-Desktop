package com.ncr.app

import androidx.compose.runtime.MutableState
import com.ncr.app.data.BulkFailure
import com.ncr.app.data.BulkOperationState
import com.ncr.app.data.NcrModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.cancel
import java.io.File

/**
 * Orchestrates batch export operations across a list of [NcrModel] records.
 *
 * All heavy I/O runs on [Dispatchers.IO]. Progress is posted back to the
 * Compose state holder on each iteration so the UI updates in real-time.
 *
 * Design decisions:
 *  - Per-item exceptions are caught and accumulated in [BulkFailure] — the
 *    batch never aborts early, because one locked file should not prevent the
 *    other 50 exports from completing.
 *  - The caller supplies a `scope` (typically `rememberCoroutineScope()`) so
 *    the job is automatically cancelled if the composable leaves composition.
 */
object BulkActionExecutor {

    /**
     * Launches a bulk export operation.
     *
     * @param scope          Coroutine scope tied to the UI lifecycle
     * @param ncrList        The filtered list of NCRs to process
     * @param operationName  Human-readable label shown in the progress dialog
     * @param outputDir      Directory where generated files are written
     * @param state          Mutable compose state that drives the progress UI
     * @param fileNameProvider Returns the output filename for a given [NcrModel]
     * @param action         The per-item export lambda, receives the NCR and the
     *                       destination [File]. Runs on [Dispatchers.IO].
     */
    fun executeBulk(
        scope: CoroutineScope,
        ncrList: List<NcrModel>,
        operationName: String,
        outputDir: File,
        state: MutableState<BulkOperationState>,
        fileNameProvider: (NcrModel) -> String,
        action: (NcrModel, File) -> Unit
    ) {
        // Guard: do nothing if already running or list is empty
        if (state.value is BulkOperationState.Running) return
        if (ncrList.isEmpty()) return

        outputDir.mkdirs()

        val job = scope.launch {
            val failures = mutableListOf<BulkFailure>()
            val total = ncrList.size
            var isCancelled = false

            // Transition to Running
            state.value = BulkOperationState.Running(
                operationName = operationName,
                total = total,
                completed = 0,
                currentItem = ncrList.firstOrNull()?.ncr ?: "—",
                onCancel = {
                    isCancelled = true
                    // Cancellation will be handled by the scope, but we mark it here
                }
            )

            // Actually link onCancel to cancelling this specific coroutine block
            state.value = (state.value as BulkOperationState.Running).copy(
                onCancel = {
                    isCancelled = true
                    this.cancel() // cancel the CoroutineScope of this launch
                }
            )


            for ((index, ncr) in ncrList.withIndex()) {
                val ncrNo = ncr.ncr ?: "Unknown"

                if (isCancelled || !isActive) {
                    break
                }

                // Update current-item indicator before processing
                state.value = (state.value as? BulkOperationState.Running)?.copy(
                    currentItem = ncrNo
                ) ?: state.value

                try {
                    val destFile = File(outputDir, fileNameProvider(ncr))
                    withContext(Dispatchers.IO) {
                        action(ncr, destFile)
                    }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    isCancelled = true
                    break
                } catch (e: Exception) {
                    failures.add(
                        BulkFailure(
                            ncrNo = ncrNo,
                            error = e.message ?: "Unknown error"
                        )
                    )
                }

                // Update progress (completed = index + 1)
                state.value = (state.value as? BulkOperationState.Running)?.copy(
                    completed = index + 1,
                    failures = failures.toList()
                ) ?: state.value
            }

            // Transition to Completed
            val finalCompleted = (state.value as? BulkOperationState.Running)?.completed ?: total
            state.value = BulkOperationState.Completed(
                operationName = if (isCancelled) "$operationName (Cancelled)" else operationName,
                total = total,
                successCount = finalCompleted - failures.size,
                failures = failures.toList()
            )
        }
    }
}
