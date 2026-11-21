package jamdm.lol

import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.resources.Identifier
import org.slf4j.LoggerFactory
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents
import net.minecraft.world.item.CreativeModeTabs

object MusicTableMod : ModInitializer {
    const val MOD_ID = "just-another-music-disc-mod"
    val LOGGER = LoggerFactory.getLogger(MOD_ID)

    fun id(path: String): Identifier = Identifier.fromNamespaceAndPath(MOD_ID, path)

    override fun onInitialize() {
        LOGGER.info("Just Another Music Disc Mod: registering...")
        ModSounds.init()
        ModBlocks.init()
        ModItems.init()
        ModMenus.init()
        LOGGER.info("Just Another Music Disc Mod: registered block, item, menu, sound")

        PayloadTypeRegistry.playC2S().register(UploadStartPayload.TYPE, UploadStartPayload.CODEC)
        PayloadTypeRegistry.playC2S().register(UploadChunkPayload.TYPE, UploadChunkPayload.CODEC)
        PayloadTypeRegistry.playC2S().register(RequestAudioPayload.TYPE, RequestAudioPayload.CODEC)

        PayloadTypeRegistry.playS2C().register(PlayDiscPayload.TYPE, PlayDiscPayload.CODEC)
        PayloadTypeRegistry.playS2C().register(StopDiscPayload.TYPE, StopDiscPayload.CODEC)
        PayloadTypeRegistry.playS2C().register(AudioChunkPayload.TYPE, AudioChunkPayload.CODEC)

        ServerPlayNetworking.registerGlobalReceiver(UploadStartPayload.TYPE) { payload, ctx ->
            Uploads.start(ctx.player(), payload)
        }
        ServerPlayNetworking.registerGlobalReceiver(UploadChunkPayload.TYPE) { payload, ctx ->
            Uploads.chunk(ctx.player(), payload)
        }
        ServerPlayNetworking.registerGlobalReceiver(RequestAudioPayload.TYPE) { payload, ctx ->
            AudioTransfers.request(ctx.player(), payload.hash)
        }
        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS).register { entries ->
            entries.accept(ModBlocks.MUSIC_TABLE)
        }
        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register { entries ->
            entries.accept(ModItems.CUSTOM_DISC)
        }

        ServerPlayConnectionEvents.DISCONNECT.register { handler, _ ->
            JukeboxTracker.forget(handler.player.uuid)
            Uploads.cancel(handler.player.uuid)
        }

        JukeboxTracker.init()
        AudioTransfers.init()
    }
}
