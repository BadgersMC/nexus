package net.badgersmc.nexus.worldedit

import org.bukkit.World
import java.io.File

/**
 * Domain interface for stall schematic capture and restore.
 *
 * Implementations resolve [regionId] via WorldGuard's `RegionContainer`:
 * the string must be a valid WorldGuard region ID in the target [World].
 * The region's min/max points are used to define the clipboard bounds for
 * capture; the clipboard origin is pasted back at the same position on restore.
 *
 * Both operations are best-effort from the caller's perspective — callers
 * should inspect [Result] and log/handle [Result.Failure] without aborting
 * business logic (e.g. a failed restore must not roll back a completed sellback).
 */
interface SchematicService {
    sealed interface Result {
        data object Success : Result
        data class Failure(val cause: Throwable) : Result
    }

    /**
     * Capture the current state of [regionId] in [world] to [outputFile].
     * Creates parent directories automatically.
     */
    fun capture(regionId: String, world: World, outputFile: File): Result

    /**
     * Restore [regionId] in [world] from [sourceFile].
     * When [async] is true the paste is dispatched asynchronously (default).
     */
    fun restore(regionId: String, world: World, sourceFile: File, async: Boolean = true): Result
}
