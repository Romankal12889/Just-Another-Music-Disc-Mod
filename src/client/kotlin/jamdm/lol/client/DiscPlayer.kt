package jamdm.lol.client

import jamdm.lol.AudioChunkPayload
import jamdm.lol.AudioStore
import jamdm.lol.MusicTableMod
import jamdm.lol.PlayDiscPayload
import jamdm.lol.RequestAudioPayload
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path

/** Client side of jukebox playback: cache lookup, download on demand, start/stop sounds. */
object DiscPlayer {
    private val cacheDir: Path by lazy {
        FabricLoader.getInstance().gameDir.resolve("music_table_cache").also { Files.createDirectories(it) }
    }

    private val playing = HashMap<BlockPos, DiscSoundInstance>()
    private val wanted = HashMap<BlockPos, PlayDiscPayload>()
    private val requested = HashSet<String>()
    private val incoming = HashMap<String, Array<ByteArray?>>()

    private fun file(hash: String): Path? =
        if (AudioStore.path(hash) != null) cacheDir.resolve("$hash.bin") else null

    fun onPlay(p: PlayDiscPayload) {
        val f = file(p.hash) ?: return
        stopSound(p.pos)
        wanted[p.pos] = p
        if (Files.exists(f)) {
            start(p, f)
        } else if (requested.add(p.hash)) {
            ClientPlayNetworking.send(RequestAudioPayload(p.hash))
        }
    }

    fun onStop(pos: BlockPos) {
        wanted.remove(pos)
        stopSound(pos)
    }

    fun onChunk(c: AudioChunkPayload) {
        val f = file(c.hash) ?: return
        if (c.count <= 0 || c.count > 200 || c.index !in 0 until c.count) return
        val parts = incoming.getOrPut(c.hash) { arrayOfNulls(c.count) }
        if (parts.size != c.count) return
        parts[c.index] = c.data
        if (parts.any { it == null }) return

        incoming.remove(c.hash)
        requested.remove(c.hash)
        val out = ByteArrayOutputStream()
        parts.forEach { out.write(it!!) }
        val bytes = out.toByteArray()
        if (AudioStore.hashOf(bytes) != c.hash) {
            MusicTableMod.LOGGER.warn("Downloaded disc audio failed its hash check")
            return
        }
        Files.write(f, bytes)
        for (p in wanted.values.toList()) {
            if (p.hash == c.hash && p.pos !in playing) start(p, f)
        }
    }

    fun clear() {
        playing.keys.toList().forEach { stopSound(it) }
        wanted.clear()
        requested.clear()
        incoming.clear()
    }

    private fun start(p: PlayDiscPayload, f: Path) {
        val instance = DiscSoundInstance(p.pos)
        playing[p.pos] = instance
        DiscSoundHook.offer(f, p.offsetTicks)
        Minecraft.getInstance().soundManager.play(instance)
        DiscSoundHook.clearPending()
    }

    private fun stopSound(pos: BlockPos) {
        playing.remove(pos)?.let { Minecraft.getInstance().soundManager.stop(it) }
    }
}
