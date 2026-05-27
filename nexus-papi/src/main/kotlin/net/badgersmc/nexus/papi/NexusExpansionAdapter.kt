package net.badgersmc.nexus.papi

import me.clip.placeholderapi.expansion.PlaceholderExpansion
import org.bukkit.OfflinePlayer

/**
 * Bridges a Nexus [PlaceholderResolver] to PlaceholderAPI's
 * [PlaceholderExpansion]. Mostly delegation — the interesting code is in the
 * resolver itself.
 */
internal class NexusExpansionAdapter(
    private val resolver: PlaceholderResolver,
    private val meta: PapiExpansion
) : PlaceholderExpansion() {

    override fun getIdentifier(): String = meta.identifier
    override fun getAuthor(): String = meta.author
    override fun getVersion(): String = meta.version
    override fun persist(): Boolean = true
    override fun canRegister(): Boolean = true

    override fun onRequest(player: OfflinePlayer?, params: String): String? {
        return resolver.resolve(player, params)
    }
}
