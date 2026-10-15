package io.github.calmhertz.gurutu

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import android.provider.Settings
import android.view.OrientationEventListener
import android.view.Surface
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import io.github.calmhertz.gurutu.camera.CameraPreview
import io.github.calmhertz.gurutu.camera.backCameraCount
import io.github.calmhertz.gurutu.camera.hasFrontCamera
import io.github.calmhertz.gurutu.camera.selectorForLens
import io.github.calmhertz.gurutu.png.Output
import io.github.calmhertz.gurutu.ui.theme.GurutuTheme
import java.time.LocalDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

class MainActivity : ComponentActivity() {

    private var cameraGranted by mutableStateOf(false)
    private var locationGranted by mutableStateOf(false)
    private var flashMode by mutableStateOf(ImageCapture.FLASH_MODE_OFF)
    private var lensIndex by mutableStateOf(0)
    private var backCount by mutableStateOf(1)
    private var hasFront by mutableStateOf(false)
    private var capturing by mutableStateOf(false)
    private var lastUri by mutableStateOf<android.net.Uri?>(null)
    private var thumbBitmap by mutableStateOf<Bitmap?>(null)
    private var lastLocation by mutableStateOf<Location?>(null)
    private var nowTick by mutableStateOf(LocalDateTime.now())
    private var addressText by mutableStateOf<String?>(null)
    private var mapBitmap by mutableStateOf<Bitmap?>(null)
    private var flipped by mutableStateOf(false)
    private var sensorRotation by mutableStateOf(Surface.ROTATION_90)
    private var showLocationPrompt by mutableStateOf(false)
    private var flashTick by mutableStateOf(0)

    private lateinit var capture: ImageCapture
    private var locationManager: LocationManager? = null
    private var orientationListener: OrientationEventListener? = null
    private var pinCache: Bitmap? = null
    private val locationListener = LocationListener { location ->
        lastLocation = location
    }

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            cameraGranted = permissions[Manifest.permission.CAMERA] ?: false
            locationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
            if (!cameraGranted || !locationGranted) {
                Toast.makeText(this, "Permissions denied. App needs them to function.", Toast.LENGTH_LONG).show()
            } else {
                refreshBackCount()
                startLocation()
            }
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }

    override fun onResume() {
        super.onResume()
        if (locationGranted) {
            startLocation()
            if (!isLocationOn()) {
                showLocationPrompt = true
            }
        }
        if (cameraGranted) {
            refreshHistory()
        }
        try {
            if (orientationListener?.canDetectOrientation() == true) {
                orientationListener?.enable()
            }
        } catch (_: Exception) {
        }
    }

    override fun onPause() {
        super.onPause()
        stopLocation()
        try {
            orientationListener?.disable()
        } catch (_: Exception) {
        }
    }

    private fun isLocationOn(): Boolean {
        return try {
            val manager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
            manager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        } catch (_: Exception) {
            false
        }
    }

    @SuppressLint("MissingPermission")
    private fun startLocation() {
        if (!locationGranted) {
            return
        }
        try {
            val manager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
            locationManager = manager
            try {
                manager.getLastKnownLocation(LocationManager.GPS_PROVIDER)?.let {
                    lastLocation = it
                } ?: run {
                    manager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)?.let {
                        lastLocation = it
                    }
                }
            } catch (_: Exception) {
            }
            try {
                if (manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                    manager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 1f, locationListener, Looper.getMainLooper())
                }
            } catch (_: Exception) {
            }
            try {
                if (manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                    manager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 1000L, 1f, locationListener, Looper.getMainLooper())
                }
            } catch (_: Exception) {
            }
        } catch (_: Exception) {
        }
    }

    private fun stopLocation() {
        try {
            locationManager?.removeUpdates(locationListener)
        } catch (_: Exception) {
        }
        locationManager = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        try {
            org.maplibre.android.MapLibre.getInstance(applicationContext)
        } catch (_: Exception) {
        }
        capture = ImageCapture.Builder().setResolutionSelector(
            ResolutionSelector.Builder()
                .setAspectRatioStrategy(AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY)
                .build()
        ).build()
        try {
            pinCache = BitmapFactory.decodeResource(resources, R.drawable.map_pin_red)
        } catch (_: Exception) {
        }
        orientationListener = object : OrientationEventListener(this) {
            override fun onOrientationChanged(degrees: Int) {
                if (degrees == ORIENTATION_UNKNOWN) {
                    return
                }
                when (degrees) {
                    in 60..120 -> {
                        flipped = true
                        sensorRotation = Surface.ROTATION_270
                    }
                    in 240..300 -> {
                        flipped = false
                        sensorRotation = Surface.ROTATION_90
                    }
                }
            }
        }
        checkHardwareAndPermissions()
        setContent {
            GurutuTheme {
                val currentCapture = remember { capture }
                val selector = remember(lensIndex, backCount, hasFront) { selectorForLens(lensIndex, backCount) }
                val previewAlpha = remember { Animatable(1f) }
                val shutterFlash = remember { Animatable(0f) }
                val flipDeg = if (flipped) 180f else 0f
                val maxInfoWidth = LocalConfiguration.current.screenWidthDp.dp * 0.5f
                LaunchedEffect(flashTick) {
                    if (flashTick == 0) {
                        return@LaunchedEffect
                    }
                    try {
                        shutterFlash.snapTo(0.8f)
                        shutterFlash.animateTo(0f, tween(300))
                    } catch (_: Exception) {
                        try {
                            shutterFlash.snapTo(0f)
                        } catch (_: Exception) {
                        }
                    }
                }
                LaunchedEffect(cameraGranted) {
                    if (cameraGranted) {
                        refreshBackCount()
                        refreshHistory()
                    }
                }
                LaunchedEffect(locationGranted) {
                    if (locationGranted) {
                        startLocation()
                    }
                }
                LaunchedEffect(Unit) {
                    while (true) {
                        nowTick = LocalDateTime.now()
                        delay(5000L)
                    }
                }
                val timeText = Output.displayTime(nowTick)
                val coordsText = lastLocation?.let {
                    "%.6f, %.6f".format(it.latitude, it.longitude)
                } ?: "locating…"
                LaunchedEffect(locationGranted) {
                    var fetchedLat = Double.NaN
                    var fetchedLon = Double.NaN
                    while (true) {
                        val loc = lastLocation
                        if (locationGranted && loc != null &&
                            (fetchedLat.isNaN() || abs(loc.latitude - fetchedLat) > 0.0001 || abs(loc.longitude - fetchedLon) > 0.0001)) {
                            fetchedLat = loc.latitude
                            fetchedLon = loc.longitude
                            val address = withContext(Dispatchers.IO) {
                                Nominatim.lookup(applicationContext, loc.latitude, loc.longitude)
                            }
                            addressText = address
                            Mapshot.snapshot(this@MainActivity, loc.latitude, loc.longitude) { bitmap ->
                                if (bitmap != null && Nominatim.keyFor(loc.latitude, loc.longitude) == lastLocation?.let { Nominatim.keyFor(it.latitude, it.longitude) }) {
                                    mapBitmap = bitmap
                                }
                            }
                        }
                        delay(5000L)
                    }
                }
                val addressLine = if (lastLocation == null) "locating…" else addressText ?: "looking up…"
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                ) {
                    if (cameraGranted) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer(alpha = previewAlpha.value, rotationZ = flipDeg)
                        ) {
                            CameraPreview(
                                selector = selector,
                                flashMode = flashMode,
                                rotation = sensorRotation,
                                capture = currentCapture
                            )
                        }
                    } else {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Camera permission required",
                                color = Color.White,
                                fontSize = 16.sp
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .statusBarsPadding()
                            .padding(16.dp)
                            .graphicsLayer(rotationZ = flipDeg)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.Black.copy(alpha = 0.5f))
                            .clickable { flashMode = nextFlash(flashMode) }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = flashLabel(flashMode), color = Color.White, fontSize = 14.sp)
                    }
                    val lensTotal = backCount + if (hasFront) 1 else 0
                    if (lensTotal > 1) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .navigationBarsPadding()
                                .padding(16.dp)
                                .graphicsLayer(rotationZ = flipDeg)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.Black.copy(alpha = 0.5f))
                                .clickable { lensIndex = (lensIndex + 1) % lensTotal }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = "${lensIndex + 1}/$lensTotal", color = Color.White, fontSize = 14.sp)
                        }
                    }
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(horizontal = 20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .graphicsLayer(rotationZ = flipDeg)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = Output.shutterAlpha))
                                .clickable {
                                    lifecycleScope.launch {
                                        try {
                                            previewAlpha.animateTo(0.15f, tween(110))
                                            previewAlpha.animateTo(1f, tween(260))
                                        } catch (_: Exception) {
                                        }
                                    }
                                    flashTick++
                                    takePhoto()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                        }
                    }
                    Row(
                        modifier = Modifier
                            .align(if (flipped) Alignment.TopCenter else Alignment.BottomCenter)
                            .navigationBarsPadding()
                            .padding(vertical = 12.dp)
                            .widthIn(max = maxInfoWidth)
                            .graphicsLayer(rotationZ = flipDeg)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.Black.copy(alpha = Output.boxAlpha))
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val previewMap = mapBitmap
                        if (previewMap != null) {
                            Image(
                                bitmap = previewMap.asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(RoundedCornerShape(6.dp))
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color.Gray),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    painter = painterResource(R.drawable.map_pin_red),
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(24.dp)
                                        .offset(y = (-6).dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = addressLine,
                                color = Color.White,
                                fontSize = 10.sp,
                                maxLines = 2,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(text = timeText, color = Color.White, fontSize = 12.sp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = coordsText,
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Text(
                                text = "${Nominatim.attribution} | Esri World Imagery",
                                color = Color.Gray,
                                fontSize = 10.sp,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        }
                    }
                    val historyThumb = thumbBitmap
                    if (lastUri != null && historyThumb != null) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .navigationBarsPadding()
                                .padding(16.dp)
                                .graphicsLayer(rotationZ = flipDeg),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                bitmap = historyThumb.asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .clickable { openLast() }
                            )
                        }
                    }
                    if (showLocationPrompt) {
                        AlertDialog(
                            onDismissRequest = { showLocationPrompt = false },
                            title = { Text(text = "Location is off") },
                            text = { Text(text = "Turn on location for coordinates and address.") },
                            confirmButton = {
                                TextButton(
                                    onClick = {
                                        showLocationPrompt = false
                                        try {
                                            startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                                        } catch (_: Exception) {
                                        }
                                    }
                                ) {
                                    Text(text = "Open settings")
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { showLocationPrompt = false }) {
                                    Text(text = "Later")
                                }
                            }
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = shutterFlash.value))
                    )
                }
            }
        }
    }

    private fun flashLabel(mode: Int): String {
        return when (mode) {
            ImageCapture.FLASH_MODE_AUTO -> "AUTO"
            ImageCapture.FLASH_MODE_ON -> "ON"
            else -> "OFF"
        }
    }

    private fun nextFlash(mode: Int): Int {
        return when (mode) {
            ImageCapture.FLASH_MODE_OFF -> ImageCapture.FLASH_MODE_AUTO
            ImageCapture.FLASH_MODE_AUTO -> ImageCapture.FLASH_MODE_ON
            else -> ImageCapture.FLASH_MODE_OFF
        }
    }

    private fun refreshBackCount() {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener(
            {
                try {
                    val provider = future.get()
                    val count = backCameraCount(provider)
                    backCount = if (count > 0) count else 1
                    hasFront = hasFrontCamera(provider)
                    if (lensIndex >= backCount + if (hasFront) 1 else 0) {
                        lensIndex = 0
                    }
                } catch (_: Exception) {
                    backCount = 1
                }
            },
            ContextCompat.getMainExecutor(this)
        )
    }

    private fun refreshHistory() {
        lifecycleScope.launch(Dispatchers.IO) {
            val uri = Output.lastImageUri(contentResolver)
            val thumb = uri?.let { Output.loadThumb(contentResolver, it) }
            withContext(Dispatchers.Main) {
                if (thumb != null) {
                    lastUri = uri
                    thumbBitmap = thumb
                } else {
                    lastUri = null
                    thumbBitmap = null
                }
            }
        }
    }

    private fun openLast() {
        val uri = lastUri
        if (uri == null) {
            refreshHistory()
            return
        }
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "image/jpeg")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(this, "No viewer found", Toast.LENGTH_SHORT).show()
        }
    }

    private fun takePhoto() {
        if (!cameraGranted || capturing) {
            return
        }
        capturing = true
        val stampTime = LocalDateTime.now()
        val stampCoords = lastLocation?.let {
            "%.6f, %.6f".format(it.latitude, it.longitude)
        } ?: "no GPS fix"
        val stampAddress = addressText
        val stampMap = mapBitmap
        val stampPin = pinCache
        capture.takePicture(
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    lifecycleScope.launch(Dispatchers.IO) {
                        try {
                            var bitmap = image.toBitmap()
                            val rotation = image.imageInfo.rotationDegrees
                            image.close()
                            if (rotation != 0) {
                                val matrix = Matrix()
                                matrix.postRotate(rotation.toFloat())
                                bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                            }
                            val stamped = Output.stamp(bitmap, stampMap, stampPin, stampAddress, Output.displayTime(stampTime), stampCoords)
                            val uri = Output.saveBitmap(contentResolver, stamped, stampTime)
                            withContext(Dispatchers.Main) {
                                capturing = false
                                if (uri != null) {
                                    refreshHistory()
                                } else {
                                    Toast.makeText(this@MainActivity, "Save failed", Toast.LENGTH_SHORT).show()
                                }
                            }
                        } catch (_: Exception) {
                            try {
                                image.close()
                            } catch (_: Exception) {
                            }
                            withContext(Dispatchers.Main) {
                                capturing = false
                                Toast.makeText(this@MainActivity, "Capture failed", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    capturing = false
                    Toast.makeText(this@MainActivity, "Capture failed", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    private fun checkHardwareAndPermissions() {
        val hasCamera = packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)
        val hasGps = packageManager.hasSystemFeature(PackageManager.FEATURE_LOCATION_GPS)
        if (!hasCamera) {
            Toast.makeText(this, "Warning: No camera detected on this device", Toast.LENGTH_SHORT).show()
        }
        if (!hasGps) {
            Toast.makeText(this, "Warning: No GPS hardware detected on this device", Toast.LENGTH_SHORT).show()
        }
        cameraGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        locationGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarseGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!cameraGranted || !locationGranted || !coarseGranted) {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            requestPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.CAMERA,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        } else {
            refreshBackCount()
            startLocation()
        }
    }
}
