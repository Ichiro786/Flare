package dev.dimension.flare.ui.screen.serviceselect

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
import androidx.compose.ui.unit.dp
import dev.dimension.flare.compose.ui.Res
import dev.dimension.flare.compose.ui.login_expired_relogin
import dev.dimension.flare.ui.presenter.login.LoginResumeResult
import io.github.kdroidfilter.webview.web.WebContent
import io.github.kdroidfilter.webview.web.WebView
import io.github.kdroidfilter.webview.web.WebViewState
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Duration.Companion.seconds

@Composable
internal fun WebViewLoginScreen(
    url: String,
    callback: suspend (String?) -> LoginResumeResult,
    onBack: () -> Unit,
) {
    val state =
        remember(url) {
            WebViewState(WebContent.Url("about:blank")).apply {
                webSettings.desktopWebSettings.incognito = true
            }
        }
    var failureMessage by remember(url) { mutableStateOf<String?>(null) }
    var retryGeneration by remember(url) { mutableIntStateOf(0) }

    LaunchedEffect(state.webView, url) {
        val nativeWebView = state.webView?.nativeWebView ?: return@LaunchedEffect
        while (!nativeWebView.isReady()) {
            delay(50)
        }
        nativeWebView.clearAllCookies()
        nativeWebView.loadUrl(url)
    }
    LaunchedEffect(url, retryGeneration) {
        while (failureMessage == null) {
            delay(2.seconds)
            val webView = state.webView?.nativeWebView ?: continue
            val cookies =
                listOfNotNull(state.lastLoadedUrl, url)
                    .distinct()
                    .flatMap { webView.getCookiesForUrl(it) }
                    .plus(webView.getCookies())
                    .distinctBy { listOf(it.domain, it.path, it.name) }
                    .joinToString("; ") { "${it.name}=${it.value}" }
                    .takeIf { it.isNotBlank() }
            when (val result = callback(cookies)) {
                LoginResumeResult.NotReady -> Unit
                LoginResumeResult.Success -> return@LaunchedEffect
                is LoginResumeResult.Failure -> failureMessage = result.message
            }
        }
    }
    Box(modifier = Modifier.fillMaxSize()) {
        WebView(
            state,
            modifier = Modifier.fillMaxSize(),
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
                            retryGeneration += 1
                        },
                        modifier = Modifier.padding(top = 16.dp),
                    ) {
                        Text(text = stringResource(Res.string.login_expired_relogin))
                    }
                    TextButton(onClick = onBack) {
                        Text(text = stringResource(Res.string.cancel))
                    }
                }
            }
        }
    }
}
