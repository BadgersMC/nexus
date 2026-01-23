package net.badgersmc.nexus.core

import net.badgersmc.nexus.annotations.ScopeType
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass

/**
 * Registry for all component definitions in the Nexus container.
 * Thread-safe storage for bean definitions and singleton instances.
 */
class ComponentRegistry {

    private val definitions = ConcurrentHashMap<String, BeanDefinition>()
    private val singletons = ConcurrentHashMap<String, Any>()
    private val typeIndex = ConcurrentHashMap<KClass<*>, MutableList<String>>()

    /**
     * Register a bean definition with the container.
     */
    fun register(definition: BeanDefinition) {
        definitions[definition.name] = definition

        // Index by type for lookup
        typeIndex.computeIfAbsent(definition.type) { mutableListOf() }.add(definition.name)
    }

    /**
     * Get a bean definition by name.
     */
    fun getDefinition(name: String): BeanDefinition? {
        return definitions[name]
    }

    /**
     * Get all bean definitions of a specific type.
     */
    fun getDefinitionsByType(type: KClass<*>): List<BeanDefinition> {
        val names = typeIndex[type] ?: return emptyList()
        return names.mapNotNull { definitions[it] }
    }

    /**
     * Check if a bean with the given name exists.
     */
    fun contains(name: String): Boolean {
        return definitions.containsKey(name)
    }

    /**
     * Store a singleton instance.
     */
    fun putSingleton(name: String, instance: Any) {
        singletons[name] = instance
    }

    /**
     * Retrieve a singleton instance.
     */
    fun getSingleton(name: String): Any? {
        return singletons[name]
    }

    /**
     * Get all registered bean names.
     */
    fun getAllBeanNames(): Set<String> {
        return definitions.keys
    }

    /**
     * Get all singleton instances for lifecycle management.
     */
    fun getAllSingletons(): Collection<Any> {
        return singletons.values
    }

    /**
     * Clear all registrations (for testing or shutdown).
     */
    fun clear() {
        definitions.clear()
        singletons.clear()
        typeIndex.clear()
    }
}
