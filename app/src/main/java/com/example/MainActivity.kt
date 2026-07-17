package com.example

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.HttpAuthHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.ProxyConfig
import androidx.webkit.ProxyController
import androidx.webkit.WebViewFeature
import com.example.ui.theme.*
import java.util.concurrent.Executor
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme(dynamicColor = false) {
                InstaUtilApp()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun InstaUtilApp() {
    val context = LocalContext.current
    var webView: WebView? by remember { mutableStateOf(null) }
    var currentUrl by remember { mutableStateOf("https://www.instagram.com") }
    var isLoading by remember { mutableStateOf(false) }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }
    
    var isDesktopMode by remember { mutableStateOf(false) }
    var isProxyEnabled by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    
    val focusManager = LocalFocusManager.current

    val desktopUserAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36"
    val mobileUserAgent = WebSettings.getDefaultUserAgent(context)

    // Proxy Config
    val proxyHost = "change6.owlproxy.com"
    val proxyPort = 7778
    val proxyUser = "117Sz8vwEt70_custom_zone_BD"
    val proxyPass = "3341056"

    LaunchedEffect(isDesktopMode) {
        webView?.settings?.apply {
            userAgentString = if (isDesktopMode) desktopUserAgent else mobileUserAgent
            useWideViewPort = true
            loadWithOverviewMode = true
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
        }
        // Set initial scale to 1 for mobile, but let loadWithOverviewMode handle desktop
        if (!isDesktopMode) {
            webView?.setInitialScale(0)
        } else {
            // For desktop, we might want to force a smaller scale initially to see "everything"
            webView?.setInitialScale(0) 
        }
        webView?.reload()
    }

    LaunchedEffect(isProxyEnabled) {
        if (WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) {
            if (isProxyEnabled) {
                val proxyConfig = ProxyConfig.Builder()
                    .addProxyRule("$proxyHost:$proxyPort")
                    .addDirect().build()
                ProxyController.getInstance().setProxyOverride(proxyConfig, { runnable -> runnable.run() }, {
                    // Proxy set successfully
                })
            } else {
                ProxyController.getInstance().clearProxyOverride({ runnable -> runnable.run() }, {
                    // Proxy cleared successfully
                })
            }
            webView?.reload()
        }
    }

    BackHandler(enabled = canGoBack) {
        webView?.goBack()
    }

    Scaffold(
        containerColor = BackgroundGray,
        topBar = {
            Column(
                modifier = Modifier
                    .statusBarsPadding()
                    .background(Color.White)
                    .padding(vertical = 4.dp, horizontal = 8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    IconButton(
                        onClick = { webView?.goBack() },
                        enabled = canGoBack,
                        modifier = Modifier
                            .size(32.dp)
                            .testTag("back_button")
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack, 
                            contentDescription = "Back",
                            modifier = Modifier.size(20.dp),
                            tint = if (canGoBack) Slate600 else Slate400
                        )
                    }
                    IconButton(
                        onClick = { webView?.goForward() },
                        enabled = canGoForward,
                        modifier = Modifier
                            .size(32.dp)
                            .testTag("forward_button")
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForward, 
                            contentDescription = "Forward",
                            modifier = Modifier.size(20.dp),
                            tint = if (canGoForward) Slate600 else Slate400
                        )
                    }
                    
                    TextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 4.dp)
                            .height(40.dp)
                            .testTag("search_bar"),
                        placeholder = { Text("instagram.com", fontSize = 12.sp, color = Slate400) },
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = BackgroundGray,
                            unfocusedContainerColor = BackgroundGray,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            cursorColor = Blue500
                        ),
                        shape = RoundedCornerShape(20.dp),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = {
                            val url = if (searchQuery.contains(".") && !searchQuery.contains(" ")) {
                                if (searchQuery.startsWith("http")) searchQuery else "https://$searchQuery"
                            } else {
                                "https://www.google.com/search?q=$searchQuery"
                            }
                            webView?.loadUrl(url)
                            focusManager.clearFocus()
                        }),
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = null, tint = Slate400, modifier = Modifier.size(16.dp))
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(
                                    onClick = { searchQuery = "" },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear", tint = Slate400, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    )

                    IconButton(
                        onClick = { webView?.reload() },
                        modifier = Modifier
                            .size(32.dp)
                            .testTag("reload_button")
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Reload", tint = Slate600, modifier = Modifier.size(20.dp))
                    }
                }
                
                if (isLoading) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp)
                            .padding(top = 4.dp),
                        color = Blue500,
                        trackColor = Slate100
                    )
                } else {
                    Spacer(Modifier.height(6.dp))
                    Divider(color = Slate200, thickness = 1.dp)
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Box(modifier = Modifier.weight(1f)) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { context ->
                        WebView(context).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            settings.apply {
                                javaScriptEnabled = true
                                domStorageEnabled = true
                                databaseEnabled = true
                                setSupportZoom(true)
                                builtInZoomControls = true
                                displayZoomControls = false
                            }
                            
                            webViewClient = object : WebViewClient() {
                                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                    isLoading = true
                                    url?.let { currentUrl = it }
                                }

                                override fun onPageFinished(view: WebView?, url: String?) {
                                    isLoading = false
                                    canGoBack = view?.canGoBack() == true
                                    canGoForward = view?.canGoForward() == true
                                    
                                    // Inject JS to force pinch-to-zoom and allow deep zoom-out
                                    val viewportWidth = if (isDesktopMode) "1024" else "device-width"
                                    val initialScale = if (isDesktopMode) "0.4" else "1.0"
                                    view?.evaluateJavascript(
                                        "(function() { " +
                                        "  var meta = document.querySelector('meta[name=\"viewport\"]'); " +
                                        "  var content = 'width=$viewportWidth, initial-scale=$initialScale, minimum-scale=0.1, maximum-scale=5.0, user-scalable=yes'; " +
                                        "  if (meta) { " +
                                        "    meta.setAttribute('content', content); " +
                                        "  } else { " +
                                        "    meta = document.createElement('meta'); " +
                                        "    meta.name = 'viewport'; " +
                                        "    meta.content = content; " +
                                        "    document.getElementsByTagName('head')[0].appendChild(meta); " +
                                        "  } " +
                                        "})();",
                                        null
                                    )
                                }

                                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                    return false
                                }

                                override fun onReceivedHttpAuthRequest(
                                    view: WebView?,
                                    handler: HttpAuthHandler?,
                                    host: String?,
                                    realm: String?
                                ) {
                                    if (isProxyEnabled && host == proxyHost) {
                                        handler?.proceed(proxyUser, proxyPass)
                                    } else {
                                        super.onReceivedHttpAuthRequest(view, handler, host, realm)
                                    }
                                }
                            }
                            
                            webChromeClient = object : WebChromeClient() {
                                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                    // Could show detailed progress if needed
                                }
                            }
                            
                            loadUrl(currentUrl)
                            webView = this
                        }
                    },
                    update = { view ->
                        // Updates are handled via LaunchedEffects
                    }
                )
            }

            // Bento Box Shortcuts
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(BackgroundGray)
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                BentoCard(
                    title = "Desktop",
                    icon = Icons.Default.DesktopWindows,
                    iconBg = Blue50,
                    iconTint = Blue500,
                    isActive = isDesktopMode,
                    onClick = { isDesktopMode = !isDesktopMode },
                    tag = "desktop_mode_card"
                )
                BentoCard(
                    title = "Privacy",
                    icon = Icons.Default.Security,
                    iconBg = Emerald50,
                    iconTint = Emerald600,
                    isActive = isProxyEnabled,
                    onClick = { isProxyEnabled = !isProxyEnabled },
                    tag = "proxy_card"
                )
                BentoCard(
                    title = "Security",
                    icon = Icons.Default.Lock,
                    iconBg = Indigo50,
                    iconTint = Indigo600,
                    onClick = { webView?.loadUrl("https://accountscenter.instagram.com/password_and_security/") },
                    tag = "security_card"
                )
                BentoCard(
                    title = "Profile",
                    icon = Icons.Default.Person,
                    iconBg = Orange50,
                    iconTint = Orange600,
                    onClick = { webView?.loadUrl("https://accountscenter.instagram.com/personal_details/contact_points/") },
                    tag = "profile_card"
                )
            }

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 2.dp),
                shape = RoundedCornerShape(6.dp),
                color = WarningRed,
                shadowElevation = 1.dp
            ) {
                Button(
                    onClick = {
                        webView?.apply {
                            clearCache(true)
                            clearHistory()
                            CookieManager.getInstance().removeAllCookies(null)
                            CookieManager.getInstance().flush()
                            loadUrl("https://www.instagram.com")
                        }
                        isDesktopMode = false
                        isProxyEnabled = false
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(30.dp)
                        .testTag("clear_reload_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Icon(Icons.Default.DeleteForever, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("CLEAR & RELOAD", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 10.sp)
                }
            }
            
            Spacer(Modifier.navigationBarsPadding())
        }
    }
}

@Composable
fun BentoCard(
    title: String,
    icon: ImageVector,
    iconBg: Color,
    iconTint: Color,
    isActive: Boolean = false,
    onClick: () -> Unit,
    tag: String
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(CardWhite)
            .clickable { onClick() }
            .border(
                width = 1.dp,
                color = if (isActive) Blue500 else Slate100,
                shape = RoundedCornerShape(10.dp)
            )
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .testTag(tag)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(iconBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(12.dp)
                )
            }
            Spacer(Modifier.width(6.dp))
            Text(
                text = if (isActive) title.uppercase() else title,
                fontWeight = if (isActive) FontWeight.ExtraBold else FontWeight.Bold,
                fontSize = 10.sp,
                color = if (isActive) Blue500 else Slate700,
                textAlign = TextAlign.Center
            )
        }
    }
}
