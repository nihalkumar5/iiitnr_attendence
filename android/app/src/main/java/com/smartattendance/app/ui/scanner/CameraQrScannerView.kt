package com.smartattendance.app.ui.scanner

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import com.smartattendance.app.ui.theme.BrandAccent
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import org.json.JSONObject
import java.util.concurrent.Executors

data class ScannedQrData(
    val sessionId: String,
    val token: String,
    val classroom: String? = null
)

@OptIn(ExperimentalGetImage::class)
@Composable
fun CameraQrScannerView(
    onQrDetected: (ScannedQrData) -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasScanned by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                val previewView = PreviewView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }

                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()

                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }

                    val options = BarcodeScannerOptions.Builder()
                        .setBarcodeFormats(
                            Barcode.FORMAT_QR_CODE,
                            Barcode.FORMAT_AZTEC,
                            Barcode.FORMAT_DATA_MATRIX
                        )
                        .build()
                    val scanner = BarcodeScanning.getClient(options)

                    val imageAnalysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()

                    val cameraExecutor = Executors.newSingleThreadExecutor()

                    imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                        val mediaImage = imageProxy.image
                        if (mediaImage != null && !hasScanned) {
                            val image = InputImage.fromMediaImage(
                                mediaImage,
                                imageProxy.imageInfo.rotationDegrees
                            )

                            scanner.process(image)
                                .addOnSuccessListener { barcodes ->
                                    for (barcode in barcodes) {
                                        val rawClean = (barcode.rawValue ?: "").trim()
                                        if (rawClean.isEmpty()) continue
                                        Log.d("QrScanner", "Scanned raw QR: $rawClean")

                                        // Parse JSON or delimiter formats
                                        var parsedSessionId = "c921ca2a-bddf-487f-a5c8-55c05929655f"
                                        var parsedToken = ""
                                        var parsedRoom = "Room A-204"

                                        try {
                                            if (rawClean.startsWith("{")) {
                                                val json = JSONObject(rawClean)
                                                parsedSessionId = json.optString("sessionId", json.optString("session_id", parsedSessionId))
                                                parsedToken = json.optString("token", json.optString("qrToken", ""))
                                                parsedRoom = json.optString("classroom", json.optString("room", "Room A-204"))
                                            } else if (rawClean.contains(":")) {
                                                val parts = rawClean.split(":")
                                                if (parts.size >= 3) {
                                                    parsedSessionId = parts[0].trim()
                                                    parsedRoom = parts[1].trim()
                                                    parsedToken = parts[2].trim()
                                                } else {
                                                    parsedToken = parts.last().trim()
                                                }
                                            } else {
                                                parsedToken = rawClean
                                            }

                                            if (parsedToken.isNotEmpty() && !hasScanned) {
                                                hasScanned = true

                                                // Haptic vibration feedback
                                                triggerVibration(ctx)

                                                onQrDetected(
                                                    ScannedQrData(
                                                        sessionId = parsedSessionId,
                                                        token = parsedToken,
                                                        classroom = parsedRoom
                                                    )
                                                )
                                                break
                                            }
                                        } catch (e: Exception) {
                                            Log.e("QrScanner", "Error parsing QR: ${e.message}")
                                        }
                                    }
                                }
                                .addOnCompleteListener {
                                    imageProxy.close()
                                }
                        } else {
                            imageProxy.close()
                        }
                    }

                    try {
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            imageAnalysis
                        )
                    } catch (e: Exception) {
                        Log.e("QrScanner", "Camera binding failed", e)
                    }
                }, ContextCompat.getMainExecutor(ctx))

                previewView
            }
        )

        // Overlay & Viewfinder
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Point camera at Teacher's Screen",
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    modifier = Modifier.padding(bottom = 20.dp)
                )

                // High-visibility viewfinder box
                Box(
                    modifier = Modifier
                        .size(260.dp)
                        .border(2.dp, BrandAccent, RoundedCornerShape(20.dp))
                        .background(Color.White.copy(alpha = 0.04f))
                )

                Text(
                    text = "Scanning for dynamic 30s rotating QR code...",
                    color = Color.White.copy(alpha = 0.75f),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 20.dp)
                )
            }
        }

        // Close Button
        IconButton(
            onClick = onClose,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(24.dp)
                .size(44.dp)
                .background(Color.Black.copy(alpha = 0.5f), CircleShape)
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Close Scanner",
                tint = Color.White
            )
        }
    }
}

private fun triggerVibration(context: Context) {
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator?.vibrate(
                VibrationEffect.createOneShot(120, VibrationEffect.DEFAULT_AMPLITUDE)
            )
        } else {
            @Suppress("DEPRECATION")
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            vibrator?.vibrate(120)
        }
    } catch (e: Exception) {
        Log.w("QrScanner", "Vibration failed: ${e.message}")
    }
}
