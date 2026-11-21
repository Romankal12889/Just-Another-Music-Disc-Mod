package jamdm.lol.client

import jamdm.lol.MusicTableMenu
import jamdm.lol.Net
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.player.Inventory
import java.nio.file.Files
import java.nio.file.Path

class MusicTableScreen(menu: MusicTableMenu, inventory: Inventory, title: Component) :
    AbstractContainerScreen<MusicTableMenu>(menu, inventory, title) {

    private lateinit var selectButton: Button
    private lateinit var linkButton: Button
    private var fileName: String? = null
    private var error: String? = null
    private var loading = false

    init {
        imageWidth = 176
        imageHeight = 166
    }

    override fun init() {
        super.init()
        selectButton = Button.builder(Component.translatable("gui.just-another-music-disc-mod.select_file")) { onSelectFile() }
            .bounds(leftPos + 44, topPos + 20, 84, 18).build()
        linkButton = Button.builder(Component.translatable("gui.just-another-music-disc-mod.paste_link")) { onPasteLink() }
            .bounds(leftPos + 44, topPos + 40, 84, 18).build()
        addRenderableWidget(selectButton)
        addRenderableWidget(linkButton)
    }

    private fun onSelectFile() {
        error = null
        FilePicker.pick { path -> if (path != null) load(path) }
    }

    private fun onPasteLink() {
        error = null
        val link = (Minecraft.getInstance().keyboardHandler.clipboard ?: "").trim()
        if (link.isEmpty()) {
            error = "Clipboard is empty"
            return
        }
        startLoading { UrlLoader.fetch(link) }
    }

    private fun load(path: Path) = startLoading {
        if (Files.size(path) > Net.MAX_FILE_BYTES) throw IllegalStateException("File is bigger than 16 MB")
        Pair(path.fileName.toString().substringBeforeLast('.').take(Net.MAX_NAME_LENGTH), Files.readAllBytes(path))
    }

    private fun startLoading(source: () -> Pair<String, ByteArray>) {
        loading = true
        val mc = Minecraft.getInstance()
        val containerId = menu.containerId
        val thread = Thread({
            try {
                val (name, bytes) = source()
                if (bytes.size > Net.MAX_FILE_BYTES) throw IllegalStateException("File is bigger than 16 MB")
                AudioDecoder.decode(bytes) // validate before uploading
                mc.execute {
                    fileName = name
                    loading = false
                    UploadSender.start(containerId, name, bytes)
                }
            } catch (e: Exception) {
                mc.execute {
                    error = e.message ?: "Could not load audio"
                    loading = false
                }
            }
        }, "MusicTable-Load")
        thread.isDaemon = true
        thread.start()
    }

	    override fun renderBg(graphics: GuiGraphics, partialTick: Float, mouseX: Int, mouseY: Int) {
        val x = leftPos
        val y = topPos
        val w = imageWidth
        val h = imageHeight
        // panel: black outline, white highlight top/left, dark shadow bottom/right, grey fill
        graphics.fill(x, y, x + w, y + h, 0xFF000000.toInt())
        graphics.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0xFFFFFFFF.toInt())
        graphics.fill(x + 3, y + 3, x + w - 1, y + h - 1, 0xFF555555.toInt())
        graphics.fill(x + 3, y + 3, x + w - 3, y + h - 3, 0xFFC6C6C6.toInt())
        // slots: dark top/left edge, light bottom/right edge, grey inside
        for (slot in menu.slots) {
            val sx = slot.x + x
            val sy = slot.y + y
            graphics.fill(sx - 1, sy - 1, sx + 17, sy + 17, 0xFFFFFFFF.toInt())
            graphics.fill(sx - 1, sy - 1, sx + 16, sy + 16, 0xFF373737.toInt())
            graphics.fill(sx, sy, sx + 16, sy + 16, 0xFF8B8B8B.toInt())
        }
    }

    override fun render(graphics: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
        val ready = !menu.slots[MusicTableMenu.DISC].item.isEmpty && !UploadSender.uploading && !loading
        selectButton.active = ready
        linkButton.active = ready
        super.render(graphics, mouseX, mouseY, delta)

        var color = 0xFF404040.toInt()
        val status = when {
            loading -> "Loading..."
            UploadSender.uploading -> "Uploading " + (UploadSender.progress * 100).toInt() + "%"
            error != null -> { color = 0xFFAA0000.toInt(); error!! }
            fileName != null -> "File: " + (fileName ?: "")
            else -> ""
        }
        graphics.drawString(font, shorten(status, 13), leftPos + 44, topPos + 62, color, false)
        renderTooltip(graphics, mouseX, mouseY)
    }

    private fun shorten(text: String, max: Int): String =
        if (text.length <= max) text else text.take(max - 1) + "…"
}