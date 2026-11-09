package ir.rokoi.rmho.data

import android.content.Context
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Loads the app's data bundle. Local priority: the last remotely-updated
 * bundle cached in filesDir wins; otherwise the bundle shipped in assets.
 * Remote updates are fetched by [UpdateManager] and stored via [cacheBundle].
 */
class DataRepository(private val context: Context) {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val cacheFile: File get() = File(context.filesDir, "data.json")

    fun loadLocal(): DataBundle {
        val raw = if (cacheFile.exists()) {
            cacheFile.readText()
        } else {
            context.assets.open("data.json").bufferedReader().use { it.readText() }
        }
        return json.decodeFromString(DataBundle.serializer(), raw)
    }

    fun cacheBundle(bundle: DataBundle) {
        cacheFile.writeText(json.encodeToString(DataBundle.serializer(), bundle))
    }
}
