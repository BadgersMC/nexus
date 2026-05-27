package net.badgersmc.nexus.paper.listeners

/**
 * Marker for Bukkit [org.bukkit.event.Listener] implementations that should be
 * picked up by [registerNexusListeners] and registered with the plugin manager
 * automatically. Apply this *in addition* to your usual
 * [net.badgersmc.nexus.annotations.Component] / `@Service` annotation so the
 * Nexus DI scanner still instantiates the class.
 *
 * Replaces the boilerplate `@PostConstruct fun register() { Bukkit.getPluginManager().registerEvents(this, plugin) }`
 * block every listener used to carry.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class Listener
