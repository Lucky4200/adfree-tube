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
        Pair("Pakistan • PTV News official YouTube Live", "https://www.youtube.com/@ptvnewsofficial/live"),
        Pair("Pakistan • PTV News official live page", "https://moib.gov.pk/LiveStream"),
        Pair("Pakistan • ARY News official YouTube Live", "https://www.youtube.com/@ArynewsTvofficial/live"),
        Pair("Pakistan • Geo News official YouTube Live", "https://www.youtube.com/@geonews/live"),
        Pair("Pakistan • Samaa TV official YouTube Live", "https://www.youtube.com/@Samaatv/live"),
        Pair("Pakistan • BOL News official YouTube Live", "https://www.youtube.com/@BOLNewsofficial/live"),
        Pair("Pakistan • ARY News official website", "https://live.arynews.tv/pk/"),
        Pair("Pakistan • AVT Khyber official live", "https://avtkhyber.tv/khyber-tv-live-streaming/"),
        Pair("Pakistan • PTV Sports official page", "https://www.ptv-sports.com.pk/live-streaming/"),
        Pair("India • DD News official YouTube Live", "https://www.youtube.com/@DDnews/live"),
        Pair("India • DD India official YouTube Live", "https://www.youtube.com/channel/UCGDQNvybfDDeGTf4GtigXaw/live"),
        Pair("India • DD Sports official YouTube Live", "https://www.youtube.com/user/doordarshansports/live"),
        Pair("India • Sansad TV official YouTube Live", "https://www.youtube.com/@SansadTV/live"),
        Pair("India • Prasar Bharati Live TV / DD", "https://prasarbharati.gov.in/en/live-tv/"),
        Pair("India • WAVES official OTT", "https://prasarbharati.gov.in/en/waves/"),
        Pair("India • Animal Planet HD English (JioTV; subscription/sign-in may be required)", "https://www.jiotv.com/live-channel/animal-planet-hd-world/286/just-animals/251004286016"),
        Pair("Saudi Arabia • Aloula / Saudi Broadcasting Authority", "https://aloula.sba.sa/"),
        Pair("Bangladesh • BTV official live", "https://btvlive.gov.bd/")
    )

    // Official portals are also inserted into the country lists as in-app web-player
    // fallbacks. These are broadcaster pages, not extracted/private stream URLs.
    val OFFICIAL_CHANNELS = listOf(
        Triple("PTV News • Official YouTube Live", "PK", "https://www.youtube.com/@ptvnewsofficial/live"),
        Triple("PTV News • Official live page", "PK", "https://moib.gov.pk/LiveStream"),
        Triple("ARY News • Official YouTube Live", "PK", "https://www.youtube.com/@ArynewsTvofficial/live"),
        Triple("Geo News • Official YouTube Live", "PK", "https://www.youtube.com/@geonews/live"),
        Triple("Samaa TV • Official YouTube Live", "PK", "https://www.youtube.com/@Samaatv/live"),
        Triple("BOL News • Official YouTube Live", "PK", "https://www.youtube.com/@BOLNewsofficial/live"),
        Triple("ARY News • Official website", "PK", "https://live.arynews.tv/pk/"),
        Triple("PTV Sports • Official page", "PK", "https://www.ptv-sports.com.pk/live-streaming/"),
        Triple("DD News • Official YouTube Live", "IN", "https://www.youtube.com/@DDNewsOfficial/live"),
        Triple("DD India • Official YouTube Live", "IN", "https://www.youtube.com/channel/UCGDQNvybfDDeGTf4GtigXaw/live"),
        Triple("DD Sports • Official YouTube Live", "IN", "https://www.youtube.com/user/doordarshansports/live"),
        Triple("Sansad TV • Official YouTube Live", "IN", "https://www.youtube.com/@SansadTV/live"),
        Triple("Prasar Bharati Live TV • Official", "IN", "https://prasarbharati.gov.in/en/live-tv/"),
        Triple("WAVES • Official", "IN", "https://prasarbharati.gov.in/en/waves/"),
        Triple("BTV Live • Official", "BD", "https://btvlive.gov.bd/"),
        Triple("Aloula • Official", "SA", "https://aloula.sba.sa/")
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
