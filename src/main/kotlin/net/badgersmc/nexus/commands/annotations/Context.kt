package net.badgersmc.nexus.commands.annotations

/**
 * Marks a parameter as a context value that should be injected at runtime (not user-provided).
 *
 * Context parameters are automatically populated by the command adapter based on the
 * parameter type and command type. They allow access to Hytale's command execution context.
 *
 * **Supported Types (by CommandType):**
 *
 * ALL command types:
 * - `CommandContext` — The Hytale command context (sender, message sending, etc.)
 *
 * PLAYER, TARGET_PLAYER, TARGET_ENTITY:
 * - `World` — The world where the command was executed
 * - `Store<EntityStore>` — The entity store for the world
 *
 * PLAYER, TARGET_PLAYER:
 * - `PlayerRef` — Reference to the player who executed the command
 * - `Ref<EntityStore>` — Entity reference to the player who executed the command
 *
 * TARGET_PLAYER, TARGET_ENTITY:
 * - `Ref<EntityStore>` (when named "targetRef" or similar) — The targeted entity
 *
 * Example:
 * ```kotlin
 * @Command(name = "heal", type = CommandType.PLAYER)
 * class HealCommand {
 *     fun execute(
 *         @Context context: CommandContext,
 *         @Context world: World,
 *         @Context store: Store<EntityStore>,
 *         @Arg("amount") amount: Int
 *     ) {
 *         // Implementation
 *     }
 * }
 * ```
 *
 * **Note:** Context parameters can appear in any order relative to @Arg parameters,
 * but it's conventional to place them first.
 */
@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
annotation class Context
