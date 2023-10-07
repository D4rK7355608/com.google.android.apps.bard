package com.google.android.apps.bard
import android.annotation.SuppressLint
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.window.OnBackInvokedDispatcher
import android.content.SharedPreferences
import android.webkit.CookieManager
import android.webkit.WebSettings
import com.google.android.apps.bard.databinding.ActivityMainBinding
import com.google.android.material.snackbar.Snackbar
import me.zhanghai.android.fastscroll.FastScrollerBuilder
class MainActivity : Activity() {
    private val userAgent = "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/117.0.5938.60 Mobile Safari/537.36"
    private val chatUrl = "https://bard.google.com/"
    private lateinit var binding: ActivityMainBinding
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var cookieManager: CookieManager
    @SuppressLint("SetJavaScriptEnabled")
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        binding.webView.saveState(outState)
    }
    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        sharedPreferences = getSharedPreferences("MyPrefs", Context.MODE_PRIVATE)
        cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(binding.webView, true)
        initializeFastScroller()
        registerBackButtonCallback()
        val userAccount = getUserAccount()
        if (userAccount.isNotEmpty()) {
            binding.webView.loadUrl(userAccount)
        } else {
            binding.webView.loadUrl(chatUrl)
        }
        if (savedInstanceState != null) {
            binding.webView.restoreState(savedInstanceState)
        }
        configureWebViewSettings()
        setupWebViewInterface()
        setupWebViewClient()
        binding.swipeRefreshLayout.setOnRefreshListener {
            binding.webView.reload()
            binding.swipeRefreshLayout.isRefreshing = false
        }
    }
    private fun initializeFastScroller() {
        FastScrollerBuilder(binding.webView).useMd2Style().build()
    }
    private fun registerBackButtonCallback() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            onBackInvokedDispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT) {
                if (binding.webView.canGoBack()) {
                    binding.webView.goBack()
                } else {
                    finish()
                }
            }
        }
    }
    @SuppressLint("SetJavaScriptEnabled")
    private fun configureWebViewSettings() {
        binding.webView.settings.apply {
            cacheMode = WebSettings.LOAD_DEFAULT
            userAgentString = userAgent
            domStorageEnabled = true
            javaScriptEnabled = true
        }
    }
    private fun setupWebViewInterface() {
        binding.webView.addJavascriptInterface(WebViewInterface(this), "Android")
    }
    private fun setupWebViewClient() {
        binding.webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val urlString = request?.url?.toString() ?: return false
                if (urlString == "alert://alert") {
                    Snackbar.make(binding.root, "alert", Snackbar.LENGTH_LONG).show()
                } else if (urlString == "choose://image") {
                    val intent = Intent(Intent.ACTION_GET_CONTENT)
                    intent.addCategory(Intent.CATEGORY_OPENABLE)
                    intent.type = "image/*"
                    startActivityForResult(intent, 1)
                    return true
                }
                if (urlString.contains(chatUrl)) {
                    return false
                }
                if (binding.webView.url?.contains(chatUrl) == true && !binding.webView.url.toString().contains("/auth")) {
                    view?.loadUrl(urlString)
                    return true
                }
                return false
            }
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                if (binding.webView.url?.contains(chatUrl) == true && !binding.webView.url.toString().contains("/auth")) {
                    val userAccount = extractUserAccountFromWebPage()
                    if (userAccount.isNotEmpty()) {
                        saveUserAccount(userAccount)
                    }
                }
                binding.swipeRefreshLayout.isRefreshing = false
                binding.swipeRefreshLayout.isEnabled = !(binding.webView.url?.contains(chatUrl) == true && !binding.webView.url.toString().contains("/auth"))
                binding.webView.evaluateJavascript("""
                (() => {
                  navigator.clipboard.writeText = (text) => {
                        Android.copyToClipboard(text);
                        return Promise.resolve();
                    }
                })();
                """.trimIndent(), null
                )
            }
        }
    }
    private fun extractUserAccountFromWebPage(): String {
        var userAccount = ""
        binding.webView.evaluateJavascript("""
        (function() {
            // Replace this code with the appropriate JavaScript code to extract the account information
            // Example: If the account is stored in a DOM element with id "account", you can use the following code:
            var accountElement = document.getElementById('account');
            if (accountElement) {
                return accountElement.innerText;
            }
            return '';
        })();
        """) { result ->
            userAccount = result?.trim('"') ?: ""
        }
        return userAccount
    }
    private fun saveUserAccount(userAccount: String) {
        sharedPreferences.edit().putString("userAccount", userAccount).apply()
    }
    override fun onPause() {
        super.onPause()
        val cookies = cookieManager.getCookie(chatUrl)
        sharedPreferences.edit().putString("cookies", cookies).apply()
    }
    override fun onResume() {
        super.onResume()
        val cookies = sharedPreferences.getString("cookies", "")
        if (!cookies.isNullOrEmpty()) {
            cookieManager.removeAllCookies(null)
            cookieManager.flush()
            val cookieArray = cookies.split(";")
            for (cookie in cookieArray) {
                cookieManager.setCookie(chatUrl, cookie)
            }
            cookieManager.flush()
        }
    }
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        @Suppress("DEPRECATION")
        if (binding.webView.canGoBack() && Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU)
            binding.webView.goBack()
        else
            super.onBackPressed()
    }
    private class WebViewInterface(private val context: Context) {
        @JavascriptInterface
        fun copyToClipboard(text: String) {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Copied!", text)
            clipboard.setPrimaryClip(clip)
        }
    }
    private fun getUserAccount(): String {
        return sharedPreferences.getString("userAccount", "") ?: ""
    }
}