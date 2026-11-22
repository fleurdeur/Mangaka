package app.kurenai.core.common

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

object KurenaiDispatchers {
    val io: CoroutineDispatcher = Dispatchers.IO
    val default: CoroutineDispatcher = Dispatchers.Default
    val main: CoroutineDispatcher = Dispatchers.Main.immediate
}

sealed interface Outcome<out T> {
    data class Success<T>(val value: T) : Outcome<T>
    data class Failure(val error: Throwable, val message: String? = null) : Outcome<Nothing>

    val isSuccess: Boolean get() = this is Success
}

inline fun <T, R> Outcome<T>.map(transform: (T) -> R): Outcome<R> = when (this) {
    is Outcome.Success -> Outcome.Success(transform(value))
    is Outcome.Failure -> this
}

inline fun <T> Outcome<T>.onFailure(block: (Throwable) -> Unit): Outcome<T> {
    if (this is Outcome.Failure) block(error)
    return this
}

inline fun <T> Outcome<T>.getOrNull(): T? = (this as? Outcome.Success)?.value

class KurenaiException(message: String, cause: Throwable? = null) : Exception(message, cause)
