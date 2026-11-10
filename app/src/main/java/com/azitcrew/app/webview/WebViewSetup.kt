package com.azitcrew.app.webview

import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView

fun WebView.applyWebViewSettings() {
    settings.apply {
        javaScriptEnabled = true
        domStorageEnabled = true
        loadWithOverviewMode = true
        useWideViewPort = true
        cacheMode = WebSettings.LOAD_DEFAULT
        // 웹이 UA로 앱 여부를 판별할 수 있게 앱 이름을 붙여요 (iOS: "AZIT iOS")
        userAgentString = "$userAgentString AZIT Android"
    }
    CookieManager.getInstance().apply {
        setAcceptCookie(true)
        setAcceptThirdPartyCookies(this@applyWebViewSettings, true)
    }
}
