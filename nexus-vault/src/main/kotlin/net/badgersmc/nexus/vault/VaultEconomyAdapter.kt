package net.badgersmc.nexus.vault

import net.milkbowl.vault.economy.Economy
import org.bukkit.Bukkit
import org.bukkit.OfflinePlayer
import java.util.UUID

/**
 * [EconomyProvider] implementation backed by Vault. Resolves the registered
 * [Economy] service lazily on first use so a Vault provider that registers
 * *after* this adapter is constructed still works.
 *
 * Reports degraded health via [VaultHealth] and fires [VaultDegradedEvent]
 * the first time an operation returns failure due to a missing provider.
 */
class VaultEconomyAdapter(
    private val health: VaultHealth
) : EconomyProvider {

    @Volatile
    private var cached: Economy? = null

    private fun economy(): Economy? {
        cached?.let { return it }
        val rsp = Bukkit.getServicesManager().getRegistration(Economy::class.java)
        if (rsp == null) {
            if (health.isAvailable) {
                health.isAvailable = false
                Bukkit.getPluginManager().callEvent(
                    VaultDegradedEvent("Vault Economy provider unavailable")
                )
            }
            return null
        }
        val provider = rsp.provider
        cached = provider
        health.isAvailable = true
        return provider
    }

    private fun player(uuid: UUID): OfflinePlayer = Bukkit.getOfflinePlayer(uuid)

    override fun balance(uuid: UUID): Double =
        economy()?.getBalance(player(uuid)) ?: 0.0

    override fun has(uuid: UUID, amount: Double): Boolean =
        economy()?.has(player(uuid), amount) ?: false

    override fun withdraw(uuid: UUID, amount: Double): Boolean {
        val econ = economy() ?: return false
        val resp = econ.withdrawPlayer(player(uuid), amount)
        return resp.transactionSuccess()
    }

    override fun deposit(uuid: UUID, amount: Double): Boolean {
        val econ = economy() ?: return false
        val resp = econ.depositPlayer(player(uuid), amount)
        return resp.transactionSuccess()
    }

    override fun format(amount: Double): String =
        economy()?.format(amount) ?: amount.toString()

    override fun isAvailable(): Boolean {
        // Probe before answering — keeps the health flag accurate over time.
        return economy() != null
    }
}
