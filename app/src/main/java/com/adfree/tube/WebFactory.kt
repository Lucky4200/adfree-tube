package com.adfree.tube

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Bitmap
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import io.github.edsuns.adfilter.AdFilter

// Fullscreen video support + popup handling
class FullscreenChrome(private val activity: Activity) : WebChromeClient() {

    private var customView: View? = null
    private var callback: WebChromeClient.CustomViewCallback? = null

    override fun onShowCustomView(view: View?, cb: WebChromeClient.CustomViewCallback?) {
        if (customView != null) {
            cb?.onCustomViewHidden()
            return
        }
        customView = view
        callback = cb
        val decor = activity.window.decorView as FrameLayout
        decor.addView(
            view,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
    }

    override fun onHideCustomView() {
        val v = customView ?: return
        (activity.window.decorView as FrameLayout).removeView(v)
        customView = null
        callback?.onCustomViewHidden()
        callback = null
    }

    fun hideIfShown(): Boolean {
        if (customView != null) {
            onHideCustomView()
            return true
        }
        return false
    }
}

object WebFactory {

    @SuppressLint("SetJavaScriptEnabled")
    fun create(
        activity: Activity,
        chrome: FullscreenChrome,
        allowHost: (String) -> Boolean,
        onMainFrameError: () -> Unit,
        onLoaded: () -> Unit
    ): WebView {
        val wv = WebView(activity)
        val s = wv.settings
        s.javaScriptEnabled = true
        s.domStorageEnabled = true
        s.allowFileAccess = false
        s.allowContentAccess = false
        s.setSupportMultipleWindows(false)
        s.javaScriptCanOpenWindowsAutomatically = true
        s.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE

        val filter = AdFilter.get()
        filter.setupWebView(wv)

        wv.webChromeClient = chrome
        wv.webViewClient = object : WebViewClient() {

            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest
            ): WebResourceResponse? {
                // First apply EasyList/AdGuard-compatible native network rules.
                val nativeResponse = try {
                    filter.shouldIntercept(view, request).resourceResponse
                } catch (_: Exception) {
                    null
                }
                if (nativeResponse != null) return nativeResponse

                // Retain the app's fast emergency rules as a second layer.
                return if (AdBlock.isBlocked(request.url.toString())) {
                    AdBlock.empty()
                } else {
                    super.shouldInterceptRequest(view, request)
                }
            }

            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest
            ): Boolean {
                if (!request.isForMainFrame) return false
                val url = request.url
                val host = url.host ?: return true
                if (url.scheme != "https" && url.scheme != "http") return true
                if (AdBlock.isBlocked(url.toString())) return true
                return !allowHost(host)
            }

            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                try {
                    filter.performScript(view, url)
                } catch (_: Exception) {
                }
                view.evaluateJavascript(AdBlock.JS, null)
            }

            override fun onPageFinished(view: WebView, url: String?) {
                super.onPageFinished(view, url)
                try {
                    filter.performScript(view, url)
                } catch (_: Exception) {
                }
                view.evaluateJavascript(AdBlock.JS, null)
                onLoaded()
            }

            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest,
                error: WebResourceError
            ) {
                super.onReceivedError(view, request, error)
                if (request.isForMainFrame) onMainFrameError()
            }
        }
        return wv
    }
}
