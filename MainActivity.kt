package com.movilover.app

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.ConnectivityManager
import android.net.Uri
import android.os.Bundle
import android.os.Message
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.Toast

/**
 * Movi Lover — সাইটকে অ্যাপ হিসেবে চালানোর খোলস (WebView)।
 * সাইট আপডেট করে GitHub Pages-এ push করলেই অ্যাপের ভেতরে নিজে থেকে নতুন সংস্করণ চলে আসে —
 * নতুন APK বানানো লাগে না (শুধু অ্যাপের খোলস বদলালে লাগে)।
 */
@Suppress("DEPRECATION")
class MainActivity : Activity() {

    private lateinit var root: FrameLayout
    private lateinit var web: WebView
    private lateinit var progress: ProgressBar
    private var customView: View? = null
    private var customCallback: WebChromeClient.CustomViewCallback? = null
    private var lastBackAt = 0L
    private var failed = false

    private val siteUrl: String get() = BuildConfig.SITE_URL
    private val siteHost: String get() = Uri.parse(siteUrl).host ?: ""

    @SuppressLint("SetJavaScriptEnabled", "AddJavascriptInterface")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        root = FrameLayout(this).apply { setBackgroundColor(Color.parseColor("#0B0818")) }
        web = WebView(this).apply {
            setBackgroundColor(Color.parseColor("#0B0818"))
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }
        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            visibility = View.GONE
            progressDrawable.setColorFilter(Color.parseColor("#E11D2E"), android.graphics.PorterDuff.Mode.SRC_IN)
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 6)
        }
        root.addView(web)
        root.addView(progress)
        setContentView(root)

        with(web.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            mediaPlaybackRequiresUserGesture = false
            cacheMode = WebSettings.LOAD_DEFAULT
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            loadWithOverviewMode = true
            useWideViewPort = true
            setSupportMultipleWindows(true)
            javaScriptCanOpenWindowsAutomatically = true
            builtInZoomControls = false
            displayZoomControls = false
            setSupportZoom(false)
            // সাইট এই লেখা দেখে বুঝবে সে অ্যাপের ভেতরে চলছে
            userAgentString = userAgentString + " MoviLoverApp/" + BuildConfig.VERSION_NAME
        }
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(web, true)
        }
        web.addJavascriptInterface(Bridge(), "AndroidApp")
        web.overScrollMode = View.OVER_SCROLL_NEVER

        web.setDownloadListener { url, _, _, _, _ -> openExternal(url) }
        web.webViewClient = AppClient()
        web.webChromeClient = AppChrome()

        if (savedInstanceState != null) web.restoreState(savedInstanceState) else loadSite()
    }

    private fun loadSite() {
        failed = false
        web.loadUrl(siteUrl)
    }

    /* ---------- বাইরের লিংক (টেলিগ্রাম, ব্রাউজার ইত্যাদি) ---------- */
    private fun openExternal(url: String): Boolean {
        return try {
            val u = Uri.parse(url)
            val i = if (url.startsWith("intent:")) Intent.parseUri(url, Intent.URI_INTENT_SCHEME) else Intent(Intent.ACTION_VIEW, u)
            i.addCategory(Intent.CATEGORY_BROWSABLE)
            i.component = null
            i.selector = null
            startActivity(i)
            true
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, "এই লিংক খোলার অ্যাপ পাওয়া যায়নি", Toast.LENGTH_SHORT).show(); true
        } catch (e: Exception) { false }
    }

    private fun isInternal(u: Uri): Boolean {
        val h = u.host ?: return u.scheme == "file" || u.scheme == "about" || u.scheme == "data" || u.scheme == "blob"
        return h.equals(siteHost, ignoreCase = true)
    }

    private inner class AppClient : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            if (!request.isForMainFrame) return false
            val u = request.url
            return if (isInternal(u)) false else openExternal(u.toString())
        }

        override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
            progress.visibility = View.VISIBLE
        }

        override fun onPageFinished(view: WebView, url: String?) {
            progress.visibility = View.GONE
        }

        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
            if (request.isForMainFrame && !failed) {
                failed = true
                view.loadUrl("file:///android_asset/offline.html")
            }
        }
    }

    private inner class AppChrome : WebChromeClient() {
        override fun onProgressChanged(view: WebView, newProgress: Int) {
            progress.progress = newProgress
            if (newProgress >= 100) progress.visibility = View.GONE
        }

        // window.open / target=_blank → বাইরে খোলা (অ্যাপের পেজ বদলায় না)
        override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message): Boolean {
            val tmp = WebView(this@MainActivity)
            tmp.webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(v: WebView, r: WebResourceRequest): Boolean {
                    handle(r.url.toString(), v); return true
                }
                @Deprecated("old API") override fun shouldOverrideUrlLoading(v: WebView, url: String): Boolean {
                    handle(url, v); return true
                }
                override fun onPageStarted(v: WebView, url: String?, f: Bitmap?) {
                    if (!url.isNullOrEmpty() && url != "about:blank") handle(url, v)
                }
                private fun handle(url: String, v: WebView) {
                    val u = Uri.parse(url)
                    if (isInternal(u)) web.loadUrl(url) else openExternal(url)
                    v.stopLoading(); v.destroy()
                }
            }
            (resultMsg.obj as WebView.WebViewTransport).webView = tmp
            resultMsg.sendToTarget()
            return true
        }

        // ফুলস্ক্রিন ভিডিও
        override fun onShowCustomView(view: View, callback: CustomViewCallback) {
            if (customView != null) { callback.onCustomViewHidden(); return }
            customView = view; customCallback = callback
            view.setBackgroundColor(Color.BLACK)
            root.addView(view, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            web.visibility = View.GONE
            window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_LAYOUT_STABLE)
        }

        override fun onHideCustomView() { exitFullscreen() }
    }

    private fun exitFullscreen() {
        val v = customView ?: return
        root.removeView(v)
        customView = null
        customCallback?.onCustomViewHidden(); customCallback = null
        web.visibility = View.VISIBLE
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
    }

    inner class Bridge {
        @JavascriptInterface fun retry() {
            runOnUiThread { loadSite() }
        }
    }

    /* ---------- ব্যাক বাটন: ফুলস্ক্রিন → সাইটের ভেতরের ব্যাক (পপআপ/পেজ) → ব্রাউজার হিস্ট্রি → বের হওয়া ---------- */
    override fun onBackPressed() {
        if (customView != null) { exitFullscreen(); return }
        if (failed) { exitApp(); return }
        // সাইট নিজে ব্যাক সামলাতে পারলে (window.__appBack() → true) সেটাই
        web.evaluateJavascript("(function(){try{return window.__appBack?window.__appBack():false}catch(e){return false}})()") { r ->
            if (r == "true") return@evaluateJavascript
            if (web.canGoBack()) web.goBack() else exitApp()
        }
    }

    private fun exitApp() {
        val now = System.currentTimeMillis()
        if (now - lastBackAt < 2000) finish()
        else { lastBackAt = now; Toast.makeText(this, "বের হতে আবার ব্যাক চাপুন", Toast.LENGTH_SHORT).show() }
    }

    override fun onSaveInstanceState(outState: Bundle) { super.onSaveInstanceState(outState); web.saveState(outState) }
    override fun onPause() { web.onPause(); super.onPause() }
    override fun onResume() {
        super.onResume(); web.onResume()
        if (failed && online()) loadSite()   // নেট ফিরলে নিজে থেকে আবার লোড
    }
    override fun onDestroy() { (web.parent as? ViewGroup)?.removeView(web); web.destroy(); super.onDestroy() }

    private fun online(): Boolean {
        val cm = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        return cm.activeNetworkInfo?.isConnected == true
    }
}
