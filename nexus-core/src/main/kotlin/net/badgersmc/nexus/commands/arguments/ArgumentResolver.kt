package net.badgersmc.nexus.commands.arguments

import kotlin.reflect.KClass

/**
 * Maps a Kotlin type to Hytale command arguments.
 *
 * ArgumentResolvers are responsible for creating Hytale argument objects (RequiredArg,
 * OptionalArg, DefaultArg) from Kotlin parameter types. Each resolver handles one type
 * (e.g., String, Int, Player).
 *
 * **Implementation Notes:**
 * - The `command` parameter is the Hytale command instance (e.g., AbstractPlayerCommand)
 * - Call methods like `command.withRequiredArg(name, description, argType)` to create arguments
 * - Store the returned argument object and return it
 * - For type conversion (e.g., String → Int for defaultValue), handle parsing in the resolver
 *
 * Example implementation:
 * ```kotlin
 * object StringArgumentResolver : ArgumentResolver<String> {
 *     override val type = String::class
 *
 *     override fun createRequiredArg(command: Any, name: String, description: String): Any {
 *         return (command as BaseCommand).withRequiredArg(name, description, ArgTypes.STRING)
 *     }
 *
 *     // ... implement other methods
 * }
 * ```
 *
 * @param T The Kotlin type this resolver handles (e.g., String, Int, Player)
 */
interface ArgumentResolver<T : Any> {
    /**
     * The Kotlin type this resolver handles.
     */
    val type: KClass<T>

    /**
     * Create a required argument (user must provide a value).
     *
     * @param command The Hytale command instance (can be cast to BaseCommand or specific type)
     * @param name The argument name (for --name flags and errors)
     * @param description The argument description (for help text)
     * @return The Hytale argument object (RequiredArg<T>)
     */
    fun createRequiredArg(command: Any, name: String, description: String): Any

    /**
     * Create an optional argument (user can omit, uses --name value syntax).
     *
     * @param command The Hytale command instance
     * @param name The argument name
     * @param description The argument description
     * @return The Hytale argument object (OptionalArg<T>)
     */
    fun createOptionalArg(command: Any, name: String, description: String): Any

    /**
     * Create a default argument (user can omit, uses provided default value).
     *
     * @param command The Hytale command instance
     * @param name The argument name
     * @param description The argument description
     * @param defaultValue The default value as a string (must be parsed to type T)
     * @return The Hytale argument object (DefaultArg<T>)
     */
    fun createDefaultArg(command: Any, name: String, description: String, defaultValue: String): Any
}
