package com.ncr.app.data

/**
 * Sealed state machine representing the lifecycle of a bulk operation.
 *
 * Transitions: Idle → Running → Completed → Idle (on dismiss)
 */
sealed class BulkOperationState {

    /** No operation in progress. */
    object Idle : BulkOperationState()

    /**
     * A bulk operation is actively processing items.
     *
     * @param operationName Human-readable label, e.g. "Generating NCR PDFs"
     * @param total         Total number of items to process
     * @param completed     Number of items finished so far (success + failure)
     * @param currentItem   Display label of the item currently being processed
     * @param failures      Accumulated per-item failures that did not abort the batch
     */
    data class Running(
        val operationName: String,
        val total: Int,
        val completed: Int,
        val currentItem: String,
        val failures: List<BulkFailure> = emptyList(),
        val onCancel: () -> Unit = {}
    ) : BulkOperationState() {

        /** Determinate progress in [0.0, 1.0]. */
        val progress: Float
            get() = if (total > 0) completed.toFloat() / total else 0f
    }

    /**
     * The bulk operation has finished processing all items.
     *
     * @param operationName Same label used during [Running]
     * @param total         Total items that were processed
     * @param successCount  Items that completed without error
     * @param failures      Items that threw an exception (kept for the summary dialog)
     */
    data class Completed(
        val operationName: String,
        val total: Int,
        val successCount: Int,
        val failures: List<BulkFailure> = emptyList()
    ) : BulkOperationState()
}

/**
 * Captures a single per-item failure inside a bulk operation.
 *
 * @param ncrNo Identifier of the NCR that failed (e.g. "NCR-24/001")
 * @param error Human-readable error message
 */
data class BulkFailure(
    val ncrNo: String,
    val error: String
)
