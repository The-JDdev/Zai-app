package com.zai.wrapper

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.View
import android.view.animation.AnimationUtils
import android.webkit.CookieManager
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient.FileChooserParams
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.floatingactionbutton.FloatingActionButton
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var progressBar: ProgressBar
    private lateinit var fabHome: FloatingActionButton

    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private var cameraPhotoUri: Uri? = null

    private val targetUrl = "https://chat.z.ai"

    /**
     * JS that surgically hides the site's headers/footers/banners so the WebView
     * feels like a 100% native app. The interval re-applies after SPA route changes.
     */
    private val hideChromeJs = """
        (function() {
            function hideChrome() {
                var selectors = [
                    'header', 'footer', 'nav.topbar', 'nav[class*="topbar"]',
                    '[class*="DesktopHeader"]', '[class*="MobileHeader"]',
                    '[class*="SiteHeader"]', '[class*="SiteFooter"]',
                    '[class*="AppHeader"]', '[class*="AppFooter"]',
                    '[class*="promo-banner"]', '[class*="announcement"]',
                    '[id*="header"]', '[id*="footer"]'
                ];
                selectors.forEach(function(sel) {
                    document.querySelectorAll(sel).forEach(function(el) {
                        if (el.offsetHeight < 220) {
                            el.style.display = 'none !important';
                        }
                    });
                });
                document.body.style.background = '#000';
            }
            hideChrome();
            var observer = new MutationObserver(function() { hideChrome(); });
            observer.observe(document.body, { childList: true, subtree: true });
            setInterval(hideChrome, 2000);
        })();
    """.trimIndent()

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (filePathCallback != null) {
            val baseIntent = Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "*/*"
                addCategory(Intent.CATEGORY_OPENABLE)
                putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            }
            openFilePicker(baseIntent, includeCamera = granted)
        }
    }

    private val fileChooserLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val results: Array<Uri>? = when {
            result.resultCode != Activity.RESULT_OK -> null
            result.data?.data != null -> arrayOf(result.data!!.data!!)
            result.data?.clipData != null -> {
                val clip = result.data!!.clipData!!
                Array(clip.itemCount) { clip.getItemAt(it).uri }
            }
            cameraPhotoUri != null -> arrayOf(cameraPhotoUri!!)
            else -> null
        }
        filePathCallback?.onReceiveValue(results)
        filePathCallback = null
        cameraPhotoUri = null
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Edge-to-edge AMOLED immersive chrome
        WindowCompat.setDecorFitsSystemWindows(window, true)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK

        webView = findViewById(R.id.webView)
        swipeRefresh = findViewById(R.id.swipeRefresh)
        progressBar = findViewById(R.id.progressBar)
        fabHome = findViewById(R.id.fabHome)

        // Cinematic FAB reveal
        fabHome.visibility = View.INVISIBLE
        Handler(Looper.getMainLooper()).postDelayed({
            fabHome.visibility = View.VISIBLE
            fabHome.startAnimation(
                AnimationUtils.loadAnimation(this, android.R.anim.fade_in)
            )
        }, 800)

        setupSwipeRefresh()
        setupWebView()
        setupBackNavigation()
        setupFAB()

        if (savedInstanceState == null) {
            webView.loadUrl(targetUrl)
        } else {
            webView.restoreState(savedInstanceState)
        }
    }

    private fun setupSwipeRefresh() {
        swipeRefresh.setColorSchemeColors(
            ContextCompat.getColor(this, R.color.crimson_red),
            ContextCompat.getColor(this, R.color.neon_yellow),
            ContextCompat.getColor(this, R.color.emerald_green),
            ContextCompat.getColor(this, R.color.deep_blue)
        )
        swipeRefresh.setProgressBackgroundColorSchemeColor(
            ContextCompat.getColor(this, R.color.amoled_black)
        )
        swipeRefresh.setOnRefreshListener { webView.reload() }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            cacheMode = WebSettings.LOAD_DEFAULT
            loadWithOverviewMode = true
            useWideViewPort = true
            setSupportZoom(false)
            builtInZoomControls = false
            displayZoomControls = false
            allowFileAccess = true
            allowContentAccess = true
            mediaPlaybackRequiresUserGesture = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            userAgentString = "$userAgentString ZAIWrapper/1.0"
        }
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        webView.isVerticalScrollBarEnabled = false
        webView.isHorizontalScrollBarEnabled = false
        webView.isFocusable = true
        webView.isFocusableInTouchMode = true
        webView.setBackgroundColor(Color.BLACK)
        webView.isScrollbarFadingEnabled = true

        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest
            ): Boolean {
                val url = request.url.toString()
                return when {
                    // Keep all z.ai traffic in-app (chat, agent, account, etc.)
                    url.contains("z.ai") && (url.startsWith("http://") || url.startsWith("https://")) -> {
                        view.loadUrl(url)
                        false
                    }
                    // External http(s) links also kept in-app for native feel
                    url.startsWith("http://") || url.startsWith("https://") -> {
                        view.loadUrl(url)
                        false
                    }
                    // Deep schemes -> system handler
                    else -> {
                        try {
                            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                        } catch (e: ActivityNotFoundException) {
                            Toast.makeText(
                                this@MainActivity,
                                "No app found to open this link",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        true
                    }
                }
            }

            override fun onPageFinished(view: WebView, url: String?) {
                super.onPageFinished(view, url)
                view.evaluateJavascript(hideChromeJs, null)
                swipeRefresh.isRefreshing = false
            }

            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest,
                error: WebResourceError
            ) {
                super.onReceivedError(view, request, error)
                swipeRefresh.isRefreshing = false
                if (request.isForMainFrame) {
                    Toast.makeText(
                        this@MainActivity,
                        "Connection error — pull down to retry",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                progressBar.progress = newProgress
                progressBar.visibility =
                    if (newProgress in 1..99) View.VISIBLE else View.GONE
            }

            override fun onShowFileChooser(
                webView: WebView,
                callback: ValueCallback<Array<Uri>>,
                params: FileChooserParams
            ): Boolean {
                filePathCallback?.onReceiveValue(null)
                filePathCallback = callback

                val baseIntent = Intent(Intent.ACTION_GET_CONTENT).apply {
                    type = "*/*"
                    addCategory(Intent.CATEGORY_OPENABLE)
                    putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                }

                val wantsCamera = params.acceptTypes.isEmpty() ||
                        params.acceptTypes.any {
                            it.contains("image") || it.equals("*/*", true)
                        }

                if (wantsCamera &&
                    ContextCompat.checkSelfPermission(
                        this@MainActivity,
                        android.Manifest.permission.CAMERA
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
                    return true
                }
                openFilePicker(baseIntent, includeCamera = wantsCamera)
                return true
            }

            override fun onPermissionRequest(request: PermissionRequest) {
                runOnUiThread {
                    val needed = request.resources
                    val needsCamera = needed.contains(PermissionRequest.RESOURCE_VIDEO_CAPTURE)
                    if (needsCamera &&
                        ContextCompat.checkSelfPermission(
                            this@MainActivity,
                            android.Manifest.permission.CAMERA
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        // Ask OS for permission; user must retry the action after granting.
                        cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
                        request.deny()
                    } else {
                        request.grant(needed)
                    }
                }
            }
        }
    }

    private fun openFilePicker(baseIntent: Intent, includeCamera: Boolean) {
        val chooserIntent = Intent.createChooser(baseIntent, "Select file or capture photo")

        if (includeCamera) {
            val captureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            if (captureIntent.resolveActivity(packageManager) != null) {
                try {
                    val photoFile = createImageFile()
                    cameraPhotoUri = FileProvider.getUriForFile(
                        this,
                        "${packageName}.fileprovider",
                        photoFile
                    )
                    captureIntent.putExtra(MediaStore.EXTRA_OUTPUT, cameraPhotoUri)
                    captureIntent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                    chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, arrayOf(captureIntent))
                } catch (_: IOException) {
                    // Camera unavailable — fall back to file picker only
                }
            }
        }

        try {
            fileChooserLauncher.launch(chooserIntent)
        } catch (_: ActivityNotFoundException) {
            filePathCallback?.onReceiveValue(null)
            filePathCallback = null
            Toast.makeText(this, "No file chooser available", Toast.LENGTH_SHORT).show()
        }
    }

    @Throws(IOException::class)
    private fun createImageFile(): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val storageDir = cacheDir
        return File.createTempFile("IMG_${timeStamp}_", ".jpg", storageDir)
    }

    private fun setupFAB() {
        fabHome.setOnClickListener {
            if (webView.url != targetUrl && webView.canGoBack()) {
                webView.goBack()
            } else {
                webView.loadUrl(targetUrl)
                fabHome.startAnimation(
                    AnimationUtils.loadAnimation(this, android.R.anim.fade_in)
                )
            }
        }
        fabHome.setOnLongClickListener {
            webView.reload()
            Toast.makeText(this, "Reloading…", Toast.LENGTH_SHORT).show()
            true
        }
    }

    private fun setupBackNavigation() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) {
                    webView.goBack()
                } else {
                    showExitPrompt()
                }
            }
        })
    }

    private fun showExitPrompt() {
        AlertDialog.Builder(
            this,
            com.google.android.material.R.style.MaterialAlertDialog_Material3
        )
            .setTitle("Exit Z.AI")
            .setMessage("Are you sure you want to exit?")
            .setPositiveButton("Exit") { _, _ -> finishAffinity() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        webView.saveState(outState)
    }

    override fun onPause() {
        super.onPause()
        webView.onPause()
    }

    override fun onResume() {
        super.onResume()
        webView.onResume()
        CookieManager.getInstance().flush()
    }

    override fun onDestroy() {
        webView.apply {
            stopLoading()
            removeAllViews()
            destroy()
        }
        super.onDestroy()
    }
}
