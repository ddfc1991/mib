package com.openminis.app

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.window.OnBackInvokedDispatcher
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat

/**
 * destiny — the WebView shell that owns the product's visible UI.
 *
 * Why a WebView instead of porting the design into Compose: the redesign is
 * 56 screens of a single design system (tokens, one SVG sprite, one set of
 * row/pill/ring primitives). Keeping it as one HTML document means the whole
 * visual layer is one artifact that can be iterated in minutes, while the
 * existing Compose app keeps doing what it already does well — sandbox,
 * providers, MCP, terminal, plugins. The three launcher aliases point at THIS
 * activity, so the icon the user taps lands here; anything that has to
 * actually *do* something hands back to [MainActivity] through the bridge.
 *
 * Failure mode that matters: this activity is the launcher entry, so if the
 * WebView never comes up the app would look bricked. Every step below is
 * therefore wrapped — on any exception we forward straight to MainActivity
 * and finish, so the user always ends up in a working screen.
 */
class DestinyWebActivity : Activity() {

    private var web: WebView? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val ink = Color.parseColor(INK)
        window.statusBarColor = ink
        window.navigationBarColor = ink

        val view = try {
            WebView(this).apply {
                setBackgroundColor(ink)
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    // The whole UI is one file under assets/destiny/. No
                    // network, no remote origin: file access stays local.
                    allowFileAccess = true
                    allowContentAccess = false
                    mediaPlaybackRequiresUserGesture = true
                    cacheMode = WebSettings.LOAD_DEFAULT
                    // Let the page own its own scaling — the design is a
                    // device-width document with a viewport meta already.
                    useWideViewPort = true
                    loadWithOverviewMode = false
                    textZoom = 100
                }
                webViewClient = WebViewClient()
                webChromeClient = WebChromeClient()
                addJavascriptInterface(Bridge(), BRIDGE)
                loadUrl(ASSET_URL)
            }
        } catch (t: Throwable) {
            Log.e(TAG, "WebView unavailable, falling back to native UI", t)
            fallbackToNative("")
            return
        }

        web = view
        val root = FrameLayout(this).apply {
            setBackgroundColor(ink)
            addView(
                view,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
        }

        // targetSdk 35 forces edge-to-edge on Android 15 and up, while older
        // releases still inset the content view themselves. Rather than let
        // the header end up under the status bar on one version and not the
        // other, take over the window explicitly and pad by the reported
        // system bars. The page therefore does NOT need env(safe-area-inset-*)
        // — adding both would double the gap.
        WindowCompat.setDecorFitsSystemWindows(window, false)
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            v.setPadding(0, bars.top, 0, bars.bottom)
            WindowInsetsCompat.CONSUMED
        }
        setContentView(root)

        // API 33+: the app opts into the new back pipeline, so onBackPressed
        // is dead there and this callback is what receives the gesture.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            onBackInvokedDispatcher.registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_DEFAULT
            ) { handleBack() }
        }
    }

    /** Everything the page is allowed to ask the native side to do. */
    inner class Bridge {

        /**
         * Fired on every screen change. Currently only a hook — kept so that
         * analytics or a per-screen native affordance can be added without
         * touching the page again.
         */
        @JavascriptInterface
        fun onScreen(id: String, title: String) {
            Log.d(TAG, "screen=$id title=$title")
        }

        /**
         * Layout / runtime self-check channel. The page measures every layer
         * and sends the numbers here; without this a collapsed layout still
         * looks like "screen=01" in the log while the screen is black.
         */
        @JavascriptInterface
        fun log(msg: String) {
            Log.d(TAG, msg ?: "")
        }

        /** Back at the first screen: let the system close the app. */
        @JavascriptInterface
        fun exitApp() {
            runOnUiThread { finish() }
        }

        /**
         * Hand off to the Compose side. [screen] is a free-form tag the page
         * uses to say where the user was going ("chat", "terminal", ...);
         * MainActivity ignores unknown values.
         */
        @JavascriptInterface
        fun openNative(screen: String) {
            runOnUiThread { fallbackToNative(screen ?: "") }
        }
    }

    private fun fallbackToNative(screen: String) {
        try {
            val i = Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            if (screen.isNotEmpty()) i.putExtra(EXTRA_NATIVE_SCREEN, screen)
            startActivity(i)
        } catch (t: Throwable) {
            Log.e(TAG, "cannot reach MainActivity", t)
        }
        finish()
    }

    /**
     * Back has to walk the page's own stack first: the app keeps its own
     * history (the screen directory sits on top of screens), so the JS side
     * owns the decision. `appBack()` returns true when it consumed the press.
     *
     * The manifest sets android:enableOnBackInvokedCallback="true" on
     * <application>, which means on API 33+ [onBackPressed] is never called —
     * the dispatcher path below is the one that actually runs there. Both
     * paths funnel into [handleBack], so behaviour is identical either way.
     */
    private fun handleBack() {
        val w = web
        if (w == null) {
            finish()
            return
        }
        w.evaluateJavascript("(window.appBack && window.appBack()) ? '1' : '0'") { result ->
            if (result?.contains('1') != true) finish()
        }
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        // Pre-33 only (see above). Never delegate to super: that would close
        // the app even when the page still had a screen to pop.
        handleBack()
    }

    override fun onDestroy() {
        web?.let {
            (it.parent as? ViewGroup)?.removeView(it)
            it.destroy()
        }
        web = null
        super.onDestroy()
    }

    private companion object {
        const val TAG = "DestinyWeb"
        const val BRIDGE = "DestinyNative"
        const val ASSET_URL = "file:///android_asset/destiny/index.html"
        const val INK = "#08080A"

        /** Consumed by MainActivity if it wants to land on a specific tab. */
        const val EXTRA_NATIVE_SCREEN = "destiny_native_screen"
    }
}
