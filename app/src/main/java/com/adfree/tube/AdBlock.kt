package com.adfree.tube

import android.net.Uri
import android.webkit.WebResourceResponse
import java.io.ByteArrayInputStream

object AdBlock {

    fun isBlocked(url: String): Boolean {
        val host = try {
            Uri.parse(url).host?.lowercase()
        } catch (e: Exception) {
            null
        } ?: return false

        if (Config.AD_HOSTS.any { host == it || host.endsWith(".$it") }) return true

        val u = url.lowercase()
        return u.contains("/pagead/") ||
            u.contains("/api/stats/ads") ||
            u.contains("/adserver") ||
            u.contains("/ads/") ||
            u.contains("adsbygoogle") ||
            u.contains("popunder") ||
            u.contains("/banner_ads")
    }

    fun empty(): WebResourceResponse =
        WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))

    // Page par se ads wale hisse chupa deta hai aur popups band karta hai
    const val JS = """
(function(){
  try { window.open = function(){ return null; }; } catch(e) {}
  try {
    var css = 'ins.adsbygoogle,[id^="google_ads"],[class*="adsbox"],[class*="ad-banner"],' +
      '[id*="ad-container"],[class*="popup-ad"],iframe[src*="doubleclick"],' +
      'iframe[src*="/ads/"],.ytp-ad-module,.video-ads,ytd-ad-slot-renderer{display:none!important}';
    var s = document.createElement('style');
    s.innerHTML = css;
    (document.head || document.documentElement).appendChild(s);
  } catch(e) {}
})();
"""
}
