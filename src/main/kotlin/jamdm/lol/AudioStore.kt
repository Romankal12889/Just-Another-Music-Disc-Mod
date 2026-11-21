package jamdm.lol

import net.fabricmc.loader.api.FabricLoader
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest

/** Server-side storage of uploaded audio, keyed by content hash. */
object AudioStore {
    private val HASH_REGEX = Regex("^[0-9a-f]{40}$")

    private val dir: Path by lazy {
        FabricLoader.getInstance().gameDir.resolve("music_table_audio").also { Files.createDirectories(it) }
    }

    fun hashOf(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }.substring(0, 40)

    /** Null if the hash is not well formed (protects against path traversal). */
    fun path(hash: String): Path? = if (HASH_REGEX.matches(hash)) dir.resolve("$hash.bin") else null

    fun save(bytes: ByteArray): String {
        val hash = hashOf(bytes)
        val target = path(hash)!!
        if (!Files.exists(target)) Files.write(target, bytes)
        return hash
    }

    /** Cheap sanity check: Ogg ("OggS") or WAV ("RIFF....WAVE"). */
    fun looksLikeAudio(b: ByteArray): Boolean {
        if (b.size < 16) return false
        val ogg = b[0] == 'O'.code.toByte() && b[1] == 'g'.code.toByte() && b[2] == 'g'.code.toByte() && b[3] == 'S'.code.toByte()
        val wav = String(b, 0, 4, Charsets.US_ASCII) == "RIFF" && String(b, 8, 4, Charsets.US_ASCII) == "WAVE"
        return ogg || wav
    }
}
