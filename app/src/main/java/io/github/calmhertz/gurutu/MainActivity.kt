package io.github.calmhertz.gurutu

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import io.github.calmhertz.gurutu.ui.theme.GurutuTheme

class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val locationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
            val cameraGranted = permissions[Manifest.permission.CAMERA] ?: false

            if (locationGranted && cameraGranted) {
                Toast.makeText(this, "Permissions granted successfully!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Permissions denied. App needs them to function.", Toast.LENGTH_LONG).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        checkHardwareAndPermissions()

        setContent {
            GurutuTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Hello Gurutu",
                            style = MaterialTheme.typography.headlineLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
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


        val locationPermission = Manifest.permission.ACCESS_FINE_LOCATION
        val cameraPermission = Manifest.permission.CAMERA

        val isLocationGranted = ContextCompat.checkSelfPermission(this, locationPermission) == PackageManager.PERMISSION_GRANTED
        val isCameraGranted = ContextCompat.checkSelfPermission(this, cameraPermission) == PackageManager.PERMISSION_GRANTED


        if (!isLocationGranted || !isCameraGranted) {
            requestPermissionLauncher.launch(
                arrayOf(
                    locationPermission,
                    cameraPermission
                )
            )
        } else {
            Toast.makeText(this, "All permissions already granted!", Toast.LENGTH_SHORT).show()
        }
    }
}