package com.adfree.tube

import android.content.Context
import android.os.Build
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView

/**
 * Keeps the Brave JNI dependency out of Android 7.1 runtime class loading.
 * API 25 uses the app's lightweight AdBlock rules instead.
 */
object BraveCompat {
    private const val MIN_BRAVE_API = 26
    @Volatile private var target: Any? = null
    @Volatile private var targetClass: Class<*>? = null

    private fun engineObject(): Any? {
        if (Build.VERSION.SDK_INT < MIN_BRAVE_API) return null
        target?.let { return it }
        synchronized(this) {
            target?.let { return it }
            return try {
                val clazz = Class.forName("com.adfree.tube.BraveAdBlock")
                val instance = clazz.getField("INSTANCE").get(null)
                targetClass = clazz
                target = instance
                instance
            } catch (_: Throwable) {
                null
            }
        }
    }

    fun initialize(context: Context) {
        val instance = engineObject() ?: return
        try { targetClass?.getMethod("initialize", Context::class.java)?.invoke(instance, context) }
        catch (_: Throwable) { }
    }

    fun shouldBlock(view: WebView, request: WebResourceRequest): Boolean {
        val instance = engineObject() ?: return false
        return try {
            targetClass?.getMethod("shouldBlock", WebView::class.java, WebResourceRequest::class.java)
                ?.invoke(instance, view, request) as? Boolean ?: false
        } catch (_: Throwable) { false }
    }

    fun emptyResponse(): WebResourceResponse? {
        val instance = engineObject() ?: return null
        return try {
            targetClass?.getMethod("emptyResponse")?.invoke(instance) as? WebResourceResponse
        } catch (_: Throwable) { null }
    }

    fun rememberPage(view: WebView, url: String?) {
        val instance = engineObject() ?: return
        try { targetClass?.getMethod("rememberPage", WebView::class.java, String::class.java)?.invoke(instance, view, url) }
        catch (_: Throwable) { }
    }

    fun injectCosmetics(view: WebView, url: String?) {
        val instance = engineObject() ?: return
        try { targetClass?.getMethod("injectCosmetics", WebView::class.java, String::class.java)?.invoke(instance, view, url) }
        catch (_: Throwable) { }
    }
}
