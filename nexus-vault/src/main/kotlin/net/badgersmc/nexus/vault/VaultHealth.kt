package net.badgersmc.nexus.vault

/**
 * Carrier object signalling whether Vault Economy was successfully resolved at
 * plugin enable. Mutable on purpose — `onEnable` probes Vault before the DI
 * context is created, sets the flag, then registers this instance as an
 * external bean. Downstream services inject [VaultHealth] and feature-gate.
 *
 * Pair with [VaultDegradedEvent] for code paths that need notification
 * (e.g. shutdown background workers on degraded boot).
 */
class VaultHealth {
    @Volatile
    var isAvailable: Boolean = false
}
