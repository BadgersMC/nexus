package net.badgersmc.nexus.scanning

import net.badgersmc.nexus.annotations.*
import net.badgersmc.nexus.core.BeanDefinition
import kotlin.reflect.KClass
import kotlin.reflect.full.findAnnotation

/**
 * Scans for components annotated with @Component, @Service, or @Repository.
 */
class ComponentScanner {

    /**
     * Scan a package for component classes.
     * Note: This is a simplified implementation. In production, you'd use
     * ClassGraph or similar for full classpath scanning.
     */
    fun scan(basePackage: String, classes: List<KClass<*>>): List<BeanDefinition> {
        val definitions = mutableListOf<BeanDefinition>()

        for (klass in classes) {
            val componentAnnotation = klass.findAnnotation<Component>()
            val serviceAnnotation = klass.findAnnotation<Service>()
            val repositoryAnnotation = klass.findAnnotation<Repository>()

            // Classes explicitly provided in the list are always registered as components,
            // even without annotations. Annotations are used for bean name and scope overrides.
            val beanName = determineBeanName(klass, componentAnnotation, serviceAnnotation, repositoryAnnotation)
            val scope = klass.findAnnotation<Scope>()?.value ?: ScopeType.SINGLETON

            definitions.add(
                BeanDefinition(
                    name = beanName,
                    type = klass,
                    scope = scope,
                    factory = { klass.objectInstance ?: throw IllegalStateException("Factory will be set by context") }
                )
            )
        }

        return definitions
    }

    private fun determineBeanName(
        klass: KClass<*>,
        component: Component?,
        service: Service?,
        repository: Repository?
    ): String {
        val explicitName = component?.value?.takeIf { it.isNotEmpty() }
            ?: service?.value?.takeIf { it.isNotEmpty() }
            ?: repository?.value?.takeIf { it.isNotEmpty() }

        return explicitName ?: klass.simpleName!!.replaceFirstChar { it.lowercase() }
    }
}
