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
            u.contains("/banner_ads") ||
            u.contains("/pagead/") ||
            u.contains("/api/stats/ads")
    }

    fun empty(): WebResourceResponse =
        WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))

    // Page par se ads wale hisse chupa deta hai aur popups band karta hai
    const val JS = """
(function(){
  if (window.__adFreeTubeAdHelperInstalled) return;
  window.__adFreeTubeAdHelperInstalled = true;

  try { window.open = function(){ return null; }; } catch(e) {}

  // Hide common page ads, but never hide YouTube's player ad container:
  // doing so can hide the Skip Ad button itself.
  try {
    var css = 'ins.adsbygoogle,[id^="google_ads"],[class*="adsbox"],[class*="ad-banner"],' +
      '[id*="ad-container"],[class*="popup-ad"],iframe[src*="doubleclick"],' +
      'iframe[src*="/ads/"],ytd-ad-slot-renderer{display:none!important}';
    if (!document.querySelector('style[data-adfree-tube="ad-css"]')) {
      var s = document.createElement('style');
      s.setAttribute('data-adfree-tube', 'ad-css');
      s.textContent = css;
      (document.head || document.documentElement).appendChild(s);
    }
  } catch(e) {}

  function scan(root) {
    if (!root || !root.querySelectorAll) return;
    var selectors = [
      '.ytp-ad-skip-button',
      '.ytp-ad-skip-button-modern',
      '.ytp-skip-ad-button',
      'button[aria-label*="Skip ad" i]',
      'button[aria-label*="Skip Ads" i]',
      '.ytp-ad-overlay-close-button'
    ];
    try {
      for (var i = 0; i < selectors.length; i++) {
        var nodes = root.querySelectorAll(selectors[i]);
        for (var j = 0; j < nodes.length; j++) {
          var el = nodes[j];
          var rect = el.getBoundingClientRect();
          var style = window.getComputedStyle(el);
          var label = ((el.getAttribute('aria-label') || '') + ' ' +
                       (el.innerText || el.textContent || '')).toLowerCase();
          var isSkip = selectors[i].indexOf('close-button') < 0 ||
                       label.indexOf('skip') >= 0;
          if (isSkip && rect.width > 0 && rect.height > 0 &&
              style.display !== 'none' && style.visibility !== 'hidden' &&
              !el.disabled && el.getAttribute('aria-disabled') !== 'true') {
            el.click();
          }
        }
      }
      // Search open shadow roots used by some YouTube player controls.
      var all = root.querySelectorAll('*');
      for (var k = 0; k < all.length; k++) {
        if (all[k].shadowRoot) scan(all[k].shadowRoot);
      }
    } catch(e) {}
  }

  function trySkip() { scan(document); }
  trySkip();
  try {
    new MutationObserver(trySkip).observe(document.documentElement || document, {
      childList: true, subtree: true, attributes: true,
      attributeFilter: ['class', 'style', 'aria-label', 'disabled']
    });
  } catch(e) {}
  try { window.setInterval(trySkip, 500); } catch(e) {}
})();
"""
}
