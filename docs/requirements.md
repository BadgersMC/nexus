# Requirements — Nexus

**Date:** 2026-05-19
**Status:** Active — v1.5.3 → v1.6.0 (Paper 1.21.11 support)
**EARS subset enforced:** Ubiquitous, Event-driven, State-driven, Unwanted

Each requirement carries a stable ID. Tasks reference requirements by ID. New requirements append at the next free integer ID (three-digit padded); IDs are never re-used or renumbered.

---

## Paper Platform Support

### REQ-001 — Paper 1.21.11 API compatibility
**Ubiquitous.** THE SYSTEM SHALL compile against Paper API version `1.21.11-R0.1-SNAPSHOT` without errors or deprecation warnings.

### REQ-002 — Paper 1.21.11 Brigadier API compatibility
**Ubiquitous.** THE SYSTEM SHALL use the Brigadier command API available in Paper 1.21.11, including `CommandSourceStack`, `ArgumentTypes`, and `LifecycleEvents.COMMANDS`.

### REQ-003 — Backward compatibility with plugin code
**Ubiquitous.** THE SYSTEM SHALL maintain backward compatibility with existing plugin code built against Nexus 1.5.x. Plugins using `registerPaperCommands()`, `BukkitDispatcher`, and `@Command`/`@Subcommand` annotations SHALL continue to work without modification.

### REQ-004 — Paper argument resolvers functional
**Ubiquitous.** THE SYSTEM SHALL resolve Player, String, Int, Long, Double, and Boolean arguments via Paper's Brigadier argument types in version 1.21.11.

### REQ-005 — BukkitDispatcher functional
**Ubiquitous.** THE SYSTEM SHALL dispatch coroutines to the Paper main thread via `BukkitDispatcher` without errors on Paper 1.21.11.

---

## Dependency Management

### REQ-001 — Gradle build targets Paper 1.21.11
**Event-driven.** WHEN the `nexus-paper` module is built, THE SYSTEM SHALL resolve `io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT` from the Paper Maven repository.

### REQ-002 — Gradle build targets correct Kotlin version
**Ubiquitous.** THE SYSTEM SHALL use Kotlin `2.0.21` across all modules.

### REQ-003 — JVM toolchain set to 21
**Ubiquitous.** THE SYSTEM SHALL target JVM toolchain 21 for all modules.

---

## Testing

### REQ-001 — Command scanner tests pass
**Event-driven.** WHEN the test suite is run, THE SYSTEM SHALL pass all `PaperCommandScannerTest` tests against the updated Paper API.

### REQ-002 — Existing tests remain green
**Unwanted.** IF any test that passed before the 1.21.11 update fails after the update, THEN THE SYSTEM SHALL treat this as a build failure requiring investigation.

---

## Hytale Platform (unchanged)

### REQ-001 — Hytale command adapters unchanged
**Ubiquitous.** THE SYSTEM SHALL continue to support Hytale command types (ASYNC, PLAYER, TARGET_PLAYER, TARGET_ENTITY) via `nexus-core` without modification.

### REQ-002 — Hytale API dependency unchanged
**Ubiquitous.** THE SYSTEM SHALL continue to compile against `com.hypixel.hytale:Server:2026.02.11-891910c77` as a `compileOnly` dependency.

---

## Authoring rules

1. Every REQ has a single ID, a heading, and exactly one EARS-formatted sentence under a **pattern label**.
2. Use `spear:spec` to add or revise REQ entries — it runs the EARS validator and assigns the next free ID.
3. Never reuse an ID. When a requirement is obsolete, strike it through and note the deprecation date.
