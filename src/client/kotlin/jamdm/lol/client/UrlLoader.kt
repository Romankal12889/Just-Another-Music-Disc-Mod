package jamdm.lol.client

import jamdm.lol.Net
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/** Downloads an audio file from a direct https link. */
object UrlLoader {
    fun fetch(text: String): Pair<String, ByteArray> {
        val uri = try { URI(text.trim()) } catch (e: Exception) { throw IOException("Not a valid link") }
        if (!"https".equals(uri.scheme, ignoreCase = true) || uri.host.isNullOrEmpty()) {
            throw IOException("Only https:// links work")
        }
        val conn = uri.toURL().openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 20_000
        conn.instanceFollowRedirects = true
        conn.setRequestProperty("User-Agent", "JustAnotherMusicDiscMod")
        try {
            if (conn.responseCode !in 200..299) throw IOException("Server answered " + conn.responseCode)
            if (conn.contentLengthLong > Net.MAX_FILE_BYTES) throw IOException("File is bigger than 16 MB")
            val out = ByteArrayOutputStream()
            conn.inputStream.use { input ->
                val buffer = ByteArray(16 * 1024)
                var total = 0
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    total += n
                    if (total > Net.MAX_FILE_BYTES) throw IOException("File is bigger than 16 MB")
                    out.write(buffer, 0, n)
                }
            }
            val last = (uri.path ?: "").substringAfterLast('/')
            val name = URLDecoder.decode(last.substringBeforeLast('.'), StandardCharsets.UTF_8)
                .ifBlank { "Custom Disc" }.take(Net.MAX_NAME_LENGTH)
            return Pair(name, out.toByteArray())
        } finally {
            conn.disconnect()
        }
    }
}
