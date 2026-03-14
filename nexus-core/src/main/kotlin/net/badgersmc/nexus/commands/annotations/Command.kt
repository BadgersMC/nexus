package net.badgersmc.nexus.commands.annotations

/**
 * Marks a class as a Hytale command that should be auto-discovered and registered.
 *
 * The annotated class must have exactly one method named `execute` which will be invoked
 * when the command is run. Parameters of the execute method should be annotated with
 * @Arg for user-provided arguments or @Context for runtime-injected values.
 *
 * Example:
 * ```kotlin
 * @Command(name = "heal", description = "Heal a player", permission = "admin.heal")
 * class HealCommand(private val healthService: HealthService) {
 *     fun execute(
 *         @Context context: CommandContext,
 *         @Arg("target") target: Player,
 *         @Arg("amount", required = false, defaultValue = "20") amount: Int
 *     ) {
 *         healthService.heal(target, amount)
 *     }
 * }
 * ```
 *
 * @param name The command name (e.g., "teleport", "give")
 * @param description Command description shown in help
 * @param permission Required permission to execute the command (empty = no permission required)
 * @param aliases Alternative names for the command
 * @param type The type of command (determines which Hytale base class to extend)
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class Command(
    val name: String,
    val description: String = "",
    val permission: String = "",
    val aliases: Array<String> = [],
    val type: CommandType = CommandType.PLAYER
)

/**
 * The type of command, determines which Hytale AbstractCommand class the adapter will extend.
 *
 * - ASYNC: Uses AbstractAsyncCommand (runs on background thread, no world access)
 * - PLAYER: Uses AbstractPlayerCommand (runs on world thread, player context available)
 * - TARGET_PLAYER: Uses AbstractTargetPlayerCommand (adds --player argument for targeting)
 * - TARGET_ENTITY: Uses AbstractTargetEntityCommand (uses raycasting to target entity)
 */
enum class CommandType {
    /**
     * Async command (AbstractAsyncCommand).
     * Runs on background thread, no world/entity access.
     * Use for commands that don't need world state (e.g., server rules, help).
     */
    ASYNC,

    /**
     * Player command (AbstractPlayerCommand).
     * Runs on world thread, has access to player entity, world, and stores.
     * Use for most player-initiated commands.
     */
    PLAYER,

    /**
     * Target player command (AbstractTargetPlayerCommand).
     * Like PLAYER but adds automatic --player argument for targeting another player.
     * Use for admin commands that operate on other players.
     */
    TARGET_PLAYER,

    /**
     * Target entity command (AbstractTargetEntityCommand).
     * Uses raycasting to determine which entity the player is looking at.
     * Use for commands that operate on arbitrary entities.
     */
    TARGET_ENTITY
}
