package net.badgersmc.nexus.paper.commands.annotations

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class Permission(val value: String)
