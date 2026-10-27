package com.example.dinefinder.ui

import android.graphics.Bitmap
import android.net.http.SslError
import android.webkit.*
import android.widget.FrameLayout
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

internal fun isInAppReviewUrl(url: String): Boolean = url.toHttpUrlOrNull()?.let {
    it.isHttps && it.username.isEmpty() && it.password.isEmpty() && url.length <= 4096
} == true

internal class ReviewBrowserController {
    var webView: WebView? = null
        private set
    fun attach(view: WebView) { webView = view }
    fun detach(view: WebView) { if (webView === view) webView = null }
    fun back(close: () -> Unit) {
        val view = webView
        if (view?.canGoBack() == true) view.goBack() else close()
    }
}

/** Displays the supplied live webpage; never scrapes it or launches another app. */
@Suppress("SetJavaScriptEnabled")
@Composable
internal fun RestaurantReviewBrowser(name: String, url: String, controller: ReviewBrowserController, onClose: () -> Unit) {
    val owner = LocalLifecycleOwner.current
    var loading by remember(url) { mutableStateOf(true) }
    var progress by remember(url) { mutableFloatStateOf(0f) }
    var failure by remember(url) { mutableStateOf<String?>(null) }
    var attempt by remember(url) { mutableIntStateOf(0) }
    DisposableEffect(owner, controller) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) controller.webView?.onPause()
            if (event == Lifecycle.Event.ON_RESUME) controller.webView?.onResume()
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    Surface(Modifier.fillMaxSize().testTag("in_app_reviews"), color = Color.White, contentColor = DiscoveryInk) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IconButton(onClick = { controller.back(onClose) }, modifier = Modifier.size(48.dp).background(DiscoveryMint, CircleShape)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back from reviews", tint = DiscoveryGreen)
                }
                Column(Modifier.weight(1f)) {
                    Text("Restaurant reviews", fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.Bold)
                    Text(name, color = DiscoveryMuted, fontSize = 13.sp, lineHeight = 19.sp)
                }
                IconButton(onClick = onClose, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Default.Close, "Close reviews", tint = DiscoveryGreen)
                }
            }
            Text("Reviews from Google Maps", Modifier.fillMaxWidth().background(DiscoveryMint.copy(alpha = .4f)).padding(horizontal = 18.dp, vertical = 8.dp),
                color = DiscoveryGreen, fontSize = 12.sp, lineHeight = 18.sp)
            if (loading && failure == null) {
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth(), color = DiscoveryGreen)
                Text("Loading reviews…", Modifier.padding(horizontal = 18.dp, vertical = 6.dp), color = DiscoveryMuted, fontSize = 12.sp)
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (!isInAppReviewUrl(url)) {
                    Text("Review link not available", Modifier.padding(24.dp), color = DiscoveryMuted)
                } else key(url, attempt) {
                    AndroidView(modifier = Modifier.fillMaxSize().testTag("review_web_content"),
                        factory = { context ->
                            val browser = runCatching { WebView(context) }.getOrNull()
                            if (browser == null) {
                                loading = false
                                failure = "The review viewer is unavailable. Update Android System WebView and try again."
                                FrameLayout(context)
                            } else browser.apply {
                                controller.attach(this)
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                settings.allowFileAccess = false
                                settings.allowContentAccess = false
                                settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                                settings.javaScriptCanOpenWindowsAutomatically = false
                                settings.setSupportMultipleWindows(false)
                                settings.setGeolocationEnabled(false)
                                settings.useWideViewPort = true
                                settings.loadWithOverviewMode = true
                                CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
                                webChromeClient = object : WebChromeClient() {
                                    override fun onProgressChanged(view: WebView, newProgress: Int) {
                                        if (controller.webView === view) progress = newProgress.coerceIn(0,100) / 100f
                                    }
                                    override fun onPermissionRequest(request: PermissionRequest) { request.deny() }
                                    override fun onGeolocationPermissionsShowPrompt(origin: String, callback: GeolocationPermissions.Callback) {
                                        callback.invoke(origin, false, false)
                                    }
                                }
                                val navigation = ReviewWebNavigation()
                                webViewClient = object : WebViewClient() {
                                    private fun navigate(view: WebView, link: String, mainFrame: Boolean): Boolean {
                                        if (controller.webView !== view) return true
                                        return when (val decision = navigation.decide(link, mainFrame)) {
                                            ReviewWebDecision.StayInWebView -> false
                                            is ReviewWebDecision.LoadFallback -> {
                                                loading = true; failure = null
                                                view.loadUrl(decision.url)
                                                true
                                            }
                                            ReviewWebDecision.BlockSubframe -> true
                                            ReviewWebDecision.ShowUnavailable -> {
                                                loading = false
                                                failure = "Google Maps hasn't provided a review page that can open here. Try again or return to the restaurant."
                                                true
                                            }
                                        }
                                    }
                                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                                        navigate(view, request.url.toString(), request.isForMainFrame)
                                    @Deprecated("Android legacy callback")
                                    override fun shouldOverrideUrlLoading(view: WebView, link: String): Boolean = navigate(view, link, true)
                                    override fun onPageStarted(view: WebView, link: String?, favicon: Bitmap?) {
                                        if (controller.webView === view) { loading = true; progress = 0f; failure = null }
                                    }
                                    override fun onPageFinished(view: WebView, link: String?) {
                                        if (controller.webView === view) loading = false
                                    }
                                    override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                                        if (request.isForMainFrame && controller.webView === view) {
                                            loading = false; failure = "Reviews couldn't be loaded. Check your connection and try again."
                                        }
                                    }
                                    override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: WebResourceResponse) {
                                        if (request.isForMainFrame && controller.webView === view && response.statusCode >= 400) {
                                            loading = false; failure = "The review page is unavailable. Please try again."
                                        }
                                    }
                                    override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
                                        handler.cancel()
                                        if (controller.webView === view) {
                                            loading = false; failure = "A secure connection to the review page couldn't be established."
                                        }
                                    }
                                }
                                setDownloadListener { _, _, _, _, _ ->
                                    loading = false
                                    failure = "This review page is requesting a download. Return to the restaurant or retry the page."
                                }
                                // No Supabase headers, tokens, native bridges or admin credentials are supplied.
                                loadUrl(url)
                            }
                        },
                        onRelease = { view ->
                            if (view is WebView) {
                                controller.detach(view)
                                view.stopLoading()
                                view.webChromeClient = null
                                view.webViewClient = WebViewClient()
                                view.destroy()
                            }
                        })
                }
                if (failure != null) Surface(Modifier.fillMaxSize(), color = Color.White) {
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(failure.orEmpty(), color = DiscoveryMuted, fontSize = 15.sp, lineHeight = 23.sp)
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = { failure = null; loading = true; progress = 0f; attempt++ },
                            colors = ButtonDefaults.buttonColors(containerColor = DiscoveryGreen)) { Text("Retry reviews") }
                        TextButton(onClick = onClose) { Text("Back to restaurant", color = DiscoveryGreen) }
                    }
                }
            }
        }
    }
}
