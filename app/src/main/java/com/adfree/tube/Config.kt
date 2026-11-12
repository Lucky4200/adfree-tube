package com.adfree.tube

/**
 * Yahan se sources badalna aasan hai. GitHub par is file ko edit karke
 * naye links add kar dein, phir build khud naya APK bana degi.
 */
object Config {

    // Movies section ka link
    const val MOVIES_URL = "https://www.allsitehub.site/"

    // Movies site ke sirf yeh hosts khulenge (baqi sab block)
    val MOVIE_HOSTS = listOf("allsitehub.site")

    // YouTube section: pehla server chalta hai, na chale to agla khud try hota hai.
    // (Piped / Invidious ads-free YouTube frontends hain. Servers kabhi kabhi band hote hain,
    //  is liye naye servers yahan add kar sakte hain.)
    val YOUTUBE_SERVERS = listOf(
        "https://piped.video",
        "https://yewtu.be",
        "https://inv.nadeko.net",
        "https://m.youtube.com"
    )

    // TV channels: (mulk, playlist link). Ek na chale to doosra khud try hota hai.
    // Naye backup links yahan add kar sakte hain.
    val PLAYLISTS = listOf(
        Pair("PK", "https://iptv-org.github.io/iptv/countries/pk.m3u"),
        Pair("PK", "https://raw.githubusercontent.com/iptv-org/iptv/master/streams/pk.m3u"),
        Pair("IN", "https://iptv-org.github.io/iptv/countries/in.m3u"),
        Pair("IN", "https://raw.githubusercontent.com/iptv-org/iptv/master/streams/in.m3u")
    )

    // Ad / tracker servers jo block hote hain
    val AD_HOSTS = listOf(
        "doubleclick.net", "googlesyndication.com", "googleadservices.com",
        "adservice.google.com", "google-analytics.com", "googletagmanager.com",
        "googletagservices.com", "adnxs.com", "taboola.com", "outbrain.com",
        "popads.net", "popcash.net", "propellerads.com", "exoclick.com",
        "juicyads.com", "adsterra.com", "trafficjunky.net", "onclickads.net",
        "onclkds.com", "clickadu.com", "hilltopads.net", "a-ads.com",
        "adcash.com", "mgid.com", "revcontent.com", "bidvertiser.com",
        "adform.net", "criteo.com", "pubmatic.com", "rubiconproject.com",
        "openx.net", "amazon-adsystem.com", "ads.yahoo.com", "an.yandex.ru",
        "smartadserver.com", "adsco.re", "pushails.com", "monetag.com",
        "profitablecpmrate.com", "highperformanceformat.com", "effectivegatecpm.com",
        "ad-maven.com", "admaven.com", "richpush.co", "yllix.com", "adskeeper.com",
        "scorecardresearch.com", "facebook.net", "hotjar.com"
    )
}
