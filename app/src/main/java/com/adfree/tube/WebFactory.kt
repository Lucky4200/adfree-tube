package com.adfree.tube

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Bitmap
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.os.Build
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout

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
        // HTML5 video fullscreen should rotate to landscape on phones and TVs.
        activity.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
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
        // Return control to the device's normal orientation policy after video fullscreen.
        activity.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
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

    private const val BACKGROUND_MEDIA_JS = """
        (function() {
          try {
            Object.defineProperty(document, 'hidden', { configurable: true, get: function(){ return false; } });
            Object.defineProperty(document, 'visibilityState', { configurable: true, get: function(){ return 'visible'; } });
            document.addEventListener('visibilitychange', function(e) { e.stopImmediatePropagation(); }, true);
          } catch (e) {}
        })();
    """

    @SuppressLint("SetJavaScriptEnabled")
    fun create(
        activity: Activity,
        chrome: FullscreenChrome,
        allowHost: (String) -> Boolean,
        onMainFrameError: () -> Unit,
        onLoaded: () -> Unit,
        keepMediaInBackground: Boolean = false
    ): WebView {
        val wv = WebView(activity)
        val s = wv.settings
        s.javaScriptEnabled = true
        s.domStorageEnabled = true
        s.databaseEnabled = true
        s.loadsImagesAutomatically = true
        s.blockNetworkImage = false
        s.cacheMode = WebSettings.LOAD_DEFAULT
        s.mediaPlaybackRequiresUserGesture = false
        s.allowFileAccess = false
        s.allowContentAccess = false
        s.setSupportMultipleWindows(false)
        s.javaScriptCanOpenWindowsAutomatically = false
        s.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        CookieManager.getInstance().setAcceptCookie(true)
        if (Build.VERSION.SDK_INT >= 21) CookieManager.getInstance().setAcceptThirdPartyCookies(wv, true)

        wv.webChromeClient = chrome
        wv.webViewClient = object : WebViewClient() {

            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest
            ): WebResourceResponse? {
                if (BraveCompat.shouldBlock(view, request)) {
                    return BraveCompat.emptyResponse()
                }
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
                BraveCompat.rememberPage(view, url)
                BraveCompat.injectCosmetics(view, url)
                view.evaluateJavascript(AdBlock.JS, null)
            }

            override fun onPageFinished(view: WebView, url: String?) {
                super.onPageFinished(view, url)
                BraveCompat.injectCosmetics(view, url)
                view.evaluateJavascript(AdBlock.JS, null)
                if (keepMediaInBackground) view.evaluateJavascript(BACKGROUND_MEDIA_JS, null)
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

    fun showErrorPage(view: WebView, destination: String, title: String) {
        val safeUrl = destination.replace("&", "&amp;").replace("'", "%27")
        val html = """<!doctype html><html><head><meta name="viewport" content="width=device-width, initial-scale=1"><meta charset="utf-8"><style>body{background:#06101e;color:white;font-family:Arial;text-align:center;padding:15vh 24px}p{color:#b8c8dc;line-height:1.6}button{background:#e32636;color:white;border:0;border-radius:12px;padding:14px 24px;font-size:16px}</style></head><body><h2>$title load nahi hua</h2><p>Internet ya purana WebView masla kar sakta hai. Dobara koshish karein.</p><button onclick="location.href='$safeUrl'">Retry / Dobara kholen</button></body></html>"""
        view.loadDataWithBaseURL(destination, html, "text/html", "UTF-8", null)
    }
}
