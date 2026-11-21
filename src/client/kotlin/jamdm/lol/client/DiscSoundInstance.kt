package jamdm.lol.client

import jamdm.lol.MusicTableMod
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.client.resources.sounds.SoundInstance
import net.minecraft.client.sounds.AudioStream
import net.minecraft.core.BlockPos
import net.minecraft.resources.Identifier
import net.minecraft.sounds.SoundSource
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import javax.sound.sampled.AudioFormat

class DiscSoundInstance(val pos: BlockPos) :
    SimpleSoundInstance(
        MusicTableMod.id("custom_stream"), SoundSource.RECORDS, 4.0f, 1.0f,
        SoundInstance.createUnseededRandom(), false, 0, SoundInstance.Attenuation.LINEAR,
        pos.x + 0.5, pos.y + 0.5, pos.z + 0.5, false
    )

/** Hands our own audio stream to Minecraft when it asks for the placeholder sound file. */
object DiscSoundHook {
    private val STREAM_PATH: Identifier = MusicTableMod.id("sounds/custom_stream.ogg")
    private val pending = java.util.ArrayDeque<CompletableFuture<AudioStream>>()

    fun offer(file: Path, offsetTicks: Int) {
        val future = CompletableFuture.supplyAsync<AudioStream> {
            val pcm = AudioDecoder.decode(Files.readAllBytes(file))
            PcmAudioStream(pcm, (offsetTicks.toLong() * pcm.rate / 20).toInt())
        }
        synchronized(pending) { pending.add(future) }
    }

    fun clearPending() {
        synchronized(pending) { pending.clear() }
    }

    @JvmStatic
    fun matches(path: Identifier): Boolean = path == STREAM_PATH

    @JvmStatic
    fun take(): CompletableFuture<AudioStream>? = synchronized(pending) { pending.poll() }
}

class PcmAudioStream(private val pcm: Pcm, startSample: Int) : AudioStream {
    private val format = AudioFormat(pcm.rate.toFloat(), 16, 1, true, false)
    private var position = startSample.coerceIn(0, pcm.samples.size)

    override fun getFormat(): AudioFormat = format

    override fun read(size: Int): ByteBuffer {
        val count = minOf(size / 2, pcm.samples.size - position).coerceAtLeast(0)
        val buffer = ByteBuffer.allocateDirect(count * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until count) buffer.putShort(pcm.samples[position + i])
        position += count
        buffer.flip()
        return buffer
    }

    override fun close() {}
}
