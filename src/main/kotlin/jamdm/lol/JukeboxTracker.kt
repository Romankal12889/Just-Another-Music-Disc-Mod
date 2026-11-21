package jamdm.lol

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.event.player.UseBlockCallback
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceKey
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.InteractionResult
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.JukeboxBlock
import net.minecraft.world.level.block.entity.JukeboxBlockEntity
import java.util.UUID

/**
 * The vanilla jukebox runs a silent placeholder song for our discs. This tracker notices
 * when a custom disc is in a jukebox and tells nearby clients to play the real audio.
 */
object JukeboxTracker {
    private const val RANGE = 96.0

    private data class Key(val dim: ResourceKey<Level>, val pos: BlockPos)

    private class Active(
        val key: Key, val hash: String, val title: String, val startTime: Long,
        val notified: MutableSet<UUID> = HashSet()
    )

    private class Candidate(val key: Key, var ticksLeft: Int)

    private val active = HashMap<Key, Active>()
    private val candidates = ArrayList<Candidate>()

    fun init() {
        // A right click on a jukebox may insert or eject a disc; re-check it for a few ticks.
        UseBlockCallback.EVENT.register(UseBlockCallback { _, level, _, hit ->
            if (!level.isClientSide() && level.getBlockState(hit.blockPos).`is`(Blocks.JUKEBOX)) {
                candidates.add(Candidate(Key(level.dimension(), hit.blockPos.immutable()), 5))
            }
            InteractionResult.PASS
        })
        ServerTickEvents.END_SERVER_TICK.register(ServerTickEvents.EndTick { server -> tick(server) })
    }

    fun forget(uuid: UUID) {
        active.values.forEach { it.notified.remove(uuid) }
    }

    private fun tick(server: MinecraftServer) {
        if (candidates.isNotEmpty()) {
            val it = candidates.iterator()
            while (it.hasNext()) {
                val c = it.next()
                val level = server.getLevel(c.key.dim)
                if (level != null) refresh(server, level, c.key)
                c.ticksLeft--
                if (level == null || c.ticksLeft <= 0) it.remove()
            }
        }
        if (active.isEmpty() || server.tickCount % 10 != 0) return
        for (a in active.values.toList()) {
            val level = server.getLevel(a.key.dim) ?: continue
            refresh(server, level, a.key)
            active[a.key]?.let { announce(level, it) }
        }
    }

    private fun refresh(server: MinecraftServer, level: ServerLevel, key: Key) {
        var hash: String? = null
        var title = "Custom Disc"
        val state = level.getBlockState(key.pos)
        if (state.`is`(Blocks.JUKEBOX) && state.getValue(JukeboxBlock.HAS_RECORD)) {
            val be = level.getBlockEntity(key.pos)
            if (be is JukeboxBlockEntity) {
                val stack = be.getItem(0)
                hash = DiscStyle.hashOf(stack)
                if (hash != null) title = DiscStyle.nameOf(stack)
            }
        }
        val current = active[key]
        if (hash != null) {
            if (current == null || current.hash != hash) {
                if (current != null) stop(server, current)
                val created = Active(key, hash, title, level.gameTime)
                active[key] = created
                announce(level, created)
            }
        } else if (current != null) {
            stop(server, current)
            active.remove(key)
        }
    }

    private fun announce(level: ServerLevel, a: Active) {
        val cx = a.key.pos.x + 0.5
        val cy = a.key.pos.y + 0.5
        val cz = a.key.pos.z + 0.5
        val offset = (level.gameTime - a.startTime).toInt().coerceAtLeast(0)
        for (player in level.players()) {
            if (player.uuid in a.notified) continue
            if (player.distanceToSqr(cx, cy, cz) > RANGE * RANGE) continue
            a.notified.add(player.uuid)
            ServerPlayNetworking.send(player, PlayDiscPayload(a.key.pos, a.hash, a.title, offset))
        }
    }

    private fun stop(server: MinecraftServer, a: Active) {
        for (uuid in a.notified) {
            val player = server.playerList.getPlayer(uuid) ?: continue
            ServerPlayNetworking.send(player, StopDiscPayload(a.key.pos))
        }
    }
}
