package jamdm.lol

import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.world.InteractionResult
import net.minecraft.world.SimpleMenuProvider
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.ContainerLevelAccess
import net.minecraft.world.inventory.MenuConstructor
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockBehaviour
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.BlockHitResult

class MusicTableBlock(properties: BlockBehaviour.Properties) : Block(properties) {

    override fun useWithoutItem(
        state: BlockState, level: Level, pos: BlockPos, player: Player, hit: BlockHitResult
    ): InteractionResult {
        if (!level.isClientSide()) {
            player.openMenu(
                SimpleMenuProvider(
                    MenuConstructor { id, inventory, _ ->
                        MusicTableMenu(id, inventory, ContainerLevelAccess.create(level, pos))
                    },
                    TITLE
                )
            )
        }
        return InteractionResult.SUCCESS
    }

    companion object {
        val TITLE: Component = Component.translatable("container.just-another-music-disc-mod.music_table")
    }
}
