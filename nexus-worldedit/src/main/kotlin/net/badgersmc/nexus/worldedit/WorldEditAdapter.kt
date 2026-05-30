package net.badgersmc.nexus.worldedit

import com.sk89q.worldedit.extent.clipboard.Clipboard
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats
import org.bukkit.World
import java.io.File

/**
 * Adapter that selects FastAsyncWorldEdit when available and falls back
 * to vanilla WorldEdit. Consumers depend only on this facade — never
 * directly on WE or FAWE.
 */
object WorldEditAdapter {

    /**
     * True if FastAsyncWorldEdit is loaded on this server.
     *
     * Exposed as a public API so consumers (e.g. `WorldEditSchematicAdapter`
     * in downstream plugins) can branch on FAWE availability — for example
     * to dispatch pastes through FAWE's async queue instead of Bukkit's
     * scheduler when FAWE is present.
     */
    val isFawePresent: Boolean by lazy {
        try {
            Class.forName("com.fastasyncworldedit.core.Fawe")
            true
        } catch (_: ClassNotFoundException) { false }
    }

    /**
     * Save a clipboard schematic to [file]. Runs on the calling thread;
     * callers are responsible for scheduling async when FAWE is available.
     */
    fun saveSchematic(clipboard: Clipboard, file: File) {
        val format = ClipboardFormats.findByFile(file)
            ?: ClipboardFormats.findByAlias("schem")
            ?: error("No schematic format found for ${file.name}")
        val parent = file.parentFile
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            error("Failed to create schematic directory: ${parent.absolutePath}")
        }
        file.outputStream().use { out ->
            format.getWriter(out).use { writer -> writer.write(clipboard) }
        }
    }

    /**
     * Load a clipboard schematic from [file].
     */
    fun loadSchematic(file: File): Clipboard {
        val format = ClipboardFormats.findByFile(file)
            ?: error("No schematic format found for ${file.name}")
        return file.inputStream().use { inp ->
            format.getReader(inp).use { reader -> reader.read() }
        }
    }
}
