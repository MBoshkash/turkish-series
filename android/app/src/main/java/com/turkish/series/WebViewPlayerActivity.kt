package com.turkish.series

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import com.turkish.series.databinding.ActivityWebviewPlayerBinding

/**
 * WebView Player - للمصادر اللي بتحتاج JavaScript زي قصة عشق
 */
class WebViewPlayerActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_URL = "url"
        const val EXTRA_TITLE = "title"
    }

    private lateinit var binding: ActivityWebviewPlayerBinding

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWebviewPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val url = intent.getStringExtra(EXTRA_URL) ?: ""
        val title = intent.getStringExtra(EXTRA_TITLE) ?: "المشاهدة"

        setupToolbar(title)
        setupWebView()

        if (url.isNotEmpty()) {
            binding.webView.loadUrl(url)
        }
    }

    private fun setupToolbar(title: String) {
        binding.toolbar.title = title
        binding.toolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        binding.webView.apply {
            settings.apply {
                // تفعيل JavaScript (ضروري للفيديو)
                javaScriptEnabled = true
                javaScriptCanOpenWindowsAutomatically = true

                // تفعيل DOM Storage
                domStorageEnabled = true

                // Media playback
                mediaPlaybackRequiresUserGesture = false

                // Cache
                cacheMode = WebSettings.LOAD_DEFAULT

                // Mixed content (http in https)
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW

                // User Agent (تقليد متصفح عادي)
                userAgentString = "Mozilla/5.0 (Linux; Android 12; Pixel 5) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

                // Allow file access
                allowFileAccess = true
                allowContentAccess = true

                // Enable zoom
                setSupportZoom(true)
                builtInZoomControls = true
                displayZoomControls = false

                // Load images
                loadsImagesAutomatically = true
            }

            // WebViewClient for loading events
            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    binding.loadingProgress.visibility = View.VISIBLE
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    binding.loadingProgress.visibility = View.GONE

                    // Inject CSS to hide ads (optional)
                    injectAdBlocker(view)
                }
            }

            // WebChromeClient for fullscreen video
            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    super.onProgressChanged(view, newProgress)
                    if (newProgress == 100) {
                        binding.loadingProgress.visibility = View.GONE
                    }
                }
            }
        }
    }

    private fun injectAdBlocker(webView: WebView?) {
        // Simple CSS injection to hide common ad elements
        val css = """
            javascript:(function() {
                var style = document.createElement('style');
                style.innerHTML = `
                    [class*="ad"], [class*="Ad"], [id*="ad"], [id*="Ad"],
                    [class*="banner"], [class*="popup"], [class*="modal"],
                    iframe[src*="ad"], div[data-ad] {
                        display: none !important;
                    }
                `;
                document.head.appendChild(style);
            })()
        """.trimIndent()

        webView?.evaluateJavascript(css, null)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (binding.webView.canGoBack()) {
            binding.webView.goBack()
        } else {
            super.onBackPressed()
        }
    }

    override fun onPause() {
        super.onPause()
        binding.webView.onPause()
    }

    override fun onResume() {
        super.onResume()
        binding.webView.onResume()
    }

    override fun onDestroy() {
        super.onDestroy()
        binding.webView.destroy()
    }
}
