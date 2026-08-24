package dev.dimension.flare.ui.presenter.login

import dev.dimension.flare.data.datasource.xqt.userById
import dev.dimension.flare.data.network.xqt.XQTLoginValidationResult
import dev.dimension.flare.data.network.xqt.XQTPlatformDetector
import dev.dimension.flare.data.network.xqt.XQTService
import dev.dimension.flare.data.network.xqt.model.User
import dev.dimension.flare.data.network.xqt.parseRetryAfterSeconds
import dev.dimension.flare.data.platform.XQTCredential
import dev.dimension.flare.data.platform.XQT_PLATFORM_ID
import dev.dimension.flare.data.platform.XqtPlatformSpec
import dev.dimension.flare.data.repository.AccountService
import dev.dimension.flare.data.repository.addAccountAndAwait
import dev.dimension.flare.di.koinInject
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformMetadata
import dev.dimension.flare.model.RecommendedInstance
import dev.dimension.flare.model.xqtHost
import dev.dimension.flare.ui.model.UiAccount
import dev.dimension.flare.ui.model.UiInstance
import dev.dimension.flare.ui.model.UiInstanceMetadata
import dev.dimension.flare.ui.model.UiStrings
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf

private const val LOGIN_ACTION = "login"

public data object XQTLoginProvider : LoginPlatformProvider {
    override val platformId: String = XQT_PLATFORM_ID
    override val metadata: PlatformMetadata
        get() = XqtPlatformSpec.metadata
    override val detector: PlatformDetector = XQTPlatformDetector
    override val methods: List<LoginMethodSpec> =
        listOf(
            LoginMethodSpec(
                type = LoginMethodType.WebCookie,
                title = UiStrings.WebCookieLogin,
            ),
        )

    override fun agreementUrl(host: String): String? = "https://help.x.com/en/rules-and-policies/x-rules"

    override suspend fun recommendInstances(): List<RecommendedInstance> =
        listOf(
            RecommendedInstance(
                instance =
                    UiInstance(
                        name = metadata.displayName,
                        description =
                            "From breaking news and entertainment to sports and politics," +
                                " get the full story with all the live commentary.",
                        iconUrl = null,
                        domain = xqtHost,
                        platformId = platformId,
                        bannerUrl = null,
                        usersCount = 0,
                    ),
                priority = 80,
            ),
        )

    override suspend fun instanceMetadata(host: String): UiInstanceMetadata =
        throw UnsupportedOperationException("$platformId is not supported yet")

    override fun createHandler(context: LoginContext): LoginMethodHandler {
        require(context.methodType == LoginMethodType.WebCookie) {
            "Unsupported X login method: ${context.methodType}"
        }
        return XQTWebCookieLoginHandler(context)
    }
}

private class XQTWebCookieLoginHandler(
    private val context: LoginContext,
) : LoginMethodHandler {
    private val accountService: AccountService by koinInject()
    private val _state = MutableStateFlow(state())
    private val _effects = MutableSharedFlow<LoginEffect>(extraBufferCapacity = 1)
    private val resumeSingleFlight = XQTLoginSingleFlight()

    override val state: StateFlow<LoginFlowState> = _state
    override val effects: Flow<LoginEffect> = _effects

    override fun updateField(
        id: String,
        value: String,
    ) = Unit

    override suspend fun perform(actionId: String) {
        if (actionId != LOGIN_ACTION) return
        _effects.emit(
            LoginEffect.OpenWebCookieLogin(
                url = "https://$xqtHost/i/flow/login",
            ),
        )
    }

    override suspend fun resume(value: String) {
        resumeSingleFlight.run {
            _state.value = state(loading = true)
            try {
                if (value.isBlank()) throw XQTLoginException(XQTLoginFailure.InvalidSession)
                if (!XQTService.checkChocolate(value)) {
                    throw XQTLoginException(XQTLoginFailure.InvalidSession)
                }
                xqtLoginUseCase(value)
                _state.value = state()
                context.onSuccess()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                val failure =
                    (error as? XQTLoginException)?.failure
                        ?: XQTLoginFailure.UnsupportedResponse
                _state.value =
                    state(
                        error = failure.message,
                        failureKind = failure.kind,
                    )
            }
        }
    }

    override fun canResume(value: String): Boolean = value.isNotBlank() && XQTService.checkChocolate(value)

    override fun clear() {
        _state.value = state()
    }

    private suspend fun xqtLoginUseCase(chocolate: String) {
        val xqtService = XQTService(flowOf(chocolate))
        try {
            val userId =
                when (val result = xqtService.getInitialUserId(chocolate = chocolate)) {
                    is XQTLoginValidationResult.Authenticated -> {
                        result.userId
                    }

                    XQTLoginValidationResult.NetworkUnavailable -> {
                        throw XQTLoginException(XQTLoginFailure.NetworkUnavailable)
                    }

                    XQTLoginValidationResult.Timeout -> {
                        throw XQTLoginException(XQTLoginFailure.Timeout)
                    }

                    XQTLoginValidationResult.XChallenge -> {
                        throw XQTLoginException(XQTLoginFailure.XChallenge)
                    }

                    XQTLoginValidationResult.InvalidSession -> {
                        throw XQTLoginException(XQTLoginFailure.InvalidSession)
                    }

                    is XQTLoginValidationResult.RateLimited -> {
                        throw XQTLoginException(XQTLoginFailure.RateLimited(result.retryAfterSeconds))
                    }

                    XQTLoginValidationResult.UnsupportedResponse -> {
                        throw XQTLoginException(XQTLoginFailure.UnsupportedResponse)
                    }
                }
            val userResponse = xqtService.userById(userId)
            when {
                userResponse.status.value == 429 -> {
                    throw XQTLoginException(
                        XQTLoginFailure.RateLimited(
                            userResponse.headers[HttpHeaders.RetryAfter].parseRetryAfterSeconds(),
                        ),
                    )
                }

                userResponse.status == io.ktor.http.HttpStatusCode.Unauthorized ||
                    userResponse.status == io.ktor.http.HttpStatusCode.Forbidden -> {
                    throw XQTLoginException(XQTLoginFailure.InvalidSession)
                }
            }
            val userResponseBody =
                userResponse.body()
                    ?: throw XQTLoginException(XQTLoginFailure.UnsupportedResponse)
            if (!userResponseBody.errors.isNullOrEmpty()) {
                throw XQTLoginException(XQTLoginFailure.InvalidSession)
            }
            val account = userResponseBody.data.user.result
            if (account !is User) {
                throw XQTLoginException(XQTLoginFailure.UnsupportedResponse)
            }
            val accountKey =
                MicroBlogKey(
                    id = account.restId,
                    host = xqtHost,
                )
            context.requireReloginAccount(accountKey)
            accountService.addAccountAndAwait(
                UiAccount(
                    accountKey = accountKey,
                    platformId = XQT_PLATFORM_ID,
                ),
                credential =
                    XQTCredential(
                        chocolate = chocolate,
                    ),
            )
        } finally {
            xqtService.close()
        }
    }

    private fun state(
        loading: Boolean = false,
        error: String? = null,
        failureKind: LoginFailureKind? = null,
    ): LoginFlowState =
        LoginFlowState(
            actions =
                listOf(
                    LoginAction(
                        id = LOGIN_ACTION,
                        label = UiStrings.Login,
                        enabled = !loading,
                    ),
                ),
            loading = loading,
            error = error,
            failureKind = failureKind,
        )
}
