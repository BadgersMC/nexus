package net.badgersmc.nexus.papi

/**
 * Marks a class as a PlaceholderAPI expansion. The annotated class must also
 * implement [PlaceholderResolver]. The [identifier] is the namespace players
 * use in PAPI syntax — `%<identifier>_<key>%`.
 *
 * Apply alongside `@Component` so the Nexus DI scanner instantiates the
 * expansion, then call [registerNexusExpansions] in `onEnable` after creating
 * the [net.badgersmc.nexus.core.NexusContext].
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class PapiExpansion(
    val identifier: String,
    val author: String = "BadgersMC",
    val version: String = "1.0.0"
)
