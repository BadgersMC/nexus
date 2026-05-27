package net.badgersmc.nexus.paper.listeners

import io.github.classgraph.ClassGraph
import net.badgersmc.nexus.core.NexusContext
import org.bukkit.Bukkit
import org.bukkit.event.Listener as BukkitListener
import org.bukkit.plugin.java.JavaPlugin
import java.util.logging.Logger

/**
 * Discovers every class in [basePackage] annotated with [Listener] AND
 * implementing [BukkitListener], resolves it from the Nexus DI context, and
 * registers it with the plugin manager.
 *
 * Call once from `onEnable` after constructing your [NexusContext]:
 *
 * ```
 * registerNexusListeners(
 *     basePackage = "net.badgersmc.em",
 *     classLoader = this::class.java.classLoader,
 *     plugin = this,
 *     nexus = nexusContext
 * )
 * ```
 */
fun registerNexusListeners(
    basePackage: String,
    classLoader: ClassLoader,
    plugin: JavaPlugin,
    nexus: NexusContext
) {
    val logger = Logger.getLogger("NexusListenerRegistry")
    val candidates = ClassGraph()
        .acceptPackages(basePackage)
        .addClassLoader(classLoader)
        .enableAnnotationInfo()
        .scan()
        .use { result ->
            result.getClassesWithAnnotation(Listener::class.java.name)
                .filter { !it.isAbstract && !it.isInterface }
                // Isolate per-class load failures so one bad class doesn't
                // abort discovery for every other listener in the package.
                .mapNotNull { classInfo ->
                    try {
                        classInfo.loadClass()
                    } catch (e: Throwable) {
                        logger.log(
                            java.util.logging.Level.WARNING,
                            "Failed to load @Listener candidate ${classInfo.name}",
                            e
                        )
                        null
                    }
                }
        }

    var registered = 0
    for (cls in candidates) {
        if (!BukkitListener::class.java.isAssignableFrom(cls)) {
            logger.warning("@Listener on ${cls.name} is not a Bukkit Listener — skipping")
            continue
        }
        try {
            val instance = nexus.getBean(cls.kotlin) as BukkitListener
            Bukkit.getPluginManager().registerEvents(instance, plugin)
            registered++
        } catch (e: Exception) {
            logger.log(
                java.util.logging.Level.WARNING,
                "Failed to register listener ${cls.name}",
                e
            )
        }
    }
    logger.info("Registered $registered @Listener beans with Bukkit")
}
