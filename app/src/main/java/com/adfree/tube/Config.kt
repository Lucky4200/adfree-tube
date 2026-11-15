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

    // Single original YouTube entry; alternate Piped/Invidious mirrors removed.
    const val YOUTUBE_URL = "https://m.youtube.com"

    // TV channels: (mulk, playlist link). Ek na chale to doosra khud try hota hai.
    // Naye backup links yahan add kar sakte hain.
    // Public broadcaster streams only: four requested countries plus curated regional
    // and sports playlists. TvSection filters every parsed channel to PK/IN/BD/SA.
    // These playlists contain publicly submitted streams that iptv-org says were
    // intentionally made public by their copyright holders.
    val PLAYLISTS = listOf(
        Pair("PK", "https://iptv-org.github.io/iptv/countries/pk.m3u"),
        Pair("IN", "https://iptv-org.github.io/iptv/countries/in.m3u"),
        Pair("BD", "https://iptv-org.github.io/iptv/countries/bd.m3u"),
        Pair("SA", "https://iptv-org.github.io/iptv/countries/sa.m3u"),

        // Full source indexes (not the non-existent /raw/ path). These carry
        // alternate URLs and per-stream User-Agent / Referer metadata.
        Pair("PK", "https://raw.githubusercontent.com/iptv-org/iptv/master/streams/pk.m3u"),
        Pair("IN", "https://raw.githubusercontent.com/iptv-org/iptv/master/streams/in.m3u"),
        Pair("BD", "https://raw.githubusercontent.com/iptv-org/iptv/master/streams/bd.m3u"),
        Pair("SA", "https://raw.githubusercontent.com/iptv-org/iptv/master/streams/sa.m3u"),

        // Additional curated feeds provide duplicate channel entries where available,
        // allowing the TV player to keep alternate URLs for automatic failover.
        Pair("SAS", "https://iptv-org.github.io/iptv/regions/sas.m3u"),
        Pair("GCC", "https://iptv-org.github.io/iptv/regions/gcc.m3u"),
        Pair("ARAB", "https://iptv-org.github.io/iptv/regions/arab.m3u"),
        Pair("SPORTS", "https://iptv-org.github.io/iptv/categories/sports.m3u")
    )

    // Official service links only; paid channels still require a valid subscription.
    // These entries are opened inside AdFree Tube's own TV player/WebView.
    val OFFICIAL_SERVICES = listOf(
        Pair("Pakistan • PTV News Live", "https://moib.gov.pk/LiveStream"),
        Pair("Pakistan • ARY News Live", "https://live.arynews.tv/pk/"),
        Pair("Pakistan • AVT Khyber official live", "https://avtkhyber.tv/khyber-tv-live-streaming/"),
        Pair("Pakistan • PTV Sports Live", "https://www.ptv-sports.com.pk/live-streaming/"),
        Pair("India • Prasar Bharati Live TV / DD", "https://prasarbharati.gov.in/en/live-tv/"),
        Pair("India • WAVES official OTT", "https://prasarbharati.gov.in/en/waves/"),
        Pair("India • Animal Planet HD English (JioTV; subscription/sign-in may be required)", "https://www.jiotv.com/live-channel/animal-planet-hd-world/286/just-animals/251004286016"),
        Pair("Saudi Arabia • Aloula / Saudi Broadcasting Authority", "https://aloula.sba.sa/"),
        Pair("Bangladesh • BTV, BTV News, BTV Chattogram, Sangsad", "https://btvlive.gov.bd/")
    )

    // Official portals are also inserted into the country lists as in-app web-player
    // fallbacks. These are broadcaster pages, not extracted/private stream URLs.
    val OFFICIAL_CHANNELS = listOf(
        Triple("PTV News • Official", "PK", "https://moib.gov.pk/LiveStream"),
        Triple("ARY News • Official", "PK", "https://live.arynews.tv/pk/"),
        Triple("PTV Sports • Official", "PK", "https://www.ptv-sports.com.pk/live-streaming/"),
        Triple("Prasar Bharati Live TV • Official", "IN", "https://prasarbharati.gov.in/en/live-tv/"),
        Triple("WAVES • Official", "IN", "https://prasarbharati.gov.in/en/waves/"),
        Triple("BTV Live • Official", "BD", "https://btvlive.gov.bd/"),
        Triple("Aloula • Official", "SA", "https://aloula.sba.sa/")
    )
    val OFFICIAL_SERVICES = listOf(
        Pair("Pakistan • Ministry / PTV News Live", "https://moib.gov.pk/LiveStream"),
        Pair("Pakistan • ARY News Live", "https://live.arynews.tv/pk/"),
        Pair("Pakistan • AVT Khyber official live player", "https://avtkhyber.tv/khyber-tv-live-streaming/"),
        Pair("Pakistan • PTV Sports Live", "https://www.ptv-sports.com.pk/live-streaming/"),
        Pair("India • WAVES free live TV (Prasar Bharati)", "https://prasarbharati.gov.in/en/waves/"),
        Pair("India • Prasar Bharati / DD Channels", "https://prasarbharati.gov.in/en/dd-channels/"),
        Pair("India • Animal Planet HD English (official JioTV; subscription/sign-in may be required)", "https://www.jiotv.com/live-channel/animal-planet-hd-world/286/just-animals/251004286016"),
        Pair("Saudi Arabia • Aloula / Saudi Broadcasting Authority", "https://aloula.sba.sa/"),
        Pair("Bangladesh • BTV, BTV News, BTV Chattogram, Sangsad", "https://btvlive.gov.bd/")
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
