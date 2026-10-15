package io.github.calmhertz.gurutu

import android.content.Context
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import org.json.JSONObject

object Nominatim {
    const val attribution = "© OpenStreetMap contributors"
    const val agent = "Gurutu/1.0"

    private val lock = Any()
    private var lastCall = 0L
    private val memory = LinkedHashMap<String, String>(128, 0.75f, true)
    private const val cacheName = "nominatim_cache.json"
    private const val maxEntries = 200

    fun keyFor(lat: Double, lon: Double): String {
        return String.format(Locale.US, "%.4f,%.4f", lat, lon)
    }

    fun lookup(context: Context, lat: Double, lon: Double): String? {
        val key = keyFor(lat, lon)
        synchronized(lock) {
            memory[key]?.let {
                return it
            }
            readDisk(context)[key]?.let {
                memory[key] = it
                trimMemory()
                return it
            }
            val wait = 1000L - (System.currentTimeMillis() - lastCall)
            if (wait > 0) {
                try {
                    Thread.sleep(wait)
                } catch (_: InterruptedException) {
                }
            }
            lastCall = System.currentTimeMillis()
            try {
                val address = fetch(lat, lon)
                if (address != null) {
                    memory[key] = address
                    trimMemory()
                    writeDisk(context, key, address)
                }
                return address
            } catch (_: Exception) {
                return null
            }
        }
    }

    private fun trimMemory() {
        while (memory.size > maxEntries) {
            val eldest = memory.keys.iterator().next()
            memory.remove(eldest)
        }
    }

    private fun fetch(lat: Double, lon: Double): String? {
        val url = URL("https://nominatim.openstreetmap.org/reverse?format=jsonv2&lat=$lat&lon=$lon&zoom=18&addressdetails=0")
        val conn = url.openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "GET"
            conn.setRequestProperty("User-Agent", agent)
            conn.setRequestProperty("Accept", "application/json")
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            if (conn.responseCode != HttpURLConnection.HTTP_OK) {
                throw IOException("HTTP ${conn.responseCode}")
            }
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val name = JSONObject(body).optString("display_name", "")
            return if (name.isBlank()) null else name
        } finally {
            conn.disconnect()
        }
    }

    private fun cacheFile(context: Context): java.io.File {
        return java.io.File(context.cacheDir, cacheName)
    }

    private fun readDisk(context: Context): Map<String, String> {
        return try {
            val file = cacheFile(context)
            if (!file.exists()) {
                return emptyMap()
            }
            val obj = JSONObject(file.readText())
            val out = LinkedHashMap<String, String>()
            val keys = obj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                out[k] = obj.optString(k, "")
            }
            out
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private fun writeDisk(context: Context, key: String, address: String) {
        try {
            val current = readDisk(context).toMutableMap()
            current[key] = address
            while (current.size > maxEntries) {
                current.remove(current.keys.first())
            }
            val obj = JSONObject()
            for ((k, v) in current) {
                obj.put(k, v)
            }
            cacheFile(context).writeText(obj.toString())
        } catch (_: Exception) {
        }
    }
}
