package dev.dimension.flare.ui.screen.serviceselect

import android.net.Uri
import android.view.ViewGroup.LayoutParams
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kevinnzou.web.WebView
import com.kevinnzou.web.rememberWebViewState
import dev.dimension.flare.R
import dev.dimension.flare.ui.component.FlareScaffold
import dev.dimension.flare.ui.presenter.login.LoginFailureKind
import dev.dimension.flare.ui.presenter.login.LoginResumeResult
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.seconds

private const val ANDROID_X_USER_AGENT =
    "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/123.0.0.0 Mobile Safari/537.3"

private val loginHeaders =
    mapOf(
        "Pragma" to "no-cache",
        "Cache-Control" to "no-cache",
    )

private val xCookieHosts =
    setOf(
        "x.com",
        "www.x.com",
        "api.x.com",
        "twitter.com",
        "www.twitter.com",
    )

@Composable
internal fun WebCookieLoginScreen(
    url: String,
    callback: suspend (String?) -> LoginResumeResult,
    onBack: () -> Unit,
) {
    val webViewState = rememberWebViewState("about:blank")
    var failureMessage by remember(url) { mutableStateOf<String?>(null) }
    var failureKind by remember(url) { mutableStateOf<LoginFailureKind?>(null) }
    var retryGeneration by remember(url) { mutableIntStateOf(0) }

    LaunchedEffect(url, retryGeneration) {
        while (failureMessage == null) {
            webViewState.lastLoadedUrl?.let { loadedUrl ->
                when (
                    val result =
                        callback(
                            CookieManager
                                .getInstance()
                                .getXCookieHeader(
                                    loadedUrl = loadedUrl,
                                    originalUrl = url,
                                ),
                        )
                ) {
                    LoginResumeResult.NotReady -> {
                        Unit
                    }

                    LoginResumeResult.Success -> {
                        return@LaunchedEffect
                    }

                    is LoginResumeResult.Failure -> {
                        failureMessage = result.message
                        failureKind = result.kind
                    }
                }
            }
            delay(2.seconds)
        }
    }

    FlareScaffold {
        Box(modifier = Modifier.fillMaxSize()) {
            WebView(
                webViewState,
                layoutParams =
                    FrameLayout.LayoutParams(
                        LayoutParams.MATCH_PARENT,
                        LayoutParams.MATCH_PARENT,
                    ),
                modifier =
                    Modifier
                        .alpha(0.99f)
                        .background(MaterialTheme.colorScheme.background)
                        .padding(it)
                        .fillMaxSize(),
                onCreated = { webView ->
                    with(webView.settings) {
                        userAgentString = ANDROID_X_USER_AGENT
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        javaScriptCanOpenWindowsAutomatically = false
                        cacheMode = WebSettings.LOAD_DEFAULT
                    }
                    CookieManager
                        .getInstance()
                        .apply {
                            setAcceptCookie(true)
                            setAcceptThirdPartyCookies(webView, true)
                            flush()
                        }
                    webView.loadUrl(url, loginHeaders)
                },
            )
            failureMessage?.let { message ->
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background.copy(alpha = 0.96f),
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Button(
                            onClick = {
                                failureMessage = null
                                failureKind = null
                                retryGeneration += 1
                            },
                            modifier = Modifier.padding(top = 16.dp),
                        ) {
                            Text(
                                text =
                                    stringResource(
                                        id =
                                            if (failureKind == LoginFailureKind.XChallenge) {
                                                R.string.x_login_continue
                                            } else {
                                                R.string.login_expired_relogin
                                            },
                                    ),
                            )
                        }
                        TextButton(onClick = onBack) {
                            Text(text = stringResource(id = R.string.cancel))
                        }
                    }
                }
            }
        }
    }
}

private fun CookieManager.getXCookieHeader(
    loadedUrl: String,
    originalUrl: String,
): String? =
    listOf(loadedUrl, originalUrl)
        .distinct()
        .filter { it.xHost() in xCookieHosts }
        .flatMap { url ->
            getCookie(url)
                ?.split(';')
                .orEmpty()
                .map(String::trim)
                .filter { it.substringBefore('=').isNotBlank() }
        }.distinctBy { it.substringBefore('=') }
        .joinToString("; ")
        .takeIf { it.isNotBlank() }

private fun String.xHost(): String? =
    runCatching {
        Uri.parse(this).host?.lowercase()
    }.getOrNull()
