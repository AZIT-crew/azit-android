package com.azitcrew.app.webview

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.os.Looper
import android.view.View
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.addCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.azitcrew.app.BuildConfig
import com.azitcrew.app.R
import com.azitcrew.app.bridge.AppBridgeInterface
import com.azitcrew.app.databinding.ActivityWebviewBinding
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import java.io.File
import kotlin.coroutines.resume

class WebViewActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_ACCESS_TOKEN = "extra_access_token"
    }

    private lateinit var binding: ActivityWebviewBinding
    private val policy = WebPolicy(BuildConfig.WEB_URL)
    private var accessToken: String? = null

    // Permission 요청 launcher
    private var pendingPermissionCallback: ((Boolean) -> Unit)? = null
    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
        val allGranted = results.values.all { it }
        pendingPermissionCallback?.invoke(allGranted)
        pendingPermissionCallback = null
    }

    // 카메라 launcher
    private var pendingCameraCallback: ((Boolean) -> Unit)? = null
    private var cameraImageUri: Uri? = null
    private val cameraLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        pendingCameraCallback?.invoke(success)
        pendingCameraCallback = null
    }

    // 갤러리 launcher
    private var pendingGalleryCallback: ((Uri?) -> Unit)? = null
    private val galleryLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        pendingGalleryCallback?.invoke(uri)
        pendingGalleryCallback = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityWebviewBinding.inflate(layoutInflater)
        setContentView(binding.root)
        accessToken = intent.getStringExtra(EXTRA_ACCESS_TOKEN)
        // 상·하단 safe area는 웹(env(safe-area-inset-*))이 관리하므로 네이티브 패딩을 적용하지 않는다.
        // targetSdk 35+는 adjustResize가 안 먹으므로 키보드(ime)만 직접 비켜준다
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            view.updatePadding(top = 0, bottom = ime.bottom)
            insets
        }

        setupWebView()

        onBackPressedDispatcher.addCallback(this) {
            if (binding.webView.canGoBack()) {
                binding.webView.goBack()
            } else {
                isEnabled = false
                onBackPressedDispatcher.onBackPressed()
            }
        }

        binding.btnRetry.setOnClickListener {
            binding.layoutError.visibility = View.GONE
            if (binding.webView.url != null) binding.webView.reload() else binding.webView.loadUrl(startUrl(intent))
        }

        if (savedInstanceState != null) {
            binding.webView.restoreState(savedInstanceState)
        } else {
            binding.webView.loadUrl(startUrl(intent))
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        binding.webView.saveState(outState)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.data?.let(policy::webUrl)?.let(binding.webView::loadUrl)
    }

    override fun onDestroy() {
        binding.webView.destroy()
        super.onDestroy()
    }

    private fun startUrl(intent: Intent): String =
        intent.data?.let(policy::webUrl) ?: BuildConfig.WEB_URL

    private fun openExternal(uri: Uri) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, uri))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, R.string.no_app_for_link, Toast.LENGTH_SHORT).show()
        }
    }

    fun createTempImageUri(): Uri? = try {
        val tempFile = File.createTempFile("profile_", ".jpg", cacheDir)
        FileProvider.getUriForFile(this, "${packageName}.provider", tempFile).also {
            cameraImageUri = it
        }
    } catch (_: Exception) { null }

    suspend fun requestBridgePermissions(permissions: Array<String>): Boolean = suspendCancellableCoroutine { cont ->
        pendingPermissionCallback = { granted -> cont.resume(granted) }
        permissionLauncher.launch(permissions)
        cont.invokeOnCancellation { pendingPermissionCallback = null }
    }

    suspend fun takeBridgePicture(uri: Uri): Boolean = suspendCancellableCoroutine { cont ->
        pendingCameraCallback = { success -> cont.resume(success) }
        cameraLauncher.launch(uri)
        cont.invokeOnCancellation { pendingCameraCallback = null }
    }

    suspend fun pickBridgeImage(): Uri? = suspendCancellableCoroutine { cont ->
        pendingGalleryCallback = { uri -> cont.resume(uri) }
        galleryLauncher.launch("image/*")
        cont.invokeOnCancellation { pendingGalleryCallback = null }
    }

    @SuppressLint("MissingPermission")
    suspend fun getBridgeCurrentLocation(): Pair<Double, Double>? = withContext(Dispatchers.IO) {
        suspendCancellableCoroutine { cont ->
            val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val provider = when {
                locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
                else -> { cont.resume(null); return@suspendCancellableCoroutine }
            }
            val listener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    if (!cont.isCompleted) cont.resume(Pair(location.latitude, location.longitude))
                }
                override fun onProviderDisabled(provider: String) {
                    if (!cont.isCompleted) cont.resume(null)
                }
            }
            @Suppress("DEPRECATION")
            locationManager.requestSingleUpdate(provider, listener, Looper.getMainLooper())
            cont.invokeOnCancellation { locationManager.removeUpdates(listener) }
        }
    }

    private fun setupWebView() {
        binding.webView.apply {
            applyWebViewSettings()
            addJavascriptInterface(
                AppBridgeInterface(this@WebViewActivity, this, lifecycleScope),
                "ReactNativeWebView"
            )
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    if (policy.decide(request.url) == WebNavigationDecision.LOAD_IN_APP) return false
                    openExternal(request.url)
                    return true
                }

                override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    accessToken?.let { token ->
                        view.evaluateJavascript("window.__accessToken__ = '${token}';", null)
                    }
                    binding.progressBar.visibility = View.VISIBLE
                    binding.layoutError.visibility = View.GONE
                }

                override fun onPageFinished(view: WebView, url: String?) {
                    super.onPageFinished(view, url)
                    binding.progressBar.visibility = View.GONE
                }

                override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                    super.onReceivedError(view, request, error)
                    if (!request.isForMainFrame) return
                    binding.progressBar.visibility = View.GONE
                    binding.layoutError.visibility = View.VISIBLE
                }
            }
        }
    }
}
