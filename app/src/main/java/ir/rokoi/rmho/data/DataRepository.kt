package ir.rokoi.rmho.data

import android.content.Context
import kotlinx.serialization.json.Json

/**
 * Loads the bundled data.json from assets. The remote-update layer
 * (version check + download + replace) plugs in here next stage —
 * the rest of the app only ever talks to this repository.
 */
class DataRepository(private val context: Context) {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun loadBundled(): DataBundle {
        val raw = context.assets.open("data.json").bufferedReader().use { it.readText() }
        return json.decodeFromString(DataBundle.serializer(), raw)
    }

    companion object {
        /** Remote bundle location (filled in at the data-pipeline stage). */
        const val REMOTE_DATA_URL = ""
        const val REMOTE_VERSION_URL = ""
    }
}
