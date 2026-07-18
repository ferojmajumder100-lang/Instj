package com.example

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
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
                SolderApp(viewModel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SolderApp(viewModel: ProxyViewModel) {
    val context = LocalContext.current
    val isProxyActive by viewModel.isProxyActive.collectAsStateWithLifecycle()

    val vpnPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.toggleProxy(context, true)
        } else {
            android.widget.Toast.makeText(context, "ভিপিএন পারমিশন দেওয়া হয়নি!", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    // Elegant breathing transition for the active state
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val breathingAlpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )
    val ringScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Scaffold(
        containerColor = Color(0xFFF0F2F5) // Beautiful light layout background
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                // Header Display
                Text(
                    text = "SOLDER",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 4.sp,
                    color = Color(0xFF1877F2), // FB Blue Theme Brand Title
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "Secure Direct Routing",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 1.sp,
                    color = Color(0xFF606770),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(56.dp))

                // The Central Connect Button styled exactly like the Facebook Real Logo
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(240.dp)
                ) {
                    // Pulsating ring when connected
                    if (isProxyActive) {
                        Box(
                            modifier = Modifier
                                .size(160.dp)
                                .scale(ringScale)
                                .clip(CircleShape)
                                .background(Color(0xFF1877F2).copy(alpha = breathingAlpha))
                        )
                    }

                    // Main Circular Facebook Button
                    Box(
                        modifier = Modifier
                            .size(160.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF1877F2))
                            .border(4.dp, Color.White, CircleShape)
                            .clickable {
                                if (!isProxyActive) {
                                    val intent = VpnService.prepare(context)
                                    if (intent != null) {
                                        vpnPermissionLauncher.launch(intent)
                                    } else {
                                        viewModel.toggleProxy(context, true)
                                    }
                                } else {
                                    viewModel.toggleProxy(context, false)
                                }
                            },
                        contentAlignment = Alignment.BottomEnd
                    ) {
                        // Bold white lowercase "f" positioned and offset like the authentic FB logo icon
                        Text(
                            text = "f",
                            color = Color.White,
                            fontSize = 115.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.SansSerif,
                            modifier = Modifier.offset(x = (-12).dp, y = 14.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(48.dp))

                // Status Indicator Board
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(20.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                ) {
                    Column(
                        modifier = Modifier.padding(vertical = 18.dp, horizontal = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "STATUS",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.5.sp,
                            color = Color(0xFF8D949E)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            // Active State green or idle gray dot
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(if (isProxyActive) Color(0xFF31A24C) else Color(0xFFBEC2C9))
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isProxyActive) "SOCKS5 ACTIVE" else "DISCONNECTED",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isProxyActive) Color(0xFF31A24C) else Color(0xFF606770)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                // Instruction tip
                Text(
                    text = if (isProxyActive) "সুরক্ষিত সংযোগ চালু আছে" else "সংযোগ স্থাপন করতে লোগোতে চাপুন",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF8D949E),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
