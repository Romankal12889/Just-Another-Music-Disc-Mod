package jamdm.lol.client

import org.lwjgl.stb.STBVorbis
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil
import org.lwjgl.system.libc.LibCStdlib
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Mono 16-bit PCM. Mono because Minecraft only positions (3D) mono sounds. */
class Pcm(val samples: ShortArray, val rate: Int)

object AudioDecoder {
    private const val MAX_SECONDS = 20 * 60

    fun decode(bytes: ByteArray): Pcm {
        val pcm = when {
            isOgg(bytes) -> decodeOgg(bytes)
            isWav(bytes) -> decodeWav(bytes)
            else -> throw IOException("Unsupported file. Use .ogg (Vorbis) or .wav")
        }
        if (pcm.samples.size > pcm.rate.toLong() * MAX_SECONDS) throw IOException("Audio is longer than 20 minutes")
        if (pcm.samples.isEmpty()) throw IOException("Audio file is empty")
        return pcm
    }

    private fun isOgg(b: ByteArray) =
        b.size > 4 && b[0] == 'O'.code.toByte() && b[1] == 'g'.code.toByte() && b[2] == 'g'.code.toByte() && b[3] == 'S'.code.toByte()

    private fun isWav(b: ByteArray) =
        b.size > 12 && String(b, 0, 4, Charsets.US_ASCII) == "RIFF" && String(b, 8, 4, Charsets.US_ASCII) == "WAVE"

    private fun decodeOgg(bytes: ByteArray): Pcm {
        val data = MemoryUtil.memAlloc(bytes.size)
        try {
            data.put(bytes).flip()
            MemoryStack.stackPush().use { stack ->
                val channels = stack.mallocInt(1)
                val rate = stack.mallocInt(1)
                val decoded = STBVorbis.stb_vorbis_decode_memory(data, channels, rate)
                    ?: throw IOException("Could not decode this .ogg (it must be Vorbis)")
                try {
                    val ch = channels.get(0)
                    val total = decoded.remaining()
                    val frames = total / ch
                    val mono = ShortArray(frames)
                    for (i in 0 until frames) {
                        var sum = 0
                        for (c in 0 until ch) sum += decoded.get(i * ch + c).toInt()
                        mono[i] = (sum / ch).toShort()
                    }
                    return Pcm(mono, rate.get(0))
                } finally {
                    LibCStdlib.free(decoded)
                }
            }
        } finally {
            MemoryUtil.memFree(data)
        }
    }

    private fun decodeWav(b: ByteArray): Pcm {
        val bb = ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN)
        var pos = 12
        var format = 0
        var channels = 0
        var rate = 0
        var bits = 0
        var dataOff = -1
        var dataLen = 0
        while (pos + 8 <= b.size) {
            val id = String(b, pos, 4, Charsets.US_ASCII)
            val len = bb.getInt(pos + 4)
            val body = pos + 8
            if (id == "fmt " && body + 16 <= b.size) {
                format = bb.getShort(body).toInt() and 0xFFFF
                channels = bb.getShort(body + 2).toInt()
                rate = bb.getInt(body + 4)
                bits = bb.getShort(body + 14).toInt()
                if (format == 0xFFFE && len >= 26 && body + 26 <= b.size) format = bb.getShort(body + 24).toInt() and 0xFFFF
            } else if (id == "data") {
                dataOff = body
                dataLen = minOf(len.toLong() and 0xFFFFFFFFL, (b.size - body).toLong()).toInt()
                break
            }
            if (len < 0) break
            pos = body + len + (len and 1)
        }
        if (dataOff < 0 || channels <= 0 || rate <= 0) throw IOException("Broken .wav file")
        val isFloat = format == 3
        if (!(format == 1 || isFloat) || bits !in intArrayOf(8, 16, 24, 32)) {
            throw IOException("Unsupported .wav (use 8/16/24/32-bit PCM)")
        }
        val bytesPer = bits / 8
        val frames = dataLen / (bytesPer * channels)
        val mono = ShortArray(frames)
        for (f in 0 until frames) {
            var sum = 0.0
            for (c in 0 until channels) {
                val o = dataOff + (f * channels + c) * bytesPer
                sum += when {
                    isFloat && bits == 32 -> bb.getFloat(o).toDouble()
                    bits == 8 -> ((b[o].toInt() and 0xFF) - 128) / 128.0
                    bits == 16 -> bb.getShort(o) / 32768.0
                    bits == 24 -> (((b[o + 2].toInt() shl 16) or ((b[o + 1].toInt() and 0xFF) shl 8) or (b[o].toInt() and 0xFF)) / 8388608.0)
                    else -> bb.getInt(o) / 2147483648.0
                }
            }
            mono[f] = ((sum / channels).coerceIn(-1.0, 1.0) * 32767.0).toInt().toShort()
        }
        return Pcm(mono, rate)
    }
}
