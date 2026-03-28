package com.novaterm.core.common.util

/**
 * Sealed result type for operations that can fail.
 * Avoids throwing exceptions across module boundaries.
 */
sealed interface OpResult<out T> {
    data class Success<T>(val value: T) : OpResult<T>
    data class Failure(val error: String, val cause: Throwable? = null) : OpResult<Nothing>
}

inline fun <T> OpResult<T>.onSuccess(action: (T) -> Unit): OpResult<T> {
    if (this is OpResult.Success) action(value)
    return this
}

inline fun <T> OpResult<T>.onFailure(action: (String, Throwable?) -> Unit): OpResult<T> {
    if (this is OpResult.Failure) action(error, cause)
    return this
}

fun <T> OpResult<T>.getOrNull(): T? = when (this) {
    is OpResult.Success -> value
    is OpResult.Failure -> null
}

fun <T> OpResult<T>.getOrThrow(): T = when (this) {
    is OpResult.Success -> value
    is OpResult.Failure -> throw cause ?: IllegalStateException(error)
}
