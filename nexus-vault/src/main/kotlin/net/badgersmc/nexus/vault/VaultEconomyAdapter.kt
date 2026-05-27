package net.badgersmc.nexus.vault

import net.milkbowl.vault.economy.Economy
import org.bukkit.Bukkit
import org.bukkit.OfflinePlayer
import java.util.UUID

/**
 * [EconomyProvider] implementation backed by Vault. Re-resolves the registered
 * [Economy] service on every call so a Vault provider that registers (or is
 * removed) at runtime is always reflected accurately.
 *
 * Reports degraded health via [VaultHealth] and fires [VaultDegradedEvent] when
 * a previously-available provider disappears.
 *
 * Negative or non-finite [amount] values are rejected (Vault's contract
 * forbids negatives, and NaN/Infinity would either silently succeed or push the
 * underlying ledger into an undefined state).
 */
class VaultEconomyAdapter(
    private val health: VaultHealth
) : EconomyProvider {

    @Volatile
    private var lastSeenProvider: Economy? = null

    /**
     * Resolve the current Vault Economy provider. Always queries the services
     * manager — we deliberately do NOT cache the provider beyond a single call
     * because plugins can re-register or unregister it at runtime and a stale
     * cache hides that from [VaultHealth] and [VaultDegradedEvent] listeners.
     */
    private fun economy(): Economy? {
        val rsp = Bukkit.getServicesManager().getRegistration(Economy::class.java)
        if (rsp == null) {
            if (lastSeenProvider != null || health.isAvailable) {
                lastSeenProvider = null
                health.isAvailable = false
                Bukkit.getPluginManager().callEvent(
                    VaultDegradedEvent("Vault Economy provider unavailable")
                )
            }
            return null
        }
        val provider = rsp.provider
        lastSeenProvider = provider
        health.isAvailable = true
        return provider
    }

    private fun player(uuid: UUID): OfflinePlayer = Bukkit.getOfflinePlayer(uuid)

    private fun isValidAmount(amount: Double): Boolean = amount.isFinite() && amount >= 0.0

    override fun balance(uuid: UUID): Double =
        economy()?.getBalance(player(uuid)) ?: 0.0

    override fun has(uuid: UUID, amount: Double): Boolean {
        if (!isValidAmount(amount)) return false
        return economy()?.has(player(uuid), amount) ?: false
    }

    override fun withdraw(uuid: UUID, amount: Double): Boolean {
        if (!isValidAmount(amount)) return false
        val econ = economy() ?: return false
        val resp = econ.withdrawPlayer(player(uuid), amount)
        return resp.transactionSuccess()
    }

    override fun deposit(uuid: UUID, amount: Double): Boolean {
        if (!isValidAmount(amount)) return false
        val econ = economy() ?: return false
        val resp = econ.depositPlayer(player(uuid), amount)
        return resp.transactionSuccess()
    }

    override fun format(amount: Double): String =
        economy()?.format(amount) ?: amount.toString()

    override fun isAvailable(): Boolean = economy() != null
}
