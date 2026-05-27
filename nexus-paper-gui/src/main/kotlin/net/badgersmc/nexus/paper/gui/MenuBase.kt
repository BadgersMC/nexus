package net.badgersmc.nexus.paper.gui

import org.bukkit.entity.Player

/**
 * Marker interface for player-facing menus. Adopted from EnthusiaMarket's
 * original `Menu` type so plugin code can talk to "a menu" regardless of
 * whether the implementation is IFramework (Java) or Cumulus (Bedrock).
 */
interface MenuBase {
    fun open(player: Player)
}
