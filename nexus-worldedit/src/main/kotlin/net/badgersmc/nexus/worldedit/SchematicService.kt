package net.badgersmc.nexus.worldedit

import org.bukkit.World
import java.io.File

interface SchematicService {
    sealed interface Result {
        data object Success : Result
        data class Failure(val cause: Throwable) : Result
    }
    fun capture(regionId: String, world: World, outputFile: File): Result
    fun restore(regionId: String, world: World, sourceFile: File, async: Boolean = true): Result
}
