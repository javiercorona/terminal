package com.example.terminalpython

import android.os.Bundle
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity

class WebViewActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_PAGE = "extra_page"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val web = WebView(this).apply {
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true

                // Allow local file URLs to load remote content
                allowFileAccess = true
                allowFileAccessFromFileURLs = true
                allowUniversalAccessFromFileURLs = true

                // Permit “mixed content” (file:// → https://)
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            }

            // Ensure we render in this WebView (not a browser)
            webViewClient = WebViewClient()
            // Hook console.log so you can debug JS errors if needed
            webChromeClient = WebChromeClient()
        }

        setContentView(web)

        intent.getStringExtra(EXTRA_PAGE)?.let { page ->
            web.loadUrl("file:///android_asset/$page")
        }
    }
}
