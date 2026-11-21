package jamdm.lol.client

import jamdm.lol.AudioChunkPayload
import jamdm.lol.ModMenus
import jamdm.lol.PlayDiscPayload
import jamdm.lol.StopDiscPayload
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.minecraft.client.gui.screens.MenuScreens

object MusicTableClient : ClientModInitializer {
    override fun onInitializeClient() {
        MenuScreens.register(ModMenus.MUSIC_TABLE) { menu, inventory, title ->
            MusicTableScreen(menu, inventory, title)
        }

        ClientPlayNetworking.registerGlobalReceiver(PlayDiscPayload.TYPE) { payload, _ -> DiscPlayer.onPlay(payload) }
        ClientPlayNetworking.registerGlobalReceiver(StopDiscPayload.TYPE) { payload, _ -> DiscPlayer.onStop(payload.pos) }
        ClientPlayNetworking.registerGlobalReceiver(AudioChunkPayload.TYPE) { payload, _ -> DiscPlayer.onChunk(payload) }

        ClientTickEvents.END_CLIENT_TICK.register(ClientTickEvents.EndTick { UploadSender.tick() })
        ClientPlayConnectionEvents.DISCONNECT.register { _, _ ->
            DiscPlayer.clear()
            UploadSender.cancel()
        }
    }
}
