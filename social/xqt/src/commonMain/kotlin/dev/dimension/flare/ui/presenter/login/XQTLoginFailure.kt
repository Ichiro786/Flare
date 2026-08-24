package dev.dimension.flare.ui.presenter.login

internal sealed interface XQTLoginFailure {
    val message: String

    data object NetworkUnavailable : XQTLoginFailure {
        override val message: String = "X is unreachable. Check your connection and try again."
    }

    data object Timeout : XQTLoginFailure {
        override val message: String = "X took too long to respond. Try again."
    }

    data object XChallenge : XQTLoginFailure {
        override val message: String = "X requires an additional verification step. Complete it in the browser, then try again."
    }

    data object InvalidSession : XQTLoginFailure {
        override val message: String = "The X session was rejected. Log in to X again."
    }

    data class RateLimited(
        val retryAfterSeconds: Long?,
    ) : XQTLoginFailure {
        override val message: String =
            retryAfterSeconds?.let { "X is rate-limiting login requests. Try again in ${it}s." }
                ?: "X is rate-limiting login requests. Try again shortly."
    }

    data object UnsupportedResponse : XQTLoginFailure {
        override val message: String = "X returned an unsupported login response. Try again."
    }

    data object UserCancelled : XQTLoginFailure {
        override val message: String = "X login was cancelled."
    }
}

internal val XQTLoginFailure.kind: LoginFailureKind
    get() =
        when (this) {
            XQTLoginFailure.NetworkUnavailable -> LoginFailureKind.NetworkUnavailable
            XQTLoginFailure.Timeout -> LoginFailureKind.Timeout
            XQTLoginFailure.XChallenge -> LoginFailureKind.XChallenge
            XQTLoginFailure.InvalidSession -> LoginFailureKind.InvalidSession
            is XQTLoginFailure.RateLimited -> LoginFailureKind.RateLimited
            XQTLoginFailure.UnsupportedResponse -> LoginFailureKind.UnsupportedResponse
            XQTLoginFailure.UserCancelled -> LoginFailureKind.UserCancelled
        }

internal class XQTLoginException(
    val failure: XQTLoginFailure,
    cause: Throwable? = null,
) : Exception(failure.message, cause)
