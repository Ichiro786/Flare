package dev.dimension.flare.data.network.xqt

import dev.dimension.flare.common.JSON_WITH_ENCODE_DEFAULT
import dev.dimension.flare.common.Locale
import dev.dimension.flare.data.network.ktorClient
import dev.dimension.flare.data.network.ktorfit
import dev.dimension.flare.data.network.nullableFallbackJson
import dev.dimension.flare.data.network.xqt.api.DefaultApi
import dev.dimension.flare.data.network.xqt.api.DmApi
import dev.dimension.flare.data.network.xqt.api.GuestApi
import dev.dimension.flare.data.network.xqt.api.ListsApi
import dev.dimension.flare.data.network.xqt.api.MediaApi
import dev.dimension.flare.data.network.xqt.api.OtherApi
import dev.dimension.flare.data.network.xqt.api.PinnableTimelineApi
import dev.dimension.flare.data.network.xqt.api.PostApi
import dev.dimension.flare.data.network.xqt.api.TweetApi
import dev.dimension.flare.data.network.xqt.api.UserApi
import dev.dimension.flare.data.network.xqt.api.UserListApi
import dev.dimension.flare.data.network.xqt.api.UsersApi
import dev.dimension.flare.data.network.xqt.api.V11GetApi
import dev.dimension.flare.data.network.xqt.api.V11PostApi
import dev.dimension.flare.data.network.xqt.api.V20GetApi
import dev.dimension.flare.data.network.xqt.api.VDmPostJsonPostApi
import dev.dimension.flare.data.network.xqt.api.createDefaultApi
import dev.dimension.flare.data.network.xqt.api.createDmApi
import dev.dimension.flare.data.network.xqt.api.createGuestApi
import dev.dimension.flare.data.network.xqt.api.createListsApi
import dev.dimension.flare.data.network.xqt.api.createMediaApi
import dev.dimension.flare.data.network.xqt.api.createOtherApi
import dev.dimension.flare.data.network.xqt.api.createPinnableTimelineApi
import dev.dimension.flare.data.network.xqt.api.createPostApi
import dev.dimension.flare.data.network.xqt.api.createTweetApi
import dev.dimension.flare.data.network.xqt.api.createUserApi
import dev.dimension.flare.data.network.xqt.api.createUserListApi
import dev.dimension.flare.data.network.xqt.api.createUsersApi
import dev.dimension.flare.data.network.xqt.api.createV11GetApi
import dev.dimension.flare.data.network.xqt.api.createV11PostApi
import dev.dimension.flare.data.network.xqt.api.createV20GetApi
import dev.dimension.flare.data.network.xqt.api.createVDmPostJsonPostApi
import dev.dimension.flare.data.network.xqt.elonmusk114514.ElonMusk1145141919810
import dev.dimension.flare.data.platform.XQT_PLATFORM_ID
import dev.dimension.flare.data.repository.LoginExpiredException
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.xqtHost
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.BrowserUserAgent
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.encodedPath
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlin.time.Duration.Companion.milliseconds

private val guestApiUrl = "https://api.$xqtHost/"
private val baseUrl = "https://api.$xqtHost/"
private val uploadUrl = "https://upload.$xqtHost/i/"

@Suppress("PropertyName")
private const val token =
    "AAAAAAAAAAAAAAAAAAAAANRILgAAAAAAnNwIzUejRCOuH5E6I8xnZz4puTs%3D1Zv7ttfk8LF81IUq16cHjhLTvJu4FA33AGWWjCpTnA"
private const val X_CONNECT_TIMEOUT_MILLIS = 8_000L
private const val X_REQUEST_TIMEOUT_MILLIS = 15_000L
private const val X_SOCKET_TIMEOUT_MILLIS = 15_000L
private const val X_VALIDATION_RETRIES = 1
private const val X_RETRY_DELAY_MILLIS = 250L

internal sealed interface XQTLoginValidationResult {
    data class Authenticated(
        val userId: String,
    ) : XQTLoginValidationResult

    data object NetworkUnavailable : XQTLoginValidationResult

    data object Timeout : XQTLoginValidationResult

    data object XChallenge : XQTLoginValidationResult

    data object InvalidSession : XQTLoginValidationResult

    data class RateLimited(
        val retryAfterSeconds: Long?,
    ) : XQTLoginValidationResult

    data object UnsupportedResponse : XQTLoginValidationResult
}

private fun xqtClient(
    accountKey: MicroBlogKey?,
    chocolateFlow: Flow<String>?,
): HttpClient =
    ktorClient {
        install(ContentNegotiation) {
            nullableFallbackJson(JSON_WITH_ENCODE_DEFAULT)
        }
        install(HttpTimeout) {
            connectTimeoutMillis = X_CONNECT_TIMEOUT_MILLIS
            requestTimeoutMillis = X_REQUEST_TIMEOUT_MILLIS
            socketTimeoutMillis = X_SOCKET_TIMEOUT_MILLIS
        }
        install(XQTHeaderPlugin) {
            this.chocolateFlow = chocolateFlow
            this.accountKey = accountKey
        }
    }

private fun xqtBootstrapClient(): HttpClient =
    ktorClient {
        BrowserUserAgent()
        install(HttpTimeout) {
            connectTimeoutMillis = X_CONNECT_TIMEOUT_MILLIS
            requestTimeoutMillis = X_REQUEST_TIMEOUT_MILLIS
            socketTimeoutMillis = X_SOCKET_TIMEOUT_MILLIS
        }
    }

internal class XQTClientBundle(
    accountKey: MicroBlogKey?,
    chocolateFlow: Flow<String>?,
) : AutoCloseable {
    private val apiClient = xqtClient(accountKey, chocolateFlow)
    val bootstrapClient: HttpClient = xqtBootstrapClient()
    val defaultApi = ktorfit(apiClient, baseUrl, json = JSON_WITH_ENCODE_DEFAULT).createDefaultApi()
    val otherApi = ktorfit(apiClient, baseUrl, json = JSON_WITH_ENCODE_DEFAULT).createOtherApi()
    val pinnableTimelineApi = ktorfit(apiClient, baseUrl, json = JSON_WITH_ENCODE_DEFAULT).createPinnableTimelineApi()
    val postApi = ktorfit(apiClient, baseUrl, json = JSON_WITH_ENCODE_DEFAULT).createPostApi()
    val tweetApi = ktorfit(apiClient, baseUrl, json = JSON_WITH_ENCODE_DEFAULT).createTweetApi()
    val userApi = ktorfit(apiClient, baseUrl, json = JSON_WITH_ENCODE_DEFAULT).createUserApi()
    val userListApi = ktorfit(apiClient, baseUrl, json = JSON_WITH_ENCODE_DEFAULT).createUserListApi()
    val usersApi = ktorfit(apiClient, baseUrl, json = JSON_WITH_ENCODE_DEFAULT).createUsersApi()
    val v11GetApi = ktorfit(apiClient, baseUrl, json = JSON_WITH_ENCODE_DEFAULT).createV11GetApi()
    val v11PostApi = ktorfit(apiClient, baseUrl, json = JSON_WITH_ENCODE_DEFAULT).createV11PostApi()
    val v20GetApi = ktorfit(apiClient, baseUrl, json = JSON_WITH_ENCODE_DEFAULT).createV20GetApi()
    val guestApi = ktorfit(apiClient, guestApiUrl, json = JSON_WITH_ENCODE_DEFAULT).createGuestApi()
    val mediaApi = ktorfit(apiClient, uploadUrl, json = JSON_WITH_ENCODE_DEFAULT).createMediaApi()
    val listsApi = ktorfit(apiClient, baseUrl, json = JSON_WITH_ENCODE_DEFAULT).createListsApi()
    val dmApi = ktorfit(apiClient, baseUrl, json = JSON_WITH_ENCODE_DEFAULT).createDmApi()
    val dmJsonApi = ktorfit(apiClient, baseUrl, json = JSON_WITH_ENCODE_DEFAULT).createVDmPostJsonPostApi()

    override fun close() {
        bootstrapClient.close()
        apiClient.close()
    }
}

internal class XQTService(
    private val chocolateFlow: Flow<String>? = null,
    private val accountKey: MicroBlogKey? = null,
    private val clients: XQTClientBundle = XQTClientBundle(accountKey, chocolateFlow),
) : DefaultApi by clients.defaultApi,
    OtherApi by clients.otherApi,
    PinnableTimelineApi by clients.pinnableTimelineApi,
    PostApi by clients.postApi,
    TweetApi by clients.tweetApi,
    UserApi by clients.userApi,
    UserListApi by clients.userListApi,
    UsersApi by clients.usersApi,
    V11GetApi by clients.v11GetApi,
    V11PostApi by clients.v11PostApi,
    V20GetApi by clients.v20GetApi,
    GuestApi by clients.guestApi,
    MediaApi by clients.mediaApi,
    ListsApi by clients.listsApi,
    DmApi by clients.dmApi,
    VDmPostJsonPostApi by clients.dmJsonApi,
    AutoCloseable {
    companion object {
        fun checkChocolate(value: String) = value.contains("ct0=") && value.contains("auth_token=")
    }

    suspend fun getInitialUserId(chocolate: String): XQTLoginValidationResult {
        var retry = 0
        while (true) {
            try {
                val response =
                    clients.bootstrapClient
                        .get("https://x.com/home") {
                            headers {
                                append("Cookie", chocolate)
                                append("sec-fetch-dest", "document")
                                append("sec-fetch-mode", "navigate")
                                append("sec-fetch-site", "cross-site")
                                append("sec-gpc", "1")
                                append("upgrade-insecure-requests", "1")
                                append("accept-language", Locale.language.ifBlank { "en" })
                                append("dnt", "1")
                            }
                            accept(ContentType.Text.Html)
                        }
                val body = response.bodyAsText()
                val result =
                    classifyInitialResponse(
                        status = response.status,
                        body = body,
                        retryAfter = response.headers[HttpHeaders.RetryAfter],
                    )
                if (result is XQTLoginValidationResult.NetworkUnavailable && retry < X_VALIDATION_RETRIES) {
                    retry += 1
                    delay((X_RETRY_DELAY_MILLIS * retry).milliseconds)
                    continue
                }
                return result
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                if (error::class.simpleName?.contains("Timeout", ignoreCase = true) == true) {
                    return XQTLoginValidationResult.Timeout
                }
                if (retry < X_VALIDATION_RETRIES) {
                    retry += 1
                    delay((X_RETRY_DELAY_MILLIS * retry).milliseconds)
                    continue
                }
                return XQTLoginValidationResult.NetworkUnavailable
            }
        }
    }

    override fun close() {
        clients.close()
    }
}

internal fun classifyInitialResponse(
    status: HttpStatusCode,
    body: String,
    retryAfter: String?,
): XQTLoginValidationResult {
    if (status == HttpStatusCode.TooManyRequests) {
        return XQTLoginValidationResult.RateLimited(retryAfter.parseRetryAfterSeconds())
    }
    if (status == HttpStatusCode.Unauthorized || status == HttpStatusCode.Forbidden) {
        return XQTLoginValidationResult.InvalidSession
    }
    if (status.value in 500..599) {
        return XQTLoginValidationResult.NetworkUnavailable
    }
    val userId = "\"user_id\"\\s*:\\s*\"([0-9]+)\"".toRegex().find(body)?.groupValues?.get(1)
    if (status.value in 200..299 && userId != null) {
        return XQTLoginValidationResult.Authenticated(userId)
    }
    val lowerBody = body.lowercase()
    if (listOf("challenge", "checkpoint", "/i/flow/login", "sign in to x", "authenticate").any { lowerBody.contains(it) }) {
        return XQTLoginValidationResult.XChallenge
    }
    return XQTLoginValidationResult.UnsupportedResponse
}

internal fun String?.parseRetryAfterSeconds(): Long? = this?.trim()?.toLongOrNull()?.coerceAtLeast(0L)

private class XQTHeaderConfig {
    var chocolateFlow: Flow<String>? = null
    var accountKey: MicroBlogKey? = null
}

private val XQTHeaderPlugin =
    createClientPlugin("XQTHeaderPlugin", ::XQTHeaderConfig) {
        val chocolateFlow = pluginConfig.chocolateFlow
        val accountKey = pluginConfig.accountKey
        onRequest { request, _ ->
            val transactionId =
                runCatching {
                    ElonMusk1145141919810.senpaiSukissu(
                        method = request.method.value,
                        path = request.url.encodedPath,
                    )
                }.getOrNull()
            val chocolate = chocolateFlow?.firstOrNull()
            request.headers {
                append("Authorization", "Bearer $token")
                append("x-twitter-client-language", xTwitterClientLanguage(Locale.language))
                append(
                    "Sec-Ch-Ua",
                    "\"Chromium\";v=\"116\", \"Not)A;Brand\";v=\"24\", \"Google Chrome\";v=\"116\"",
                )
                append("Sec-Ch-Ua-Mobile", "?0")
                append("Sec-Fetch-Dest", "empty")
                append("Sec-Fetch-Mode", "cors")
                append("Sec-Fetch-Site", "same-origin")
                append("Sec-Ch-Ua-Platform", "\"Windows\"")
                append(
                    "User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                        "(KHTML, like Gecko) Chrome/112.0.0.0 Safari/537.36",
                )
                if (chocolate != null) {
                    if (transactionId != null) {
                        append("x-client-transaction-id", transactionId)
                    }
                    val guestToken =
                        chocolate
                            .split(';')
                            .firstOrNull { it.trim().startsWith("gt=") }
                            ?.trim()
                            ?.removePrefix("gt=")
                    val csrfToken =
                        chocolate
                            .split(';')
                            .firstOrNull { it.trim().startsWith("ct0=") }
                            ?.trim()
                            ?.removePrefix("ct0=")
                    if (guestToken != null) append("x-guest-token", guestToken)
                    if (csrfToken != null) {
                        append("x-twitter-active-user", "yes")
                        append("x-twitter-auth-type", "OAuth2Session")
                        append("x-csrf-token", csrfToken)
                    }
                    append("Cookie", chocolate)
                }
            }
        }
        onResponse { response ->
            val error =
                runCatching {
                    val bodyJson = response.body<JsonObject>()
                    bodyJson["errors"]
                        ?.jsonArray
                        ?.firstOrNull()
                        ?.jsonObject
                        ?.get("code")
                        ?.jsonPrimitive
                        ?.longOrNull
                }.getOrNull()
            if ((error == 215L || response.status == HttpStatusCode.Forbidden) && accountKey != null) {
                throw LoginExpiredException(accountKey, XQT_PLATFORM_ID)
            }
        }
    }

internal fun xTwitterClientLanguage(locale: String): String {
    val normalized = locale.trim().replace('_', '-')
    if (normalized.equals("en-GB", ignoreCase = true)) return "en-GB"
    return when (
        val language =
            dev.dimension.flare.data.translation
                .canonicalTranslationLanguage(normalized)
    ) {
        "zh", "zh-hans" -> "zh-cn"
        "zh-hant" -> "zh-tw"
        "ms" -> "msa"
        "nb", "nn", "no" -> "no"
        "fil", "tl" -> "tl"
        null, "und" -> "en"
        else -> language
    }
}
