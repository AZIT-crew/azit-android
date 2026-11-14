package com.azitcrew.app.login

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.accessibility.AccessibilityManager
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import com.azitcrew.app.databinding.ActivityLoginBinding
import com.azitcrew.app.webview.WebViewActivity
import com.kakao.sdk.auth.model.OAuthToken
import com.kakao.sdk.common.model.ClientError
import com.kakao.sdk.common.model.ClientErrorCause
import com.kakao.sdk.user.UserApiClient
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    companion object {
        private const val SPLASH_DURATION_MS = 2000L
        private const val BUTTON_FADE_DURATION_MS = 300L
    }

    private lateinit var binding: ActivityLoginBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(bottom = systemBars.bottom)
            insets
        }

        startGradientAnimation()

        lifecycleScope.launch {
            delay(SPLASH_DURATION_MS)
            binding.btnKakaoLogin.animate()
                .alpha(1f)
                .setDuration(BUTTON_FADE_DURATION_MS)
                .start()
        }

        binding.btnKakaoLogin.setOnClickListener { loginWithKakao() }
    }

    private fun startGradientAnimation() {
        val am = getSystemService(ACCESSIBILITY_SERVICE) as AccessibilityManager
        if (am.isEnabled && am.isTouchExplorationEnabled) return

        ObjectAnimator.ofFloat(binding.bgGradientReversed, View.ALPHA, 0f, 1f).apply {
            duration = SPLASH_DURATION_MS
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
    }

    private fun loginWithKakao() {
        val callback: (OAuthToken?, Throwable?) -> Unit = { token, error ->
            if (error == null && token != null) launchWebView(token.accessToken)
        }

        if (UserApiClient.instance.isKakaoTalkLoginAvailable(this)) {
            UserApiClient.instance.loginWithKakaoTalk(this) { token, error ->
                if (error != null) {
                    if (error is ClientError && error.reason == ClientErrorCause.Cancelled) return@loginWithKakaoTalk
                    UserApiClient.instance.loginWithKakaoAccount(this, callback = callback)
                } else {
                    callback(token, null)
                }
            }
        } else {
            UserApiClient.instance.loginWithKakaoAccount(this, callback = callback)
        }
    }

    private fun launchWebView(accessToken: String) {
        startActivity(
            Intent(this, WebViewActivity::class.java).apply {
                putExtra(WebViewActivity.EXTRA_ACCESS_TOKEN, accessToken)
            }
        )
        finish()
    }
}
