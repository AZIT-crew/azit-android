# azit-android

AZIT(아지트) Android 웹뷰 shell 앱이에요. `WEB_URL`(기본 `https://azitcrew.com`)을 `WebView`로 띄워요.

- 패키지: `com.azitcrew.app` (dev flavor는 `.dev` 접미사)
- 웹 URL 변경: `app/build.gradle.kts`의 flavor별 `WEB_URL`
- 딥링크: `azit://schedule/1` → `WEB_URL/schedule/1`, App Links `https://azitcrew.com` (`assetlinks.json` 등록 필요)
- 같은 host 외 링크는 외부 앱/브라우저로 열어요
- 상·하단 safe area는 웹이 `env(safe-area-inset-*)`로 처리해요
- UA에 `AZIT Android`가 붙어요
- 빌드: `./gradlew assembleDevDebug`
- release 서명: 루트에 `keystore.properties`(`storeFile`, `storePassword`, `keyAlias`, `keyPassword`)를 두면 적용돼요
