package jamdm.lol

import net.minecraft.core.component.DataComponents
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.Container
import net.minecraft.world.SimpleContainer
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.ContainerLevelAccess
import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.ItemStack

class MusicTableMenu(
    containerId: Int,
    private val playerInventory: Inventory,
    private val access: ContainerLevelAccess
) : AbstractContainerMenu(ModMenus.MUSIC_TABLE, containerId) {

    constructor(containerId: Int, inventory: Inventory) : this(containerId, inventory, ContainerLevelAccess.NULL)

    private val input: SimpleContainer = object : SimpleContainer(2) {
        override fun setChanged() {
            super.setChanged()
            this@MusicTableMenu.slotsChanged(this)
        }
    }
    private val result = SimpleContainer(1)

    // server side only: what the player uploaded in this session
    var audioHash: String? = null
        private set
    var audioName: String? = null
        private set

    init {
        addSlot(object : Slot(input, DISC, 20, 26) {
            override fun mayPlace(stack: ItemStack): Boolean = stack.`is`(ModItems.CUSTOM_DISC)
            override fun getMaxStackSize(): Int = 1
        })
        addSlot(object : Slot(input, DYE, 20, 50) {
            override fun mayPlace(stack: ItemStack): Boolean = DiscStyle.isStyleDye(stack)
        })
        addSlot(object : Slot(result, 0, 142, 38) {
            override fun mayPlace(stack: ItemStack): Boolean = false

            override fun mayPickup(player: Player): Boolean =
                player.hasInfiniteMaterials() || player.experienceLevel >= COST_LEVELS

            override fun onTake(player: Player, stack: ItemStack) {
                super.onTake(player, stack)
                if (!player.hasInfiniteMaterials()) player.giveExperienceLevels(-COST_LEVELS)
                input.removeItem(DISC, 1)
                input.removeItem(DYE, 1)
                audioHash = null
                audioName = null
                updateResult()
            }
        })
        addStandardInventorySlots(playerInventory, 8, 84)
    }

    /** Called by [Uploads] when a full, valid file has arrived on the server. */
    fun setAudio(hash: String, name: String) {
        audioHash = hash
        audioName = name
        updateResult()
    }

    override fun slotsChanged(container: Container) {
        super.slotsChanged(container)
        if (container === input) updateResult()
    }

    private fun updateResult() {
        if (playerInventory.player !is ServerPlayer) return
        val disc = input.getItem(DISC)
        val hash = audioHash
        if (disc.isEmpty || hash == null) {
            result.setItem(0, ItemStack.EMPTY)
        } else {
            result.setItem(0, DiscStyle.create(hash, audioName ?: "Custom Disc", DiscStyle.modelFor(input.getItem(DYE))))
        }
    }

    override fun quickMoveStack(player: Player, index: Int): ItemStack {
        val slot = slots[index]
        if (!slot.hasItem()) return ItemStack.EMPTY
        val stack = slot.item
        val original = stack.copy()

        if (index == RESULT) {
            if (!moveItemStackTo(stack, INV_START, INV_END, true)) return ItemStack.EMPTY
        } else if (index < RESULT) {
            if (!moveItemStackTo(stack, INV_START, INV_END, false)) return ItemStack.EMPTY
        } else if (stack.`is`(ModItems.CUSTOM_DISC)) {
            if (!moveItemStackTo(stack, DISC, DISC + 1, false)) return ItemStack.EMPTY
        } else if (DiscStyle.isStyleDye(stack)) {
            if (!moveItemStackTo(stack, DYE, DYE + 1, false)) return ItemStack.EMPTY
        } else {
            return ItemStack.EMPTY
        }

        if (stack.isEmpty) slot.setByPlayer(ItemStack.EMPTY) else slot.setChanged()
        if (stack.count == original.count) return ItemStack.EMPTY
        slot.onTake(player, stack)
        return original
    }

    override fun stillValid(player: Player): Boolean =
        stillValid(access, player, ModBlocks.MUSIC_TABLE)

    override fun removed(player: Player) {
        super.removed(player)
        if (player is ServerPlayer) Uploads.cancel(player.uuid)
        access.execute { _, _ -> clearContainer(player, input) }
    }

    companion object {
        const val DISC = 0
        const val DYE = 1
        const val RESULT = 2
        const val INV_START = 3
        const val INV_END = 39
		const val COST_LEVELS = 0
    }
}
