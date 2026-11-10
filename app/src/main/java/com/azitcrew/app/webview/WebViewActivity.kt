package com.azitcrew.app.webview

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.addCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.azitcrew.app.BuildConfig
import com.azitcrew.app.R
import com.azitcrew.app.databinding.ActivityWebviewBinding

class WebViewActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWebviewBinding
    private val policy = WebPolicy(BuildConfig.WEB_URL)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityWebviewBinding.inflate(layoutInflater)
        setContentView(binding.root)
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

    private fun setupWebView() {
        binding.webView.apply {
            applyWebViewSettings()
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    if (policy.decide(request.url) == WebNavigationDecision.LOAD_IN_APP) return false
                    openExternal(request.url)
                    return true
                }

                override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                    super.onPageStarted(view, url, favicon)
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
