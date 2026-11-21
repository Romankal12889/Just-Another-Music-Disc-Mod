package jamdm.lol.client

import jamdm.lol.Net
import jamdm.lol.UploadChunkPayload
import jamdm.lol.UploadStartPayload
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.minecraft.client.Minecraft

/** Sends the chosen file to the server a few packets per tick so we never flood the connection. */
object UploadSender {
    private const val CHUNKS_PER_TICK = 4

    private class Job(val containerId: Int, val name: String, val bytes: ByteArray) {
        val chunkCount = (bytes.size + Net.C2S_CHUNK_SIZE - 1) / Net.C2S_CHUNK_SIZE
        var next = 0
        var started = false
    }

    private var job: Job? = null

    val uploading: Boolean get() = job != null

    /** 0.0 .. 1.0 */
    val progress: Float get() = job?.let { it.next.toFloat() / it.chunkCount } ?: 1f

    fun start(containerId: Int, name: String, bytes: ByteArray) {
        job = Job(containerId, name, bytes)
    }

    fun cancel() {
        job = null
    }

    fun tick() {
        val j = job ?: return
        // stop if the player closed the Music Table
        if (Minecraft.getInstance().player?.containerMenu?.containerId != j.containerId) {
            job = null
            return
        }
        if (!j.started) {
            ClientPlayNetworking.send(UploadStartPayload(j.containerId, j.bytes.size, j.chunkCount, j.name))
            j.started = true
        }
        repeat(CHUNKS_PER_TICK) {
            if (j.next < j.chunkCount) {
                val from = j.next * Net.C2S_CHUNK_SIZE
                val to = minOf(j.bytes.size, from + Net.C2S_CHUNK_SIZE)
                ClientPlayNetworking.send(UploadChunkPayload(j.next, j.bytes.copyOfRange(from, to)))
                j.next++
            }
        }
        if (j.next >= j.chunkCount) job = null
    }
}
