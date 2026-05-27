package net.badgersmc.nexus.papi

import org.bukkit.OfflinePlayer

/**
 * Plugin-side interface for handling a PAPI placeholder request. The Nexus
 * adapter calls [resolve] with everything after the identifier (e.g. for
 * `%foo_balance_top%` with identifier `foo`, [params] is `"balance_top"`).
 *
 * Return `null` for unknown keys so PAPI falls back to default behaviour.
 */
interface PlaceholderResolver {
    fun resolve(player: OfflinePlayer?, params: String): String?
}
