package com.azitcrew.app

import android.app.Application
import com.kakao.sdk.common.KakaoSdk

class AzitApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        KakaoSdk.init(this, BuildConfig.KAKAO_APP_KEY)
    }
}
