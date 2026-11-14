package com.azitcrew.app.bridge

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.provider.Settings
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.core.content.ContextCompat
import com.azitcrew.app.BuildConfig
import com.azitcrew.app.webview.WebViewActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream

class AppBridgeInterface(
    private val activity: WebViewActivity,
    private val webView: WebView,
    private val scope: CoroutineScope,
) {
    @JavascriptInterface
    fun postMessage(data: String) {
        scope.launch {
            try {
                val msg = JSONObject(data)
                if (msg.getString("type") != "request") return@launch
                val method = msg.getString("method")
                val id = msg.getString("id")
                val args = msg.getJSONArray("args")
                handleMethod(method, id, args)
            } catch (_: Exception) {}
        }
    }

    private fun resolveWithValue(id: String, valueJson: String) {
        val response = "{\"type\":\"response\",\"id\":${JSONObject.quote(id)},\"result\":$valueJson}"
        val js = "window.dispatchEvent(new MessageEvent('message',{data:${JSONObject.quote(response)}}));"
        webView.post { webView.evaluateJavascript(js, null) }
    }

    private fun rejectWithError(id: String, message: String = "오류가 발생했습니다.") {
        val response = "{\"type\":\"response\",\"id\":${JSONObject.quote(id)},\"error\":${JSONObject.quote(message)}}"
        val js = "window.dispatchEvent(new MessageEvent('message',{data:${JSONObject.quote(response)}}));"
        webView.post { webView.evaluateJavascript(js, null) }
    }

    private suspend fun handleMethod(method: String, id: String, args: JSONArray) {
        try {
            when (method) {
                "getAppVersion" -> {
                    resolveWithValue(id, JSONObject.quote(BuildConfig.VERSION_NAME))
                }
                "openExternalBrowser" -> {
                    val url = args.getString(0)
                    openExternal(Uri.parse(url))
                    resolveWithValue(id, "null")
                }
                "shareInviteCode" -> {
                    val code = args.getString(0)
                    val crewName = args.getString(1)
                    val webUrl = BuildConfig.WEB_URL
                    val text = "[AZIT] '${crewName}' 크루에서 당신을 초대했어요! 초대 코드를 입력하고 크루에 가입하세요.\n\n초대 코드: ${code}\n\n${webUrl}/onboarding?inviteCode=${code}"
                    shareText(text)
                    resolveWithValue(id, "null")
                }
                "shareSchedule" -> {
                    val scheduleId = args.getString(0)
                    val webUrl = BuildConfig.WEB_URL
                    val text = "[AZIT 일정] 이번 주 정기런/번개런 일정 확정!\n상세 일정을 확인하고, 지금 바로 AZIT 앱에서 신청해 주세요.\n${webUrl}/schedule/${scheduleId}"
                    shareText(text)
                    resolveWithValue(id, "null")
                }
                "openNaverMap" -> {
                    val address = args.getString(0)
                    val lat = args.getDouble(1)
                    val lng = args.getDouble(2)
                    val appName = "com.azitcrew.app"
                    val naverMapUri = Uri.parse("nmap://place?lat=$lat&lng=$lng&name=${Uri.encode(address)}&appname=${appName}")
                    if (!openExternal(naverMapUri)) {
                        openExternal(Uri.parse("market://details?id=com.nhn.android.nmap"))
                    }
                    resolveWithValue(id, "null")
                }
                "getLocationPermissionStatus" -> {
                    val status = getLocationPermissionStatus()
                    resolveWithValue(id, JSONObject.quote(status))
                }
                "openLocationSettings" -> {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", activity.packageName, null)
                    }
                    activity.startActivity(intent)
                    resolveWithValue(id, "null")
                }
                "getCurrentPosition" -> {
                    val permission = Manifest.permission.ACCESS_FINE_LOCATION
                    val coarsePermission = Manifest.permission.ACCESS_COARSE_LOCATION
                    val hasPermission = ContextCompat.checkSelfPermission(activity, permission) == PackageManager.PERMISSION_GRANTED
                    if (!hasPermission) {
                        activity.getSharedPreferences("azit_bridge", Context.MODE_PRIVATE)
                            .edit().putBoolean("location_asked", true).apply()
                        val granted = activity.requestBridgePermissions(arrayOf(permission, coarsePermission))
                        if (!granted) {
                            rejectWithError(id, "위치 권한이 거부되었습니다.")
                            return
                        }
                    }
                    val location = activity.getBridgeCurrentLocation()
                    if (location != null) {
                        resolveWithValue(id, "{\"latitude\":${location.first},\"longitude\":${location.second}}")
                    } else {
                        rejectWithError(id, "위치를 가져올 수 없습니다.")
                    }
                }
                "pickProfileImage" -> {
                    val source = args.getString(0)
                    val resultJson = when (source) {
                        "camera" -> pickCamera()
                        else -> pickGallery()
                    }
                    resolveWithValue(id, resultJson)
                }
                "socialLogin" -> {
                    val type = args.getString(0)
                    when (type) {
                        "kakao" -> {
                            val result = activity.performKakaoSocialLogin()
                            resolveWithValue(id, result)
                        }
                        else -> resolveWithValue(id, "{\"success\":false,\"message\":\"지원하지 않는 로그인 방식입니다.\"}")
                    }
                }
                else -> rejectWithError(id, "지원하지 않는 메서드: $method")
            }
        } catch (_: Exception) {
            rejectWithError(id)
        }
    }

    private fun openExternal(uri: Uri): Boolean = try {
        activity.startActivity(Intent(Intent.ACTION_VIEW, uri))
        true
    } catch (_: Exception) { false }

    private fun shareText(text: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        activity.startActivity(Intent.createChooser(intent, null))
    }

    private fun getLocationPermissionStatus(): String {
        val prefs = activity.getSharedPreferences("azit_bridge", Context.MODE_PRIVATE)
        val permission = Manifest.permission.ACCESS_FINE_LOCATION
        return when {
            ContextCompat.checkSelfPermission(activity, permission) == PackageManager.PERMISSION_GRANTED -> "granted"
            prefs.getBoolean("location_asked", false) -> "denied"
            else -> "undetermined"
        }
    }

    private suspend fun pickCamera(): String {
        val permission = Manifest.permission.CAMERA
        if (ContextCompat.checkSelfPermission(activity, permission) != PackageManager.PERMISSION_GRANTED) {
            val granted = activity.requestBridgePermissions(arrayOf(permission))
            if (!granted) return "{\"success\":false,\"message\":\"카메라 권한이 필요합니다.\"}"
        }
        val uri = activity.createTempImageUri() ?: return "{\"success\":false,\"message\":\"임시 파일 생성 실패\"}"
        val taken = activity.takeBridgePicture(uri)
        if (!taken) return "{\"success\":false,\"message\":\"이미지 선택이 취소되었습니다.\"}"
        return uriToPickerResult(uri)
    }

    private suspend fun pickGallery(): String {
        // Android 13 (API 33)+ : READ_MEDIA_IMAGES, 그 미만: READ_EXTERNAL_STORAGE
        val permission = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU)
            Manifest.permission.READ_MEDIA_IMAGES
        else
            Manifest.permission.READ_EXTERNAL_STORAGE
        if (ContextCompat.checkSelfPermission(activity, permission) != PackageManager.PERMISSION_GRANTED) {
            val granted = activity.requestBridgePermissions(arrayOf(permission))
            if (!granted) return "{\"success\":false,\"message\":\"갤러리 접근 권한이 필요합니다.\"}"
        }
        val uri = activity.pickBridgeImage() ?: return "{\"success\":false,\"message\":\"이미지 선택이 취소되었습니다.\"}"
        return uriToPickerResult(uri)
    }

    @SuppressLint("NewApi")
    private fun uriToPickerResult(uri: Uri): String {
        return try {
            val inputStream = activity.contentResolver.openInputStream(uri) ?: return "{\"success\":false,\"message\":\"이미지를 읽을 수 없습니다.\"}"
            val bytes = inputStream.use { it.readBytes() }
            val originalBitmap = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            val outputStream = ByteArrayOutputStream()
            originalBitmap.compress(Bitmap.CompressFormat.JPEG, 70, outputStream)
            val compressedBytes = outputStream.toByteArray()
            val base64 = android.util.Base64.encodeToString(compressedBytes, android.util.Base64.NO_WRAP)
            val fileName = "profile_${System.currentTimeMillis()}.jpg"
            val mimeType = "image/jpeg"
            "{\"success\":true,\"base64\":${JSONObject.quote(base64)},\"fileName\":${JSONObject.quote(fileName)},\"mimeType\":${JSONObject.quote(mimeType)}}"
        } catch (_: Exception) {
            "{\"success\":false,\"message\":\"이미지 처리 실패\"}"
        }
    }
}
