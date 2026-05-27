package net.badgersmc.nexus.papi

import io.github.classgraph.ClassGraph
import net.badgersmc.nexus.core.NexusContext
import org.bukkit.Bukkit
import java.util.logging.Logger

/**
 * Scan [basePackage] for [PapiExpansion]-annotated [PlaceholderResolver]s,
 * resolve them from the Nexus context, and register them with PlaceholderAPI.
 *
 * Silently does nothing when PlaceholderAPI is not installed — consumers can
 * still call this from `onEnable` without guarding it.
 */
fun registerNexusExpansions(
    basePackage: String,
    classLoader: ClassLoader,
    nexus: NexusContext
) {
    val logger = Logger.getLogger("NexusPapiRegistry")
    if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") == null) {
        logger.fine("PlaceholderAPI not installed — skipping @PapiExpansion registration")
        return
    }

    val candidates = ClassGraph()
        .acceptPackages(basePackage)
        .addClassLoader(classLoader)
        .enableAnnotationInfo()
        .scan()
        .use { result ->
            result.getClassesWithAnnotation(PapiExpansion::class.java.name)
                .filter { !it.isAbstract && !it.isInterface }
                .mapNotNull { it.loadClass() }
        }

    var registered = 0
    for (cls in candidates) {
        if (!PlaceholderResolver::class.java.isAssignableFrom(cls)) {
            logger.warning("@PapiExpansion on ${cls.name} is not a PlaceholderResolver — skipping")
            continue
        }
        val annotation = cls.getAnnotation(PapiExpansion::class.java) ?: continue
        try {
            val resolver = nexus.getBean(cls.kotlin) as PlaceholderResolver
            val adapter = NexusExpansionAdapter(resolver, annotation)
            adapter.register()
            registered++
        } catch (e: Exception) {
            logger.warning("Failed to register @PapiExpansion ${cls.name}: ${e.message}")
        }
    }
    logger.info("Registered $registered PlaceholderAPI expansions")
}
