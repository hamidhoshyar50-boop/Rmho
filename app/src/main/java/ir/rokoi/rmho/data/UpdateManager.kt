package ir.rokoi.rmho.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * In-app updates, served straight from the public GitHub repo:
 *  - data bundle: data/data.json in the repo (checked via data/version.json)
 *  - app itself: version.json points at the newest APK (a Release asset);
 *    the app downloads it and hands it to the Android installer.
 */
@Serializable
data class RemoteVersion(
    val dataVersion: Int = 0,
    val apkVersionCode: Int = 0,
    val apkVersionName: String = "",
    val apkUrl: String = "",
    val notesEn: String = "",
    val notesFa: String = ""
)

object UpdateManager {

    private const val BASE =
        "https://raw.githubusercontent.com/hamidhoshyar50-boop/Rmho/main/data"
    const val VERSION_URL = "$BASE/version.json"
    const val DATA_URL = "$BASE/data.json"

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private fun httpGetText(url: String): String? {
        val conn = URL(url).openConnection() as HttpURLConnection
        return try {
            conn.connectTimeout = 15_000
            conn.readTimeout = 20_000
            conn.instanceFollowRedirects = true
            if (conn.responseCode != HttpURLConnection.HTTP_OK) return null
            conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    fun fetchVersion(): RemoteVersion? = runCatching {
        httpGetText(VERSION_URL)?.let {
            json.decodeFromString(RemoteVersion.serializer(), it)
        }
    }.getOrNull()

    fun fetchBundle(): DataBundle? = runCatching {
        httpGetText(DATA_URL)?.let {
            json.decodeFromString(DataBundle.serializer(), it)
        }
    }.getOrNull()

    /** Streams [url] into [dest]; reports 0-100 progress when length is known. */
    fun downloadFile(url: String, dest: File, onProgress: (Int) -> Unit): Boolean {
        val conn = URL(url).openConnection() as HttpURLConnection
        return try {
            conn.connectTimeout = 15_000
            conn.readTimeout = 60_000
            conn.instanceFollowRedirects = true
            if (conn.responseCode != HttpURLConnection.HTTP_OK) return false
            val total = conn.contentLengthLong
            dest.parentFile?.mkdirs()
            conn.inputStream.use { input ->
                dest.outputStream().use { output ->
                    val buf = ByteArray(64 * 1024)
                    var done = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        output.write(buf, 0, n)
                        done += n
                        if (total > 0) onProgress((done * 100 / total).toInt())
                    }
                }
            }
            dest.length() > 0
        } catch (e: Exception) {
            false
        } finally {
            conn.disconnect()
        }
    }
}
