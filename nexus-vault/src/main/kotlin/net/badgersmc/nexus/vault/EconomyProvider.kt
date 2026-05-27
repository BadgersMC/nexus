package net.badgersmc.nexus.vault

import java.util.UUID

/**
 * Domain-friendly economy port. Consumer plugins inject this rather than
 * coupling directly to Vault; production wires [VaultEconomyAdapter] behind it
 * and tests substitute an in-memory fake.
 *
 * Returns booleans rather than throwing because Vault implementations
 * historically return a `ResponseType` enum — calling code that has to deal
 * with "what if the deposit failed?" gets the same shape regardless of source.
 */
interface EconomyProvider {
    /** Current balance in major units (whatever Vault's `getBalance` returns). */
    fun balance(uuid: UUID): Double

    /** True if the player has at least [amount] available. */
    fun has(uuid: UUID, amount: Double): Boolean

    /** Withdraw [amount] from [uuid]. Returns true on success. */
    fun withdraw(uuid: UUID, amount: Double): Boolean

    /** Deposit [amount] to [uuid]. Returns true on success. */
    fun deposit(uuid: UUID, amount: Double): Boolean

    /** Render [amount] as the player would see it (e.g. "$1,234"). */
    fun format(amount: Double): String

    /** True if the underlying economy is currently healthy. */
    fun isAvailable(): Boolean = true

    /** Overload for callers passing whole numbers — convenience. */
    fun withdraw(uuid: UUID, amount: Long): Boolean = withdraw(uuid, amount.toDouble())
    fun deposit(uuid: UUID, amount: Long): Boolean = deposit(uuid, amount.toDouble())
}
