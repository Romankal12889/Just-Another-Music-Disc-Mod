package jamdm.lol

import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.component.CustomData

/** Dye -> which vanilla disc the custom disc looks like. */
object DiscStyle {
    private const val HASH_KEY = "music_table_hash"
    private const val NAME_KEY = "music_table_name"

    val DEFAULT_MODEL: Identifier = Identifier.withDefaultNamespace("music_disc_11")

    // red = Pigstep, yellow = 13, green = Cat, blue = Flurry
    private fun modelName(dye: ItemStack): String? =
        when (BuiltInRegistries.ITEM.getKey(dye.item).path) {
            "red_dye" -> "music_disc_pigstep"
            "yellow_dye" -> "music_disc_13"
            "green_dye" -> "music_disc_cat"
            "blue_dye" -> "music_disc_otherside"
            else -> null
        }

    fun isStyleDye(stack: ItemStack): Boolean = modelName(stack) != null

    fun modelFor(dye: ItemStack): Identifier =
        modelName(dye)?.let { Identifier.withDefaultNamespace(it) } ?: DEFAULT_MODEL

    fun create(hash: String, name: String, model: Identifier): ItemStack {
        val stack = ItemStack(ModItems.CUSTOM_DISC)
        val tag = CompoundTag()
        tag.putString(HASH_KEY, hash)
        tag.putString(NAME_KEY, name)
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag))
        stack.set(DataComponents.ITEM_MODEL, model)
        stack.set(DataComponents.ITEM_NAME, Component.translatable("item.just-another-music-disc-mod.custom_disc.named", name))
        return stack
    }

    /** The audio hash stored on a customised disc, or null for a blank / foreign item. */
    fun hashOf(stack: ItemStack): String? {
        if (!stack.`is`(ModItems.CUSTOM_DISC)) return null
        val data = stack.get(DataComponents.CUSTOM_DATA) ?: return null
        val hash = data.copyTag().getStringOr(HASH_KEY, "")
        return hash.ifEmpty { null }
    }

    fun nameOf(stack: ItemStack): String {
        val data = stack.get(DataComponents.CUSTOM_DATA) ?: return "Custom Disc"
        return data.copyTag().getStringOr(NAME_KEY, "Custom Disc")
    }
}
