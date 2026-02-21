package net.badgersmc.nexus.commands.annotations

/**
 * Marks a parameter as a command argument that will be provided by the user.
 *
 * The parameter type must have a registered ArgumentResolver in the ArgumentResolvers registry.
 * Built-in resolvers exist for String, Int, Double, Float, and Boolean.
 *
 * Example:
 * ```kotlin
 * fun execute(
 *     @Arg("target", "Player to heal") target: Player,
 *     @Arg("amount", "Amount of health", required = false, defaultValue = "20") amount: Int
 * )
 * ```
 *
 * **Argument Order Rules:**
 * - Required arguments must come before optional arguments
 * - Arguments are processed left-to-right in the method signature
 *
 * **Argument Types:**
 * - Required (required = true, no defaultValue): User must provide, validated at startup
 * - Optional (required = false): Uses Hytale's --name value syntax, can be omitted
 * - Default (defaultValue specified): Uses Hytale's default argument, value used if omitted
 *
 * @param name The argument name (used for --name flags and error messages)
 * @param description Description shown in help text
 * @param required Whether the argument is required (true) or optional (false)
 * @param defaultValue Default value if not provided (makes this a DefaultArg in Hytale)
 */
@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
annotation class Arg(
    val name: String,
    val description: String = "",
    val required: Boolean = true,
    val defaultValue: String = ""
)
