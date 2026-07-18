package com.example

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.net.VpnService
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.theme.*

class SecureEngine : ComponentActivity() {
    private val database by lazy { ProxyDatabase.getDatabase(this) }
    private val repository by lazy { ProxyRepository(database.proxyDao()) }
    private val viewModel by lazy {
        ViewModelProvider(this, ProxyViewModelFactory(repository))[ProxyViewModel::class.java]
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme(dynamicColor = false) {
                ProxyManagerApp(viewModel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProxyManagerApp(viewModel: ProxyViewModel) {
    var selectedTab by remember { mutableStateOf(0) }

    Scaffold(
        containerColor = BackgroundGray,
        bottomBar = {
            NavigationBar(
                containerColor = Color.White,
                tonalElevation = 8.dp,
                modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)
            ) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.Dns, contentDescription = null) },
                    label = { Text("সংযোগ", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Blue600,
                        selectedTextColor = Blue600,
                        indicatorColor = Blue50
                    )
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.FormatListBulleted, contentDescription = null) },
                    label = { Text("প্রোফাইল", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Blue600,
                        selectedTextColor = Blue600,
                        indicatorColor = Blue50
                    )
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.AutoMirrored.Filled.HelpOutline, contentDescription = null) },
                    label = { Text("গাইড", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Blue600,
                        selectedTextColor = Blue600,
                        indicatorColor = Blue50
                    )
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (selectedTab) {
                0 -> ConnectionTab(viewModel)
                1 -> ProfilesTab(viewModel)
                2 -> GuideTab()
            }
        }
    }
}

@Composable
fun ConnectionTab(viewModel: ProxyViewModel) {
    val context = LocalContext.current
    val activeProxy by viewModel.activeProxy.collectAsStateWithLifecycle()
    val isProxyActive by viewModel.isProxyActive.collectAsStateWithLifecycle()
    val testStatus by viewModel.testStatus.collectAsStateWithLifecycle()

    val vpnPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.toggleProxy(context, true)
        } else {
            android.widget.Toast.makeText(context, "ভিপিএন পারমিশন দেওয়া হয়নি!", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val breathingAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            // Hero Status Header Card
            Card(
                colors = CardDefaults.cardColors(containerColor = Slate900),
                shape = RoundedCornerShape(24.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "DIRECT PROXY ROUTING",
                        color = Blue500,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 11.sp,
                        letterSpacing = 2.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (isProxyActive) "প্রক্সি সক্রিয় আছে (VPN ON)" else "প্রক্সি নিষ্ক্রিয় আছে (VPN OFF)",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    // Pulse indicator
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(90.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(75.dp)
                                .scale(if (isProxyActive) breathingAlpha else 1.0f)
                                .clip(CircleShape)
                                .background(
                                    if (isProxyActive) Emerald600.copy(alpha = 0.2f) else WarningRed.copy(alpha = 0.15f)
                                )
                        )
                        Box(
                            modifier = Modifier
                                .size(50.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isProxyActive) Emerald600 else WarningRed
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isProxyActive) Icons.Default.NetworkCheck else Icons.Default.SignalWifiOff,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    if (activeProxy != null) {
                        Surface(
                            color = Slate800,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(
                                        text = activeProxy?.name ?: "Unnamed Profile",
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        fontSize = 14.sp
                                    )
                                    Text(
                                        text = "${activeProxy?.host}:${activeProxy?.port}",
                                        color = Slate400,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 12.sp
                                    )
                                }
                                Surface(
                                    color = if (activeProxy?.type == "SOCKS5") Orange600.copy(alpha = 0.15f) else Blue500.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = activeProxy?.type ?: "HTTP",
                                        color = if (activeProxy?.type == "SOCKS5") Orange600 else Blue500,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    } else {
                        Text(
                            text = "কোনো প্রক্সি প্রোফাইল সিলেক্ট করা নেই",
                            color = Slate400,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = {
                                if (activeProxy == null) {
                                    android.widget.Toast.makeText(context, "প্রথমে একটি প্রোফাইল সিলেক্ট করুন!", android.widget.Toast.LENGTH_SHORT).show()
                                } else {
                                    if (!isProxyActive) {
                                        // Request VPN permissions if needed
                                        val intent = VpnService.prepare(context)
                                        if (intent != null) {
                                            vpnPermissionLauncher.launch(intent)
                                        } else {
                                            viewModel.toggleProxy(context, true)
                                        }
                                    } else {
                                        viewModel.toggleProxy(context, false)
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isProxyActive) WarningRed else Emerald600
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                        ) {
                            Icon(
                                imageVector = if (isProxyActive) Icons.Default.PowerSettingsNew else Icons.Default.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isProxyActive) "বন্ধ করুন (STOP)" else "চালু করুন (CONNECT)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }

                        Button(
                            onClick = { viewModel.testProxyConnection() },
                            colors = ButtonDefaults.buttonColors(containerColor = Blue500),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Speed,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("প্রক্সি টেস্ট করুন", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
            }
        }

        item {
            // Live Status Geolocation Card
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "সংযোগের তথ্য (Live Connection Status)",
                        fontWeight = FontWeight.Bold,
                        color = Slate800,
                        fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    when (val res = testStatus) {
                        is ProxyTestResult.Idle -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Slate100, RoundedCornerShape(12.dp))
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        imageVector = Icons.Default.Explore,
                                        contentDescription = null,
                                        tint = Slate400,
                                        modifier = Modifier.size(36.dp)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "সংযোগ পরীক্ষা করার জন্য 'প্রক্সি টেস্ট করুন' বাটনে চাপ দিন।",
                                        color = Slate600,
                                        fontSize = 12.sp,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }

                        is ProxyTestResult.Loading -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Slate100, RoundedCornerShape(12.dp))
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    CircularProgressIndicator(color = Blue500, strokeWidth = 3.dp)
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = "লেটেন্সি ও আইপি পরীক্ষা করা হচ্ছে...",
                                        color = Slate700,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        is ProxyTestResult.Success -> {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(
                                            if (res.latencyMs < 250) Emerald50 else Orange50,
                                            RoundedCornerShape(8.dp)
                                        )
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(if (res.latencyMs < 250) Emerald600 else Orange600)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "প্রক্সি সচল আছে (অনলাইন) • লেটেন্সি: ${res.latencyMs} ms",
                                        color = if (res.latencyMs < 250) Emerald600 else Orange600,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                }

                                InfoRow(label = "আইপি ঠিকানা (IP Address)", value = res.ip, isCopyable = true, context = context)
                                InfoRow(label = "আইএসপি (ISP)", value = res.isp, icon = Icons.Default.Router)
                                InfoRow(label = "দেশ (Country)", value = "${res.country} 🌍", icon = Icons.Default.Public)
                                InfoRow(label = "সিটি ও অঞ্চল (City / Region)", value = "${res.city}, ${res.region}", icon = Icons.Default.Place)
                            }
                        }

                        is ProxyTestResult.Error -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(WarningRed.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                                    .border(1.dp, WarningRed.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                                    .padding(16.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.ErrorOutline,
                                        contentDescription = null,
                                        tint = WarningRed,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = "সংযোগ ব্যর্থ হয়েছে!",
                                            fontWeight = FontWeight.Bold,
                                            color = WarningRed,
                                            fontSize = 13.sp
                                        )
                                        Text(
                                            text = res.message,
                                            color = Slate700,
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun InfoRow(label: String, value: String, isCopyable: Boolean = false, context: Context? = null, icon: ImageVector? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Slate100, RoundedCornerShape(10.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Slate500,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Column {
                Text(text = label, fontSize = 10.sp, color = Slate500, fontWeight = FontWeight.SemiBold)
                Text(text = value, fontSize = 13.sp, color = Slate900, fontWeight = FontWeight.Bold)
            }
        }

        if (isCopyable && context != null) {
            IconButton(
                onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    val clip = android.content.ClipData.newPlainText("Copied Info", value)
                    clipboard.setPrimaryClip(clip)
                    android.widget.Toast.makeText(context, "Copied: $value", android.widget.Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.size(28.dp)
            ) {
                Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = Blue500, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfilesTab(viewModel: ProxyViewModel) {
    val context = LocalContext.current
    val proxies by viewModel.allProxies.collectAsStateWithLifecycle()
    val activeProxy by viewModel.activeProxy.collectAsStateWithLifecycle()

    var showAddDialog by remember { mutableStateOf(false) }

    // Dialog state variables
    var nameInput by remember { mutableStateOf("") }
    var hostInput by remember { mutableStateOf("") }
    var portInput by remember { mutableStateOf("") }
    var usernameInput by remember { mutableStateOf("") }
    var passwordInput by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf("HTTP") }

    Scaffold(
        containerColor = Color.Transparent,
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    nameInput = ""
                    hostInput = ""
                    portInput = ""
                    usernameInput = ""
                    passwordInput = ""
                    selectedType = "HTTP"
                    showAddDialog = true
                },
                containerColor = Blue600,
                contentColor = Color.White,
                shape = CircleShape,
                modifier = Modifier.testTag("add_proxy_fab")
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Proxy")
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (proxies.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FormatListBulleted,
                            contentDescription = null,
                            tint = Slate400,
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "কোনো প্রক্সি প্রোফাইল যোগ করা নেই",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Slate700
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "নিচের প্লাস (+) বাটনে চাপ দিয়ে একটি প্রক্সি যোগ করুন।",
                            fontSize = 12.sp,
                            color = Slate500,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Text(
                            text = "সংরক্ষিত প্রক্সি প্রোফাইলসমূহ (${proxies.size})",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Slate600,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }

                    items(proxies) { proxy ->
                        val isActive = activeProxy?.id == proxy.id
                        val borderColor by animateColorAsState(
                            targetValue = if (isActive) Blue500 else Color.Transparent,
                            label = "border_color"
                        )

                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.5.dp, borderColor, RoundedCornerShape(16.dp))
                                .clickable {
                                    viewModel.selectProxy(proxy, context)
                                    android.widget.Toast
                                        .makeText(
                                            context,
                                            "${proxy.name} সিলেক্ট করা হয়েছে!",
                                            android.widget.Toast.LENGTH_SHORT
                                        )
                                        .show()
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(if (isActive) Blue50 else Slate100),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = if (proxy.type == "SOCKS5") Icons.Default.SettingsInputHdmi else Icons.Default.SettingsEthernet,
                                            contentDescription = null,
                                            tint = if (isActive) Blue600 else Slate600
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(14.dp))

                                    Column {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = proxy.name,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp,
                                                color = Slate900
                                            )
                                            if (isActive) {
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Surface(
                                                    color = Emerald600,
                                                    shape = RoundedCornerShape(4.dp)
                                                ) {
                                                    Text(
                                                        text = "ACTIVE",
                                                        color = Color.White,
                                                        fontSize = 8.sp,
                                                        fontWeight = FontWeight.ExtraBold,
                                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                    )
                                                }
                                            }
                                        }

                                        Text(
                                            text = "${proxy.host}:${proxy.port}",
                                            fontSize = 12.sp,
                                            color = Slate500,
                                            fontFamily = FontFamily.Monospace,
                                            modifier = Modifier.padding(top = 2.dp)
                                        )
                                    }
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        color = if (proxy.type == "SOCKS5") Orange600.copy(alpha = 0.1f) else Blue500.copy(alpha = 0.1f),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = proxy.type,
                                            color = if (proxy.type == "SOCKS5") Orange600 else Blue500,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 10.sp,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(8.dp))

                                    IconButton(
                                        onClick = {
                                            viewModel.deleteProxy(proxy, context)
                                            android.widget.Toast.makeText(context, "ডিলিট করা হয়েছে!", android.widget.Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Delete",
                                            tint = WarningRed,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (showAddDialog) {
                AlertDialog(
                    onDismissRequest = { showAddDialog = false },
                    title = {
                        Text(
                            text = "নতুন প্রক্সি যুক্ত করুন",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = Slate900
                        )
                    },
                    text = {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = nameInput,
                                onValueChange = { nameInput = it },
                                label = { Text("প্রোফাইল নাম (Label)") },
                                placeholder = { Text("e.g. Premium HK Proxy") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )

                            OutlinedTextField(
                                value = hostInput,
                                onValueChange = { hostInput = it },
                                label = { Text("আইপি বা হোস্ট (Host/IP)") },
                                placeholder = { Text("e.g. 192.168.1.1") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )

                            OutlinedTextField(
                                value = portInput,
                                onValueChange = { portInput = it },
                                label = { Text("পোর্ট (Port)") },
                                placeholder = { Text("e.g. 8080") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth()
                            )

                            OutlinedTextField(
                                value = usernameInput,
                                onValueChange = { usernameInput = it },
                                label = { Text("ইউজারনেম (Username - Optional)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )

                            OutlinedTextField(
                                value = passwordInput,
                                onValueChange = { passwordInput = it },
                                label = { Text("পাসওয়ার্ড (Password - Optional)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )

                            Text(
                                text = "প্রোটোকল সিলেক্ট করুন",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Slate600
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                val borderHttp = if (selectedType == "HTTP") 2.dp else 1.dp
                                val colorHttp = if (selectedType == "HTTP") Blue600 else Slate200
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (selectedType == "HTTP") Blue50 else Color.Transparent)
                                        .border(borderHttp, colorHttp, RoundedCornerShape(8.dp))
                                        .clickable { selectedType = "HTTP" }
                                        .padding(vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("HTTP / HTTPS", fontWeight = FontWeight.Bold, color = if (selectedType == "HTTP") Blue600 else Slate700, fontSize = 12.sp)
                                }

                                val borderSocks = if (selectedType == "SOCKS5") 2.dp else 1.dp
                                val colorSocks = if (selectedType == "SOCKS5") Orange600 else Slate200
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (selectedType == "SOCKS5") Orange50 else Color.Transparent)
                                        .border(borderSocks, colorSocks, RoundedCornerShape(8.dp))
                                        .clickable { selectedType = "SOCKS5" }
                                        .padding(vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("SOCKS5", fontWeight = FontWeight.Bold, color = if (selectedType == "SOCKS5") Orange600 else Slate700, fontSize = 12.sp)
                                }
                            }
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                val parsedPort = portInput.toIntOrNull()
                                if (nameInput.isEmpty() || hostInput.isEmpty() || parsedPort == null) {
                                    android.widget.Toast.makeText(context, "দয়া করে সঠিক নাম, হোস্ট ও পোর্ট দিন!", android.widget.Toast.LENGTH_SHORT).show()
                                } else {
                                    val proxy = ProxyEntity(
                                        name = nameInput,
                                        host = hostInput,
                                        port = parsedPort,
                                        username = usernameInput,
                                        password = passwordInput,
                                        type = selectedType
                                    )
                                    viewModel.addProxy(proxy)
                                    showAddDialog = false
                                    android.widget.Toast.makeText(context, "প্রক্সি সংরক্ষণ করা হয়েছে!", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Blue600)
                        ) {
                            Text("সেভ করুন")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showAddDialog = false }) {
                            Text("বাতিল", color = Slate600)
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun GuideTab() {
    val stepsBengali = listOf(
        "১. প্রক্সি প্রোফাইল যুক্ত করা: 'প্রোফাইল' ট্যাবে যান, (+) আইকনে চাপ দিন এবং আপনার প্রক্সি হোস্ট, পোর্ট এবং লগইন তথ্য দিয়ে সেভ করুন।",
        "২. সংযোগ চালু করা: 'সংযোগ' ট্যাবে গিয়ে কাঙ্ক্ষিত প্রক্সিটি সিলেক্ট করে 'চালু করুন (CONNECT)' বাটনে চাপ দিন। আপনার থেকে প্রথমবার ভিপিএন পারমিশন চাইলে তা অনুমোদন করুন।",
        "৩. ডিভাইস-ওয়াইড রাউটিং: ভিপিএন চালু হওয়ার সাথে সাথে আপনার পুরো ডিভাইসের HTTP/HTTPS রিকোয়েস্ট আমাদের সুরক্ষিত লোকাল টানেলের মাধ্যমে প্রক্সিতে ফরওয়ার্ড করা হবে।",
        "৪. সংযোগের আইপি পরীক্ষা: 'প্রক্সি টেস্ট করুন' বাটনে চাপ দিন। আপনার স্ক্রিনে প্রক্সির আইপি, লেটেন্সি, আইএসপি এবং দেশের লোকেশন লাইভ দেখতে পাবেন।"
    )

    val stepsEnglish = listOf(
        "1. Setting Proxy Profile: Go to 'Profiles' tab, click (+) button, fill host, port and optional auth info, then save.",
        "2. Connecting Proxy: On 'Connection' tab, select your profile and press 'CONNECT'. Approve the VPN connection request on first launch.",
        "3. System-wide Proxy: Once connected, the app routes all device HTTP/HTTPS traffic through our secure local proxy tunnel.",
        "4. Live IP Geolocation Check: Click 'Test Proxy Connection' to audit network latency, ISP, IP address and actual server location."
    )

    var currentLangBengali by remember { mutableStateOf(true) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Slate900),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (currentLangBengali) "ব্যবহার বিধি ও গাইড" else "How To Use & Guide",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Button(
                            onClick = { currentLangBengali = !currentLangBengali },
                            colors = ButtonDefaults.buttonColors(containerColor = Slate700),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
                            modifier = Modifier.height(30.dp),
                            shape = RoundedCornerShape(20.dp)
                        ) {
                            Text(
                                text = if (currentLangBengali) "English" else "বাংলা",
                                fontSize = 11.sp,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (currentLangBengali) "সম্পূর্ণ অ্যাপ্লিকেশনের কাজের বিবরণী নিচে দেওয়া হলো:"
                        else "Comprehensive guide detailing each step of the direct proxy routing setup:",
                        color = Slate400,
                        fontSize = 12.sp
                    )
                }
            }
        }

        val stepsToDisplay = if (currentLangBengali) stepsBengali else stepsEnglish

        items(stepsToDisplay) { step ->
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(Blue50),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = Blue600,
                            modifier = Modifier.size(14.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Text(
                        text = step,
                        fontSize = 13.sp,
                        color = Slate800,
                        fontWeight = FontWeight.Medium,
                        lineHeight = 20.sp
                    )
                }
            }
        }
    }
}
