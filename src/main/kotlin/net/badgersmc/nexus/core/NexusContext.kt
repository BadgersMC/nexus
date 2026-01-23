package net.badgersmc.nexus.core

import net.badgersmc.nexus.annotations.ScopeType
import net.badgersmc.nexus.scanning.ComponentScanner
import kotlin.reflect.KClass

/**
 * Main Nexus dependency injection container.
 * Manages component lifecycle, dependency resolution, and bean creation.
 */
class NexusContext private constructor() {

    private val registry = ComponentRegistry()
    private val factory = BeanFactory(registry)
    private val scanner = ComponentScanner()
    private var initialized = false

    companion object {
        /**
         * Create a new NexusContext by scanning for components.
         */
        fun create(basePackage: String, classes: List<KClass<*>>): NexusContext {
            val context = NexusContext()
            context.initialize(basePackage, classes)
            return context
        }

        /**
         * Create a new NexusContext with manual bean registration.
         */
        fun create(): NexusContext {
            return NexusContext()
        }
    }

    /**
     * Initialize the context by scanning for components.
     */
    private fun initialize(basePackage: String, classes: List<KClass<*>>) {
        if (initialized) {
            throw IllegalStateException("Context already initialized")
        }

        // Scan for component definitions
        val definitions = scanner.scan(basePackage, classes)

        // Register all definitions (but don't create instances yet)
        definitions.forEach { definition ->
            val updatedDefinition = definition.copy(
                factory = factory.createFactory(definition.type)
            )
            registry.register(updatedDefinition)
        }

        initialized = true
    }

    /**
     * Get a bean by name.
     */
    fun getBean(name: String): Any {
        ensureInitialized()
        return factory.getBean(name)
    }

    /**
     * Get a bean by type.
     */
    fun <T : Any> getBean(type: KClass<T>): T {
        ensureInitialized()
        return factory.getBean(type)
    }

    /**
     * Get a bean by type (reified version for easier usage).
     */
    inline fun <reified T : Any> getBean(): T {
        return getBean(T::class)
    }

    /**
     * Manually register a bean with the context.
     */
    fun <T : Any> registerBean(name: String, type: KClass<T>, instance: T) {
        val definition = BeanDefinition(
            name = name,
            type = type,
            scope = ScopeType.SINGLETON,
            factory = { instance }
        )
        registry.register(definition)
        registry.putSingleton(name, instance)
    }

    /**
     * Manually register a bean factory.
     */
    fun <T : Any> registerBean(
        name: String,
        type: KClass<T>,
        scope: ScopeType = ScopeType.SINGLETON,
        factory: () -> T
    ) {
        val definition = BeanDefinition(
            name = name,
            type = type,
            scope = scope,
            factory = factory
        )
        registry.register(definition)
    }

    /**
     * Check if a bean with the given name exists.
     */
    fun containsBean(name: String): Boolean {
        return registry.contains(name)
    }

    /**
     * Get all registered bean names.
     */
    fun getBeanNames(): Set<String> {
        return registry.getAllBeanNames()
    }

    /**
     * Shutdown the context and invoke @PreDestroy methods.
     */
    fun close() {
        registry.getAllSingletons().forEach { bean ->
            factory.invokePreDestroy(bean)
        }
        registry.clear()
        initialized = false
    }

    private fun ensureInitialized() {
        if (!initialized && registry.getAllBeanNames().isEmpty()) {
            throw IllegalStateException("Context not initialized. Call initialize() or register beans manually.")
        }
        initialized = true
    }
}
