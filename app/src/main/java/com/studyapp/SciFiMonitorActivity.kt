package com.studyapp

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Bundle
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

class SciFiMonitorActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar
    private lateinit var errorView: View
    private lateinit var errorDetail: TextView

    private val hostUrl = "http://localhost:3000"
    private val altUrl = "http://10.0.2.2:3000"
    private var currentUrl = hostUrl

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sci_fi_webview)

        webView = findViewById(R.id.webView)
        progressBar = findViewById(R.id.progressBar)
        errorView = findViewById(R.id.errorView)
        errorDetail = findViewById(R.id.errorDetail)
        val retryButton = findViewById<Button>(R.id.retryButton)
        val switchUrlButton = findViewById<Button>(R.id.switchUrlButton)

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            useWideViewPort = true
            loadWithOverviewMode = true
        }

        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null)

        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                progressBar.visibility = View.VISIBLE
                errorView.visibility = View.GONE
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                progressBar.visibility = View.GONE
            }

            override fun onReceivedError(
                view: WebView?,
                errorCode: Int,
                description: String?,
                failingUrl: String?
            ) {
                progressBar.visibility = View.GONE
                errorView.visibility = View.VISIBLE
                errorDetail.text = "连接失败: $currentUrl\n($description)"
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                progressBar.progress = newProgress
            }
        }

        retryButton.setOnClickListener { retryLoad() }
        switchUrlButton.setOnClickListener {
            currentUrl = if (currentUrl == hostUrl) altUrl else hostUrl
            switchUrlButton.text = if (currentUrl == hostUrl) "切换备用地址" else "切回默认地址"
            errorDetail.text = "正在连接: $currentUrl"
            errorView.visibility = View.GONE
            webView.loadUrl(currentUrl)
        }

        webView.loadUrl(currentUrl)
    }

    private fun retryLoad() {
        errorView.visibility = View.GONE
        webView.reload()
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }
}
