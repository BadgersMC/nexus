package net.badgersmc.nexus.commands.arguments

import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass

/**
 * Global registry of ArgumentResolvers.
 *
 * Maps Kotlin types (String::class, Int::class, etc.) to ArgumentResolver instances
 * that know how to create Hytale command arguments for those types.
 *
 * **Built-in Resolvers:**
 * - String, Int, Double, Float, Boolean (registered automatically)
 *
 * **Custom Resolvers:**
 * Plugin developers can register custom resolvers for their own types:
 * ```kotlin
 * ArgumentResolvers.register(Player::class, PlayerArgumentResolver())
 * ArgumentResolvers.register(Faction::class, FactionArgumentResolver())
 * ```
 *
 * **Thread Safety:**
 * This registry is thread-safe and can be accessed from multiple threads concurrently.
 */
object ArgumentResolvers {
    private val logger = LoggerFactory.getLogger(ArgumentResolvers::class.java)
    private val resolvers = ConcurrentHashMap<KClass<*>, ArgumentResolver<*>>()

    init {
        // Register built-in resolvers for primitive types
        registerBuiltInResolvers()
        logger.debug("ArgumentResolvers initialized with built-in resolvers for: String, Int, Double, Float, Boolean")
    }

    /**
     * Register an ArgumentResolver for a specific type.
     *
     * @param type The Kotlin type (e.g., String::class, Player::class)
     * @param resolver The resolver instance
     * @throws IllegalArgumentException if a resolver is already registered for this type
     */
    fun <T : Any> register(type: KClass<T>, resolver: ArgumentResolver<T>) {
        val existing = resolvers.putIfAbsent(type, resolver)
        if (existing != null) {
            throw IllegalArgumentException(
                "ArgumentResolver already registered for type ${type.simpleName}. " +
                "Existing: ${existing::class.simpleName}, New: ${resolver::class.simpleName}"
            )
        }
        logger.debug("Registered ArgumentResolver for type: ${type.simpleName}")
    }

    /**
     * Get the ArgumentResolver for a specific type.
     *
     * @param type The Kotlin type
     * @return The resolver, or null if none is registered
     */
    @Suppress("UNCHECKED_CAST")
    fun <T : Any> get(type: KClass<T>): ArgumentResolver<T>? {
        return resolvers[type] as? ArgumentResolver<T>
    }

    /**
     * Check if a resolver is registered for a specific type.
     *
     * @param type The Kotlin type
     * @return true if a resolver exists, false otherwise
     */
    fun hasResolver(type: KClass<*>): Boolean {
        return resolvers.containsKey(type)
    }

    /**
     * Get all registered types.
     *
     * @return Set of all types with registered resolvers
     */
    fun getRegisteredTypes(): Set<KClass<*>> {
        return resolvers.keys.toSet()
    }

    /**
     * Clear all registered resolvers (primarily for testing).
     */
    internal fun clear() {
        resolvers.clear()
        logger.debug("Cleared all ArgumentResolvers")
    }
}
