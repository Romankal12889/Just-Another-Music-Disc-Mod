package jamdm.lol.client

import net.minecraft.client.Minecraft
import org.lwjgl.system.MemoryStack
import org.lwjgl.util.tinyfd.TinyFileDialogs
import java.nio.file.Path

/** Opens the operating system's own "open file" dialog (the normal Windows 10/11 one on Windows). */
object FilePicker {
    fun pick(onResult: (Path?) -> Unit) {
        val thread = Thread({
            var chosen: String? = null
            try {
                MemoryStack.stackPush().use { stack ->
                    val filters = stack.mallocPointer(2)
                    filters.put(stack.UTF8("*.ogg"))
                    filters.put(stack.UTF8("*.wav"))
                    filters.flip()
                    chosen = TinyFileDialogs.tinyfd_openFileDialog(
                        "Select an audio file", "", filters, "Audio files (*.ogg, *.wav)", false
                    )
                }
            } catch (t: Throwable) {
                jamdm.lol.MusicTableMod.LOGGER.error("File dialog failed", t)
            }
            val result = chosen?.let { Path.of(it) }
            Minecraft.getInstance().execute { onResult(result) }
        }, "MusicTable-FilePicker")
        thread.isDaemon = true
        thread.start()
    }
}
