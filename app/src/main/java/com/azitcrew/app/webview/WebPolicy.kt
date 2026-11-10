package com.azitcrew.app.webview

import android.net.Uri

enum class WebNavigationDecision { LOAD_IN_APP, OPEN_EXTERNALLY }

class WebPolicy(private val baseUrl: String) {

    private val baseUri: Uri = Uri.parse(baseUrl)

    private fun isTrusted(host: String?): Boolean =
        host != null && host.equals(baseUri.host, ignoreCase = true)

    fun decide(uri: Uri): WebNavigationDecision = when (uri.scheme?.lowercase()) {
        "http", "https" -> if (isTrusted(uri.host)) WebNavigationDecision.LOAD_IN_APP else WebNavigationDecision.OPEN_EXTERNALLY
        "about", "blob", "data" -> WebNavigationDecision.LOAD_IN_APP
        else -> WebNavigationDecision.OPEN_EXTERNALLY
    }

    // azit://schedule/1?tab=a → WEB_URL/schedule/1?tab=a, https://azitcrew.com/... → 그대로
    fun webUrl(deepLink: Uri): String? = when (deepLink.scheme?.lowercase()) {
        "azit" -> {
            val host = deepLink.host ?: return null
            buildString {
                append(baseUrl.trimEnd('/')).append('/').append(host).append(deepLink.path.orEmpty())
                deepLink.encodedQuery?.let { append('?').append(it) }
            }
        }
        "https" -> if (isTrusted(deepLink.host)) deepLink.toString() else null
        else -> null
    }
}
