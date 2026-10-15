package io.github.calmhertz.gurutu

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.util.Log
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.Style
import org.maplibre.android.snapshotter.MapSnapshotter

object Mapshot {
    private const val TAG = "Mapshot"
    const val credit = "github.com/calmhertz | © Esri | © OSM"
    const val zoom = 17.0
    const val sizePx = 512

    @Volatile
    private var current: MapSnapshotter? = null

    fun snapshot(context: Context, lat: Double, lon: Double, done: (Bitmap?) -> Unit) {
        val appContext = context.applicationContext
        try {
            MapLibre.getInstance(appContext)
        } catch (e: Exception) {
            Log.e(TAG, "MapLibre init failed", e)
            done(null)
            return
        }
        try {
            val style = appContext.resources.openRawResource(R.raw.satellite).bufferedReader().use { it.readText() }
            val options = MapSnapshotter.Options(sizePx, sizePx)
                .withPixelRatio(1f)
                .withLogo(false)
                .withStyleBuilder(Style.Builder().fromJson(style))
                .withCameraPosition(CameraPosition.Builder().target(LatLng(lat, lon)).zoom(zoom).build())
            try {
                current?.cancel()
            } catch (_: Exception) {
            }
            current = null
            val snapshotter = MapSnapshotter(appContext, options)
            current = snapshotter
            snapshotter.start(
                { snapshot ->
                    if (current === snapshotter) {
                        current = null
                    }
                    try {
                        done(drawPin(appContext, snapshot.bitmap))
                    } catch (e: Exception) {
                        Log.e(TAG, "snapshot decode failed", e)
                        done(null)
                    }
                },
                { error ->
                    if (current === snapshotter) {
                        current = null
                    }
                    Log.e(TAG, "snapshot failed: $error")
                    done(null)
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "snapshot start failed", e)
            done(null)
        }
    }

    fun drawPin(context: Context, base: Bitmap): Bitmap {
        val out = base.copy(Bitmap.Config.ARGB_8888, true)
        val pin = try {
            BitmapFactory.decodeResource(context.resources, R.drawable.map_pin_red)
        } catch (_: Exception) {
            null
        }
        if (pin == null) {
            val canvas = Canvas(out)
            val dot = Paint().apply {
                isAntiAlias = true
                color = android.graphics.Color.RED
            }
            canvas.drawCircle(out.width / 2f, out.height / 2f, out.width * 0.045f, dot)
            return out
        }
        drawPinned(Canvas(out), pin, out.width / 2f, out.height / 2f, out.width * 0.1f)
        return out
    }

    fun drawPinned(canvas: Canvas, pin: Bitmap, cx: Float, cy: Float, targetWidth: Float) {
        val w = maxOf(1, (targetWidth).toInt())
        val h = maxOf(1, (pin.height * (targetWidth / pin.width)).toInt())
        val small = Bitmap.createScaledBitmap(pin, w, h, true)
        var tip = small.height - 1
        while (tip > 0 && isRowClear(small, tip)) {
            tip--
        }
        canvas.drawBitmap(small, cx - small.width / 2f, cy - (tip + 1), null)
    }

    private fun isRowClear(bitmap: Bitmap, y: Int): Boolean {
        for (x in 0 until bitmap.width) {
            if ((bitmap.getPixel(x, y) ushr 24) != 0) {
                return false
            }
        }
        return true
    }
}
