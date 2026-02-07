package net.badgersmc.nexus.core

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import net.badgersmc.nexus.annotations.ScopeType
import net.badgersmc.nexus.coroutines.NexusDispatchers
import net.badgersmc.nexus.coroutines.createNexusScope
import net.badgersmc.nexus.scanning.ComponentScanner
import kotlin.reflect.KClass

/**
 * Main Nexus dependency injection container.
 * Manages component lifecycle, dependency resolution, bean creation,
 * and optional coroutine infrastructure.
 */
class NexusContext private constructor(
    private val classLoader: ClassLoader?,
    private val contextName: String
) {

    private val registry = ComponentRegistry()
    private val factory = BeanFactory(registry)
    private val scanner = ComponentScanner()
    private var initialized = false

    /**
     * Coroutine dispatchers backed by virtual threads.
     * Only available if a classLoader was provided at creation time.
     */
    val dispatchers: NexusDispatchers? = classLoader?.let {
        NexusDispatchers(it, contextName)
    }

    /**
     * Plugin-scoped CoroutineScope using SupervisorJob and virtual threads.
     * Only available if a classLoader was provided at creation time.
     *
     * All plugin coroutines should be launched in this scope to ensure:
     * - Automatic cancellation on context close
     * - Structured concurrency with SupervisorJob
     * - Correct classloader propagation to virtual threads
     */
    val scope: CoroutineScope? = dispatchers?.let {
        createNexusScope(it, contextName)
    }

    companion object {
        /**
         * Create a new NexusContext by scanning the classpath for annotated components.
         *
         * Discovers all classes in [basePackage] (and sub-packages) annotated with
         * @Component, @Service, or @Repository using ClassGraph.
         *
         * @param basePackage Base package to scan for components
         * @param classLoader The classloader to scan (required — typically the plugin's classloader)
         * @param contextName Name for the context (used in thread names and coroutine debugging)
         */
        fun create(
            basePackage: String,
            classLoader: ClassLoader,
            contextName: String = "nexus"
        ): NexusContext {
            val context = NexusContext(classLoader, contextName)
            context.registerCoroutineBeans()
            context.initialize(basePackage, classLoader)
            return context
        }

        /**
         * Create a new NexusContext with manual bean registration only.
         * Use [registerBean] to add beans after creation.
         */
        fun create(
            classLoader: ClassLoader? = null,
            contextName: String = "nexus"
        ): NexusContext {
            val context = NexusContext(classLoader, contextName)
            context.registerCoroutineBeans()
            return context
        }
    }

    /**
     * Register the coroutine scope and dispatchers as injectable beans.
     */
    private fun registerCoroutineBeans() {
        dispatchers?.let { d ->
            registerBean("nexusDispatchers", NexusDispatchers::class, d)
        }
        scope?.let { s ->
            registerBean("nexusScope", CoroutineScope::class, s)
        }
    }

    /**
     * Initialize the context by scanning the classpath for annotated components.
     */
    private fun initialize(basePackage: String, classLoader: ClassLoader) {
        if (initialized) {
            throw IllegalStateException("Context already initialized")
        }

        // Scan classpath for annotated component definitions
        val definitions = scanner.scan(basePackage, classLoader)

        // Register all definitions (but don't create instances yet — factories are lazy)
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
     * Shutdown the context.
     *
     * Order of operations:
     * 1. Cancel the coroutine scope (stops all running coroutines)
     * 2. Invoke @PreDestroy on all singletons
     * 3. Shutdown the virtual thread executor
     * 4. Clear the registry
     */
    fun close() {
        scope?.cancel("NexusContext closing")

        registry.getAllSingletons().forEach { bean ->
            factory.invokePreDestroy(bean)
        }

        dispatchers?.shutdown()

        registry.clear()
        initialized = false
    }

    private fun ensureInitialized() {
        if (!initialized && registry.getAllBeanNames().isEmpty()) {
            throw IllegalStateException("Context not initialized. Call create() or register beans manually.")
        }
        initialized = true
    }
}
