package dev.dimension.flare.data.network.xqt

import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals

class XQTLoginValidationTest {
    @Test
    fun authenticatedBootstrapExtractsUserId() {
        val result =
            classifyInitialResponse(
                status = HttpStatusCode.OK,
                body = "{\"user_id\" : \"12345\"}",
                retryAfter = null,
            )

        assertEquals(XQTLoginValidationResult.Authenticated("12345"), result)
    }

    @Test
    fun challengePageIsNotTreatedAsMissingUserId() {
        val result =
            classifyInitialResponse(
                status = HttpStatusCode.OK,
                body = "<html>Sign in to X challenge checkpoint</html>",
                retryAfter = null,
            )

        assertEquals(XQTLoginValidationResult.XChallenge, result)
    }

    @Test
    fun unauthorizedResponseIsInvalidSession() {
        val result =
            classifyInitialResponse(
                status = HttpStatusCode.Forbidden,
                body = "{}",
                retryAfter = null,
            )

        assertEquals(XQTLoginValidationResult.InvalidSession, result)
    }

    @Test
    fun rateLimitPreservesRetryAfter() {
        val result =
            classifyInitialResponse(
                status = HttpStatusCode.TooManyRequests,
                body = "{}",
                retryAfter = "17",
            )

        assertEquals(XQTLoginValidationResult.RateLimited(17), result)
        assertEquals(17, "17".parseRetryAfterSeconds())
    }

    @Test
    fun serverFailureIsRetryableNetworkFailure() {
        val result =
            classifyInitialResponse(
                status = HttpStatusCode.ServiceUnavailable,
                body = "temporary failure",
                retryAfter = null,
            )

        assertEquals(XQTLoginValidationResult.NetworkUnavailable, result)
    }

    @Test
    fun unknownSuccessfulResponseIsUnsupported() {
        val result =
            classifyInitialResponse(
                status = HttpStatusCode.OK,
                body = "<html>unexpected response</html>",
                retryAfter = null,
            )

        assertEquals(XQTLoginValidationResult.UnsupportedResponse, result)
    }
}
