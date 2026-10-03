package org.randomdhamma.util

import kotlinx.coroutines.CancellationException

/**
 * Like [runCatching], but for suspending work: cancellation is rethrown instead of being
 * captured as a failure, so a cancelled coroutine stops instead of reporting an error.
 */
suspend fun <T> suspendRunCatching(block: suspend () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }
