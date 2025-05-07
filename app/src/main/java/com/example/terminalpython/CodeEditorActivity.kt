package com.example.terminalpython

import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform

class CodeEditorActivity : AppCompatActivity() {
    private lateinit var webView: WebView
    private lateinit var filename: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_code_editor)

        // Extract the filename from Intent
        filename = intent.getStringExtra(EXTRA_FILE) ?: ""

        webView = findViewById(R.id.webViewEditor)
        setupWebView()
        loadEditor()
    }

    private fun setupWebView() {
        webView.settings.javaScriptEnabled = true
        webView.webChromeClient = WebChromeClient()
        webView.webViewClient = WebViewClient()
        webView.addJavascriptInterface(EditorBridge(), "Android")
    }

    private fun loadEditor() {
        webView.loadUrl("file:///android_asset/editor.html")
        // When page loads, inject file contents
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                // Ensure Python is started
                if (!Python.isStarted()) Python.start(AndroidPlatform(this@CodeEditorActivity))
                val py = Python.getInstance()
                val code = py.getModule("terminal").callAttr("read_file", filename).toString().replace("'", "\\'")
                // Call JS to populate editor
                webView.evaluateJavascript("setCodeFromAndroid('" + code + "')", null)
            }
        }
    }

    inner class EditorBridge {
        @JavascriptInterface
        fun receiveCode(code: String) {
            // Save code back to file
            val py = Python.getInstance()
            py.getModule("terminal").callAttr("write_file_contents", filename, code)
        }
    }

    companion object {
        const val EXTRA_FILE = "extra_file_name"
    }
}
