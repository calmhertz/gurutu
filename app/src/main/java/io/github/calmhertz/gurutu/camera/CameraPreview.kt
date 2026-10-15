package io.github.calmhertz.gurutu.camera

import androidx.camera.core.CameraInfo
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner

fun backCameraCount(provider: ProcessCameraProvider): Int {
    return provider.availableCameraInfos.count { it.lensFacing == CameraSelector.LENS_FACING_BACK }
}

fun hasFrontCamera(provider: ProcessCameraProvider): Boolean {
    return provider.availableCameraInfos.any { it.lensFacing == CameraSelector.LENS_FACING_FRONT }
}

fun selectorForLens(index: Int, backCount: Int): CameraSelector {
    if (index < backCount) {
        return selectorForBackCamera(index)
    }
    return CameraSelector.DEFAULT_FRONT_CAMERA
}

fun selectorForBackCamera(index: Int): CameraSelector {
    if (index <= 0) {
        return CameraSelector.DEFAULT_BACK_CAMERA
    }
    return CameraSelector.Builder()
        .requireLensFacing(CameraSelector.LENS_FACING_BACK)
        .addCameraFilter { infos: List<CameraInfo> ->
            val backs = infos.filter { it.lensFacing == CameraSelector.LENS_FACING_BACK }
            listOf(backs[index % backs.size])
        }
        .build()
}

@Composable
fun CameraPreview(
    selector: CameraSelector,
    flashMode: Int,
    rotation: Int,
    capture: ImageCapture
) {
    val context = LocalContext.current
    val owner = context as LifecycleOwner
    val preview = remember { Preview.Builder().setResolutionSelector(
        ResolutionSelector.Builder()
            .setAspectRatioStrategy(AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY)
            .build()
    ).build() }
    val boundKey = remember { mutableStateOf<Any?>(null) }
    AndroidView(
        factory = { ctx ->
            PreviewView(ctx).apply {
                scaleType = PreviewView.ScaleType.FIT_CENTER
            }
        },
        modifier = Modifier.fillMaxSize(),
        update = { view ->
            capture.flashMode = flashMode
            capture.targetRotation = rotation
            val key: Any = listOf(selector, flashMode, rotation, capture)
            if (boundKey.value != key) {
                boundKey.value = key
                val future = ProcessCameraProvider.getInstance(context)
                future.addListener(
                    {
                        val provider = future.get()
                        preview.surfaceProvider = view.surfaceProvider
                        provider.unbindAll()
                        provider.bindToLifecycle(owner, selector, preview, capture)
                    },
                    ContextCompat.getMainExecutor(context)
                )
            }
        }
    )
}
