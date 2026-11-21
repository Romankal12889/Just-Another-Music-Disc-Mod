package jamdm.lol

import net.minecraft.core.Registry
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceKey
import net.minecraft.sounds.SoundEvent
import net.minecraft.world.flag.FeatureFlagSet
import net.minecraft.world.inventory.MenuType
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.Item
import net.minecraft.world.item.JukeboxSong
import net.minecraft.world.item.Rarity
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.SoundType
import net.minecraft.world.level.block.state.BlockBehaviour
import net.minecraft.world.level.material.MapColor

object ModSounds {
    /** The vanilla jukebox plays this; it has no audio, our client mod plays the real file. */
    val CUSTOM_DISC_SILENT: SoundEvent = run {
        val id = MusicTableMod.id("custom_disc_silent")
        Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id))
    }

    fun init() {}
}

object ModBlocks {
    val MUSIC_TABLE: Block = run {
        val id = MusicTableMod.id("music_table")
        val blockKey = ResourceKey.create(Registries.BLOCK, id)
        val block = Registry.register(
            BuiltInRegistries.BLOCK, blockKey,
            MusicTableBlock(
                BlockBehaviour.Properties.of()
                    .setId(blockKey)
                    .mapColor(MapColor.WOOD)
                    .strength(2.5f)
                    .sound(SoundType.WOOD)
            )
        )
        val itemKey = ResourceKey.create(Registries.ITEM, id)
        Registry.register(
            BuiltInRegistries.ITEM, itemKey,
            BlockItem(block, Item.Properties().setId(itemKey).useBlockDescriptionPrefix())
        )
        block
    }

    fun init() {}
}

object ModItems {
    val CUSTOM_DISC_SONG: ResourceKey<JukeboxSong> =
        ResourceKey.create(Registries.JUKEBOX_SONG, MusicTableMod.id("custom_disc"))

    val CUSTOM_DISC: Item = run {
        val key = ResourceKey.create(Registries.ITEM, MusicTableMod.id("custom_disc"))
        Registry.register(
            BuiltInRegistries.ITEM, key,
            Item(
                Item.Properties()
                    .setId(key)
                    .stacksTo(1)
                    .rarity(Rarity.RARE)
                    .jukeboxPlayable(CUSTOM_DISC_SONG)
                    // looks like disc 11 by default; the Music Table swaps this per dye
                    .component(DataComponents.ITEM_MODEL, DiscStyle.DEFAULT_MODEL)
            )
        )
    }

    fun init() {}
}

object ModMenus {
    val MUSIC_TABLE: MenuType<MusicTableMenu> = Registry.register(
        BuiltInRegistries.MENU,
        MusicTableMod.id("music_table"),
        MenuType(MenuType.MenuSupplier<MusicTableMenu> { id, inv -> MusicTableMenu(id, inv) }, FeatureFlagSet.of())
    )

    fun init() {}
}
