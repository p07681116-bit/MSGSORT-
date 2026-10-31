package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.ui.theme.*
import com.example.util.AppLanguage
import com.example.util.AppStrings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanScreen(
    language: AppLanguage,
    onCodeScanned: (String) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
        if (!isGranted) {
            Toast.makeText(context, "Camera permission needed to scan QR codes", Toast.LENGTH_SHORT).show()
        }
    }

    var manualCodeInput by remember { mutableStateOf("") }
    var inputError by remember { mutableStateOf<String?>(null) }
    var isFlashOn by remember { mutableStateOf(false) }

    // Scanner beam animation
    val infiniteTransition = rememberInfiniteTransition(label = "scanner_beam")
    val beamOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "beam_offset"
    )

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Scan SortCode QR",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("scan_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            isFlashOn = !isFlashOn
                            Toast.makeText(context, if (isFlashOn) "Flashlight ON" else "Flashlight OFF", Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Icon(
                            imageVector = if (isFlashOn) Icons.Filled.FlashOn else Icons.Filled.FlashOff,
                            contentDescription = "Torch",
                            tint = if (isFlashOn) AmberGold else MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color(0xFF0B0F19))
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Instructions
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "Align QR Code inside Viewfinder",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Point camera at any MsgSort QR code to instantly decrypt and view content.",
                    color = LightSlate,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )
            }

            // High-Tech Viewfinder Box
            Box(
                modifier = Modifier
                    .size(280.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0xFF131B2E))
                    .border(2.dp, RoyalIndigo.copy(alpha = 0.4f), RoundedCornerShape(24.dp)),
                contentAlignment = Alignment.Center
            ) {
                // Viewfinder grid & reticles
                Box(
                    modifier = Modifier
                        .size(240.dp)
                        .border(1.5.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(16.dp))
                )

                // Corner Reticles
                CornerReticle(Alignment.TopStart)
                CornerReticle(Alignment.TopEnd)
                CornerReticle(Alignment.BottomStart)
                CornerReticle(Alignment.BottomEnd)

                // Animated Laser Scanning Beam
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .align(Alignment.TopCenter)
                        .graphicsLayer { translationY = beamOffset * 270.dp.toPx() }
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color.Transparent, Color(0xFF06B6D4), RoyalIndigo, Color(0xFF06B6D4), Color.Transparent)
                            )
                        )
                )

                if (!hasCameraPermission) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(ContainerIndigo),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.CameraAlt, contentDescription = null, tint = RoyalIndigo)
                        }
                        Text(
                            text = "Camera Access Required",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Button(
                            onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = RoyalIndigo)
                        ) {
                            Text("Enable Camera", fontSize = 12.sp)
                        }
                    }
                } else {
                    Text(
                        text = "Scanning for MsgSort QR...",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            // Interactive Simulator & Fallback Controls
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Testing simulation button for emulator testing
                AssistChip(
                    onClick = {
                        val sampleCode = "MS-" + (1000..9999).random()
                        onCodeScanned(sampleCode)
                    },
                    label = { Text("⚡ Simulate Test QR Scan", fontSize = 11.sp, color = RoyalIndigo) },
                    leadingIcon = {
                        Icon(Icons.Filled.QrCode, contentDescription = null, tint = RoyalIndigo, modifier = Modifier.size(16.dp))
                    },
                    shape = RoundedCornerShape(16.dp),
                    colors = AssistChipDefaults.assistChipColors(containerColor = ContainerIndigo),
                    border = BorderStroke(1.dp, RoyalIndigo.copy(alpha = 0.3f))
                )

                // Manual Entry Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF131B2E)),
                    border = BorderStroke(1.dp, Color(0xFF1E293B))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Or Enter SortCode Manually",
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )

                        OutlinedTextField(
                            value = manualCodeInput,
                            onValueChange = {
                                manualCodeInput = it.uppercase()
                                inputError = null
                            },
                            placeholder = { Text("e.g. MS-7X9K2", color = SlateMuted) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("scan_manual_input"),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true,
                            leadingIcon = {
                                Icon(Icons.Filled.Key, contentDescription = null, tint = RoyalIndigo)
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = RoyalIndigo,
                                unfocusedBorderColor = Color(0xFF334155),
                                focusedContainerColor = Color(0xFF0F172A),
                                unfocusedContainerColor = Color(0xFF0F172A)
                            ),
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Characters,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    if (manualCodeInput.trim().length >= 4) {
                                        onCodeScanned(manualCodeInput.trim())
                                    } else {
                                        inputError = "Please enter a valid SortCode (min 4 characters)"
                                    }
                                }
                            )
                        )

                        if (inputError != null) {
                            Text(
                                text = inputError ?: "",
                                color = ErrorRed,
                                fontSize = 11.sp
                            )
                        }

                        Button(
                            onClick = {
                                val trimmed = manualCodeInput.trim()
                                if (trimmed.length >= 4) {
                                    onCodeScanned(trimmed)
                                } else {
                                    inputError = "Please enter a valid SortCode (min 4 characters)"
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("scan_open_button"),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = RoyalIndigo)
                        ) {
                            Icon(Icons.Filled.LockOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Open Share", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BoxScope.CornerReticle(alignment: Alignment) {
    Box(
        modifier = Modifier
            .size(24.dp)
            .align(alignment)
            .padding(4.dp)
            .border(
                width = 3.dp,
                color = Color(0xFF06B6D4),
                shape = RoundedCornerShape(4.dp)
            )
    )
}
