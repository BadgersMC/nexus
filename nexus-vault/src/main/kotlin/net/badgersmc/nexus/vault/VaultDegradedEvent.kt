package net.badgersmc.nexus.vault

import org.bukkit.event.Event
import org.bukkit.event.HandlerList

/**
 * Fired by [VaultEconomyAdapter] when an economy operation fails in a way that
 * suggests the underlying provider has gone away (e.g. plugin disabled,
 * connection dropped). Consumers can listen and switch to degraded mode.
 */
class VaultDegradedEvent(val reason: String) : Event() {

    override fun getHandlers(): HandlerList = HANDLERS

    companion object {
        @JvmStatic
        val HANDLERS: HandlerList = HandlerList()

        @JvmStatic
        fun getHandlerList(): HandlerList = HANDLERS
    }
}
