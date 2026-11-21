package jamdm.lol

import net.minecraft.core.BlockPos
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload

object Net {
    /** Client -> server packets are limited to 32 KiB, stay below that. */
    const val C2S_CHUNK_SIZE = 30_000
    /** Server -> client packets are limited to 1 MiB, stay well below that. */
    const val S2C_CHUNK_SIZE = 200_000
    const val MAX_FILE_BYTES = 16 * 1024 * 1024
    const val MAX_NAME_LENGTH = 64
}

private fun <T : CustomPacketPayload> codec(
    write: (RegistryFriendlyByteBuf, T) -> Unit,
    read: (RegistryFriendlyByteBuf) -> T
): StreamCodec<RegistryFriendlyByteBuf, T> =
    StreamCodec.of<RegistryFriendlyByteBuf, T>(
        { buf, value -> write(buf, value) },
        { buf -> read(buf) }
    )

// ---------------------------------------------------------------- client -> server

data class UploadStartPayload(
    val containerId: Int, val totalSize: Int, val chunkCount: Int, val fileName: String
) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<UploadStartPayload> = TYPE

    companion object {
        val TYPE = CustomPacketPayload.Type<UploadStartPayload>(MusicTableMod.id("upload_start"))
        val CODEC = codec<UploadStartPayload>(
            { b, v ->
                b.writeVarInt(v.containerId); b.writeVarInt(v.totalSize)
                b.writeVarInt(v.chunkCount); b.writeUtf(v.fileName, 96)
            },
            { b -> UploadStartPayload(b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readUtf(96)) }
        )
    }
}

class UploadChunkPayload(val index: Int, val data: ByteArray) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<UploadChunkPayload> = TYPE

    companion object {
        val TYPE = CustomPacketPayload.Type<UploadChunkPayload>(MusicTableMod.id("upload_chunk"))
        val CODEC = codec<UploadChunkPayload>(
            { b, v -> b.writeVarInt(v.index); b.writeByteArray(v.data) },
            { b -> UploadChunkPayload(b.readVarInt(), b.readByteArray(Net.C2S_CHUNK_SIZE)) }
        )
    }
}

data class RequestAudioPayload(val hash: String) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<RequestAudioPayload> = TYPE

    companion object {
        val TYPE = CustomPacketPayload.Type<RequestAudioPayload>(MusicTableMod.id("request_audio"))
        val CODEC = codec<RequestAudioPayload>(
            { b, v -> b.writeUtf(v.hash, 64) },
            { b -> RequestAudioPayload(b.readUtf(64)) }
        )
    }
}

// ---------------------------------------------------------------- server -> client

data class PlayDiscPayload(
    val pos: BlockPos, val hash: String, val title: String, val offsetTicks: Int
) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<PlayDiscPayload> = TYPE

    companion object {
        val TYPE = CustomPacketPayload.Type<PlayDiscPayload>(MusicTableMod.id("play_disc"))
        val CODEC = codec<PlayDiscPayload>(
            { b, v -> b.writeBlockPos(v.pos); b.writeUtf(v.hash, 64); b.writeUtf(v.title, 96); b.writeVarInt(v.offsetTicks) },
            { b -> PlayDiscPayload(b.readBlockPos(), b.readUtf(64), b.readUtf(96), b.readVarInt()) }
        )
    }
}

data class StopDiscPayload(val pos: BlockPos) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<StopDiscPayload> = TYPE

    companion object {
        val TYPE = CustomPacketPayload.Type<StopDiscPayload>(MusicTableMod.id("stop_disc"))
        val CODEC = codec<StopDiscPayload>(
            { b, v -> b.writeBlockPos(v.pos) },
            { b -> StopDiscPayload(b.readBlockPos()) }
        )
    }
}

class AudioChunkPayload(val hash: String, val index: Int, val count: Int, val data: ByteArray) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<AudioChunkPayload> = TYPE

    companion object {
        val TYPE = CustomPacketPayload.Type<AudioChunkPayload>(MusicTableMod.id("audio_chunk"))
        val CODEC = codec<AudioChunkPayload>(
            { b, v -> b.writeUtf(v.hash, 64); b.writeVarInt(v.index); b.writeVarInt(v.count); b.writeByteArray(v.data) },
            { b -> AudioChunkPayload(b.readUtf(64), b.readVarInt(), b.readVarInt(), b.readByteArray(Net.S2C_CHUNK_SIZE + 1024)) }
        )
    }
}
