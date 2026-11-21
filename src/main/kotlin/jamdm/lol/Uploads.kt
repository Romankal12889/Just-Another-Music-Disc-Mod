package jamdm.lol

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.util.UUID

/** Receives the chunked file upload from the Music Table screen. */
object Uploads {
    private class Session(val containerId: Int, val total: Int, val chunkCount: Int, val name: String) {
        val out = ByteArrayOutputStream(total)
        var next = 0
    }

    private val sessions = HashMap<UUID, Session>()

    fun start(player: ServerPlayer, p: UploadStartPayload) {
        val menu = player.containerMenu as? MusicTableMenu
        val expectedChunks = (p.totalSize + Net.C2S_CHUNK_SIZE - 1) / Net.C2S_CHUNK_SIZE
        if (menu == null || menu.containerId != p.containerId ||
            p.totalSize !in 1..Net.MAX_FILE_BYTES || p.chunkCount != expectedChunks
        ) {
            fail(player, "message.just-another-music-disc-mod.upload_rejected")
            return
        }
        val name = p.fileName.trim().take(Net.MAX_NAME_LENGTH).ifEmpty { "Custom Disc" }
        sessions[player.uuid] = Session(p.containerId, p.totalSize, p.chunkCount, name)
    }

    fun chunk(player: ServerPlayer, p: UploadChunkPayload) {
        val s = sessions[player.uuid] ?: return
        val menu = player.containerMenu as? MusicTableMenu
        if (menu == null || menu.containerId != s.containerId ||
            p.index != s.next || s.out.size() + p.data.size > s.total
        ) {
            sessions.remove(player.uuid)
            fail(player, "message.just-another-music-disc-mod.upload_rejected")
            return
        }
        s.out.write(p.data)
        s.next++
        if (s.next < s.chunkCount) return

        sessions.remove(player.uuid)
        val bytes = s.out.toByteArray()
        if (bytes.size != s.total || !AudioStore.looksLikeAudio(bytes)) {
            fail(player, "message.just-another-music-disc-mod.bad_file")
            return
        }
        try {
            val hash = AudioStore.save(bytes)
            menu.setAudio(hash, s.name)
        } catch (e: Exception) {
            MusicTableMod.LOGGER.error("Could not store uploaded audio", e)
            fail(player, "message.just-another-music-disc-mod.upload_rejected")
        }
    }

    fun cancel(uuid: UUID) {
        sessions.remove(uuid)
    }

    private fun fail(player: ServerPlayer, key: String) {
        player.sendSystemMessage(Component.translatable(key))
    }
}

/** Streams stored audio to clients that ask for it (throttled, a few chunks per tick). */
object AudioTransfers {
    private class Pending(val player: ServerPlayer, val payload: AudioChunkPayload)

    private val queue = java.util.ArrayDeque<Pending>()

    fun init() {
        ServerTickEvents.END_SERVER_TICK.register(ServerTickEvents.EndTick { drain() })
    }

    fun request(player: ServerPlayer, hash: String) {
        val path = AudioStore.path(hash) ?: return
        if (!Files.exists(path)) return
        val bytes = Files.readAllBytes(path)
        val count = (bytes.size + Net.S2C_CHUNK_SIZE - 1) / Net.S2C_CHUNK_SIZE
        for (i in 0 until count) {
            val from = i * Net.S2C_CHUNK_SIZE
            val to = minOf(bytes.size, from + Net.S2C_CHUNK_SIZE)
            queue.add(Pending(player, AudioChunkPayload(hash, i, count, bytes.copyOfRange(from, to))))
        }
    }

    private fun drain() {
        repeat(4) {
            val next = queue.poll() ?: return
            if (!next.player.hasDisconnected()) ServerPlayNetworking.send(next.player, next.payload)
        }
    }
}
