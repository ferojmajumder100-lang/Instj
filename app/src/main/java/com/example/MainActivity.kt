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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.draw.scale
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
import android.util.Base64
import androidx.compose.material3.TextField
import com.example.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.*
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
    
    // Security States
    var isAppActive by remember { mutableStateOf(false) }
    var remotePassword by remember { mutableStateOf("") }
    var isIntegrityOk by remember { mutableStateOf(true) }
    
    // Check Integrity on Start
    LaunchedEffect(Unit) {
        val expectedPackage = "com.aistudio.instautil.pkzxwy"
        val expectedAppName = "FB Utility"
        val actualPackage = context.packageName
        val actualAppName = context.getString(R.string.app_name)
        
        if (actualPackage != expectedPackage || actualAppName != expectedAppName) {
            isIntegrityOk = false
        }
    }
    
    // Poll Pastebin every 5s
    LaunchedEffect(Unit) {
        val client = OkHttpClient()
        // https://pastebin.com/raw/hkgf3b24 encoded in Base64
        val rawUrl = String(Base64.decode("aHR0cHM6Ly9wYXN0ZWJpbi5jb20vcmF3L2hrZ2YzYjI0", Base64.DEFAULT))
        
        while (true) {
            try {
                val request = Request.Builder().url(rawUrl).build()
                val response = withContext(Dispatchers.IO) {
                    client.newCall(request).execute()
                }
                
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    if (body.startsWith("{")) {
                        val json = JSONObject(body)
                        val status = json.optString("status", "OFF")
                        remotePassword = json.optString("password", "")
                        isAppActive = status == "ON"
                    } else {
                        isAppActive = body.trim() == "ON"
                    }
                } else {
                    isAppActive = false
                }
            } catch (e: Exception) {
                isAppActive = false
            }
            delay(5000)
        }
    }

    if (!isIntegrityOk) {
        Box(modifier = Modifier.fillMaxSize().background(Color.White), contentAlignment = Alignment.Center) {
            Text("Something went wrong", color = Color.Gray)
        }
        return
    }

    if (!isAppActive) {
        Box(modifier = Modifier.fillMaxSize().background(Color.White)) {
            // Keep it silent as requested
        }
        return
    }

    var webView: WebView? by remember { mutableStateOf(null) }
    var currentUrl by remember { mutableStateOf("https://limited.facebook.com") }
    var isLoading by remember { mutableStateOf(false) }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }
    
    var isDesktopMode by remember { mutableStateOf(false) }
    var isProxyEnabled by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("https://limited.facebook.com") }
    var showCookieLoginDialog by remember { mutableStateOf(false) }
    var showGuideDialog by remember { mutableStateOf(false) }
    var showPasswordDialog by remember { mutableStateOf(false) }
    var passwordInput by remember { mutableStateOf("") }
    var guideLanguage by remember { mutableStateOf("BN") } // "BN" or "EN"
    var cookieInput by remember { mutableStateOf("") }
    var lastUsedCookie by remember { mutableStateOf("") }
    
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val clipboardManager = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager

    val desktopUserAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36"
    val mobileUserAgent = WebSettings.getDefaultUserAgent(context)

    // Proxy Config
    val proxyHost = "change6.owlproxy.com"
    val proxyPort = 7778
    val proxyUser = "iZm3XTj3t830_custom_zone_RE"
    val proxyPass = "5138110"

    LaunchedEffect(isDesktopMode, webView) {
        webView?.let { view ->
            view.settings.apply {
                userAgentString = if (isDesktopMode) desktopUserAgent else mobileUserAgent
                useWideViewPort = true
                loadWithOverviewMode = true
                setSupportZoom(true)
                builtInZoomControls = true
                displayZoomControls = false
            }
            // Set initial scale to 1 for mobile, but let loadWithOverviewMode handle desktop
            view.setInitialScale(0)
            view.reload()
        }
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
            // webView?.reload() // Auto-reload disabled as requested
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
                        placeholder = { Text("facebook.com", fontSize = 12.sp, color = Slate400) },
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
                                    // Update searchQuery only to show the real link in search bar
                                    // without triggering a reload loop
                                    url?.let { searchQuery = it }
                                }

                                override fun onPageFinished(view: WebView?, url: String?) {
                                    isLoading = false
                                    url?.let { searchQuery = it }
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
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier
                    .fillMaxWidth()
                    .background(BackgroundGray)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 4.dp)
            ) {
                item {
                    BentoCard(
                        title = "Guide",
                        icon = Icons.Default.Info,
                        iconBg = Slate100,
                        iconTint = Slate900,
                        onClick = { showGuideDialog = true },
                        tag = "guide_card"
                    )
                }
                item {
                    BentoCard(
                        title = "Desktop",
                        icon = Icons.Default.DesktopWindows,
                        iconBg = Blue50,
                        iconTint = Blue500,
                        showSwitch = true,
                        isActive = isDesktopMode,
                        onClick = { isDesktopMode = !isDesktopMode },
                        tag = "desktop_mode_card"
                    )
                }
                item {
                    BentoCard(
                        title = "Privacy",
                        icon = Icons.Default.Security,
                        iconBg = Emerald50,
                        iconTint = Emerald600,
                        showSwitch = true,
                        isActive = isProxyEnabled,
                        onClick = { isProxyEnabled = !isProxyEnabled },
                        tag = "proxy_card"
                    )
                }
                item {
                    BentoCard(
                        title = "Login",
                        icon = Icons.Default.Login,
                        iconBg = Blue50,
                        iconTint = Blue500,
                        onClick = { showCookieLoginDialog = true },
                        tag = "cookie_login_card"
                    )
                }
                item {
                    BentoCard(
                        title = "Cookie",
                        icon = Icons.Default.ContentCopy,
                        iconBg = Indigo50,
                        iconTint = Indigo600,
                        onClick = {
                            val cookies = CookieManager.getInstance().getCookie(currentUrl)
                            if (cookies != null) {
                                val clip = android.content.ClipData.newPlainText("Cookies", cookies)
                                clipboardManager.setPrimaryClip(clip)
                                android.widget.Toast.makeText(context, "Cookies copied!", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        },
                        tag = "copy_cookie_card"
                    )
                }
                item {
                    BentoCard(
                        title = "UID",
                        icon = Icons.Default.Fingerprint,
                        iconBg = Slate100,
                        iconTint = Slate900,
                        onClick = {
                            val cookies = CookieManager.getInstance().getCookie(currentUrl)
                            if (cookies != null) {
                                val cUser = cookies.split("; ").find { it.startsWith("c_user=") }?.split("=")?.get(1)
                                if (cUser != null) {
                                    val clip = android.content.ClipData.newPlainText("UID", cUser)
                                    clipboardManager.setPrimaryClip(clip)
                                    android.widget.Toast.makeText(context, "UID copied: $cUser", android.widget.Toast.LENGTH_SHORT).show()
                                } else {
                                    android.widget.Toast.makeText(context, "UID not found in cookies!", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        tag = "copy_uid_card"
                    )
                }
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
                            loadUrl("https://limited.facebook.com")
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

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 2.dp),
                shape = RoundedCornerShape(6.dp),
                color = Blue500,
                shadowElevation = 1.dp
            ) {
                Button(
                    onClick = {
                        showPasswordDialog = true
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(30.dp)
                        .testTag("start_ban_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("START BAN", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 10.sp)
                }
            }
            
            if (showPasswordDialog) {
                AlertDialog(
                    onDismissRequest = { 
                        showPasswordDialog = false
                        passwordInput = ""
                    },
                    title = { Text("Enter Password") },
                    text = {
                        Column {
                            Text("Please enter the security password to proceed.")
                            Spacer(Modifier.height(8.dp))
                            TextField(
                                value = passwordInput,
                                onValueChange = { passwordInput = it },
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = { Text("Password") },
                                singleLine = true
                            )
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                if (passwordInput == remotePassword) {
                                    showPasswordDialog = false
                                    passwordInput = ""
                                    scope.launch {
                                        // 1. Data Clear & Reload
                                        webView?.apply {
                                            clearCache(true)
                                            clearHistory()
                                            CookieManager.getInstance().removeAllCookies(null)
                                            CookieManager.getInstance().flush()
                                        }
                                        
                                        // 2. Auto Login with Last Cookie
                                        if (lastUsedCookie.isNotEmpty()) {
                                            val cookieManager = CookieManager.getInstance()
                                            cookieManager.setAcceptCookie(true)
                                            val cookies = lastUsedCookie.split(";")
                                            for (cookie in cookies) {
                                                cookieManager.setCookie("https://.facebook.com", cookie.trim())
                                            }
                                            cookieManager.flush()
                                        }
                                        
                                        webView?.loadUrl("https://limited.facebook.com")
                                        
                                        // 3. Desktop Mode ON then OFF
                                        kotlinx.coroutines.delay(2000) // Wait for page to start loading
                                        isDesktopMode = true
                                        kotlinx.coroutines.delay(1000) // Short stay in desktop mode
                                        isDesktopMode = false
                                    }
                                } else {
                                    android.widget.Toast.makeText(context, "Wrong Password", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            }
                        ) {
                            Text("Confirm")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { 
                            showPasswordDialog = false 
                            passwordInput = ""
                        }) {
                            Text("Cancel")
                        }
                    }
                )
            }
            
            Spacer(Modifier.navigationBarsPadding())
        }

        if (showGuideDialog) {
            AlertDialog(
                onDismissRequest = { showGuideDialog = false },
                title = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (guideLanguage == "BN") "ব্যবহার বিধি" else "How to Use",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Row(
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(20.dp))
                                .padding(2.dp)
                        ) {
                            val btnMod = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .clickable { guideLanguage = if (guideLanguage == "BN") "EN" else "BN" }
                                .padding(horizontal = 12.dp, vertical = 4.dp)
                            
                            Text(
                                text = if (guideLanguage == "BN") "English" else "বাংলা",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = btnMod
                            )
                        }
                    }
                },
                text = {
                    val steps = if (guideLanguage == "BN") {
                        listOf(
                            "১. কুকিজ দিয়ে লগইন করুন।",
                            "২. ইমেইল এড্রেস যোগ করুন।",
                            "৩. চাপ দিয়ে লগ আউট করুন।",
                            "৪. 'START BAN' বাটনে চাপ দিন।",
                            "৫. ৫ সেকেন্ড পর 'Forgot Password' এ যান।",
                            "৬. ইমেইল দিয়ে একাউন্ট সার্চ করুন।",
                            "৭. 'Try Another Way' সিলেক্ট করুন।",
                            "৮. 'No longer have access to these?' সিলেক্ট করুন।",
                            "৯. 'Recover' এ ক্লিক করুন।",
                            "১০. নাম্বারে এসএমএস পাঠিয়ে ওটিপি দিন।",
                            "১১. নতুন পাসওয়ার্ড সেট করুন।"
                        )
                    } else {
                        listOf(
                            "1. Login with Cookies.",
                            "2. Add an Email address.",
                            "3. Press to Logout.",
                            "4. Press 'START BAN' button.",
                            "5. Wait 5s, then go to 'Forgot Password'.",
                            "6. Search account via Email.",
                            "7. Select 'Try Another Way'.",
                            "8. Select 'No longer have access to these?'.",
                            "9. Click 'Recover'.",
                            "10. Send SMS to number & enter OTP.",
                            "11. Set new password."
                        )
                    }
                    LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                        items(steps) { step ->
                            Text(
                                text = step,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showGuideDialog = false }) {
                        Text(if (guideLanguage == "BN") "বন্ধ করুন" else "Close", color = Blue500)
                    }
                }
            )
        }

        if (showCookieLoginDialog) {
            AlertDialog(
                onDismissRequest = { showCookieLoginDialog = false },
                title = { Text("Login with Cookies", fontSize = 16.sp, fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        Text("Paste your cookie string below:", fontSize = 12.sp, color = Slate600)
                        Spacer(Modifier.height(8.dp))
                        TextField(
                            value = cookieInput,
                            onValueChange = { cookieInput = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("datr=...; c_user=...;", fontSize = 10.sp) },
                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 10.sp)
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        if (cookieInput.isNotEmpty()) {
                            val cookieManager = CookieManager.getInstance()
                            cookieManager.setAcceptCookie(true)
                            val cookies = cookieInput.split(";")
                            for (cookie in cookies) {
                                cookieManager.setCookie("https://.facebook.com", cookie.trim())
                            }
                            cookieManager.flush()
                            lastUsedCookie = cookieInput
                            webView?.loadUrl("https://limited.facebook.com")
                            showCookieLoginDialog = false
                            cookieInput = ""
                        }
                    }) {
                        Text("Login", color = Blue500)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showCookieLoginDialog = false }) {
                        Text("Cancel", color = Slate500)
                    }
                }
            )
        }
    }
}

@Composable
fun BentoCard(
    title: String,
    icon: ImageVector,
    iconBg: Color,
    iconTint: Color,
    showSwitch: Boolean = false,
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
                color = if (isActive && showSwitch) Blue500 else Slate100,
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
                text = if (isActive && showSwitch) title.uppercase() else title,
                fontWeight = if (isActive && showSwitch) FontWeight.ExtraBold else FontWeight.Bold,
                fontSize = 10.sp,
                color = if (isActive && showSwitch) Blue500 else Slate700,
                textAlign = TextAlign.Center
            )
            
            if (showSwitch) {
                Spacer(Modifier.width(6.dp))
                Switch(
                    checked = isActive,
                    onCheckedChange = { onClick() },
                    modifier = Modifier.scale(0.5f), // Make the switch very small to fit
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Blue500,
                        uncheckedThumbColor = Slate400,
                        uncheckedTrackColor = Slate200
                    )
                )
            }
        }
    }
}
