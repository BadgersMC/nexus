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

---

## Vault (Phase 4)

### REQ-180 — EconomyProvider port
**Ubiquitous.** THE SYSTEM SHALL expose an EconomyProvider interface offering balance/has/withdraw/deposit/format/isAvailable so consumers depend on the port rather than Vault directly.

### REQ-181 — Degraded mode signalling
**Event-driven.** WHEN VaultEconomyAdapter detects that the registered Economy provider has disappeared THE SYSTEM SHALL set VaultHealth.isAvailable to false and fire VaultDegradedEvent once.

### REQ-182 — Lazy provider resolution
**Ubiquitous.** THE SYSTEM SHALL resolve the Vault Economy service lazily on first use so providers registering after the adapter still work.

---

## PlaceholderAPI (Phase 4)

### REQ-190 — @PapiExpansion auto-registration
**Event-driven.** WHEN registerNexusExpansions runs THE SYSTEM SHALL register every @PapiExpansion-annotated PlaceholderResolver resolved from the Nexus context with PlaceholderAPI, or no-op gracefully if PlaceholderAPI is absent.

---

## Paper GUI (Phase 3)

### REQ-150 — ItemBuilder DSL
**Ubiquitous.** THE SYSTEM SHALL expose an `itemStack(material) { ... }` DSL building Adventure-aware ItemStacks with name, lore, amount, glow, custom-model-data, and arbitrary ItemMeta hooks.

### REQ-151 — Live polling base
**Event-driven.** WHEN a player closes a LivePollingMenu THE SYSTEM SHALL cancel its associated NexusScheduler repeat task.

### REQ-152 — Paginated list base
**Ubiquitous.** THE SYSTEM SHALL render a paginated list with prev/sort/page/next/close control row, given a typed source list and a per-entry renderer.

---

## Paper Bedrock (Phase 3)

### REQ-160 — Reflective Floodgate availability probe
**Ubiquitous.** THE SYSTEM SHALL detect Floodgate availability via reflection so plugins linking nexus-paper-bedrock run on servers without Floodgate.

### REQ-161 — Cumulus form base routes through LangService
**Event-driven.** WHEN a CumulusFormBase fails to dispatch its form THE SYSTEM SHALL send the player the LangService-resolved bedrock.menu_error message rather than a hardcoded string.

---

## Paper Listeners (Phase 3)

### REQ-170 — @Listener auto-registration
**Event-driven.** WHEN registerNexusListeners is called THE SYSTEM SHALL register every @Listener-annotated Bukkit Listener resolved from the Nexus context with the plugin manager.

---

## Scheduler (Phase 2)

### REQ-130 — Scheduled tasks return cancellable AutoCloseable
**Ubiquitous.** THE SYSTEM SHALL return an AutoCloseable from every NexusScheduler.run* method whose close cancels the underlying Bukkit task.

### REQ-131 — cancelAll cancels every outstanding scheduled task
**Event-driven.** WHEN NexusScheduler.cancelAll() is called THE SYSTEM SHALL cancel every task previously registered and not yet closed.

### REQ-132 — Thread guards
**Unwanted.** IF requireMainThread is called from a non-primary thread THE SYSTEM SHALL throw IllegalStateException. IF requireAsyncThread is called from the primary thread THE SYSTEM SHALL throw IllegalStateException.

### REQ-133 — Scheduler swallows task exceptions
**Ubiquitous.** THE SYSTEM SHALL catch any Throwable thrown by a scheduled action and log it via java.util.logging without re-throwing.

---

## Paper Loader (Phase 2)

### REQ-140 — Standard library set declared automatically
**Ubiquitous.** THE SYSTEM SHALL declare the standard Nexus runtime libraries (kotlin-stdlib, kotlin-reflect, kotlinx-coroutines-core-jvm, kaml-jvm, classgraph, slf4j-api) when a consumer extends NexusPaperPluginLoader.

### REQ-141 — repo1.maven.org used directly
**Ubiquitous.** THE SYSTEM SHALL declare https://repo1.maven.org/maven2/ as the central Maven repository on the resolver so server mirror outages do not break plugin startup.

---

## Persistence (Phase 2)

### REQ-120 — DataSource construction from declarative spec
**Event-driven.** WHEN DatabaseFactory.open is called with a DatabaseSpec THE SYSTEM SHALL return a HikariCP DataSource with pool sizing matched to the spec type (1 for Sqlite, 10 for networked, explicit for JdbcUrl).

### REQ-121 — Migration runner is idempotent
**Ubiquitous.** THE SYSTEM SHALL apply migrations in ascending version order and skip any whose version is already present in schema_migration.

### REQ-122 — Migration discovery from classpath
**Ubiquitous.** THE SYSTEM SHALL discover migration files named `V<number>__<name>.sql` under the configured resource prefix on the runner's classloader.

### REQ-123 — Statement splitter ignores quoted literals and comments
**Ubiquitous.** THE SYSTEM SHALL split migration scripts on `;` while ignoring semicolons inside single- or double-quoted string literals and `--` line comments.

---

## i18n (Phase 1)

### REQ-110 — LangService resolves keys to Adventure Components
**Ubiquitous.** THE SYSTEM SHALL resolve flat dotted keys against the loaded YAML and deserialise the matching MiniMessage value into a Component.

### REQ-111 — Missing user file falls back to bundled default
**Event-driven.** WHEN the configured locale file does not exist in the plugin data folder THE SYSTEM SHALL copy the bundled default from the consumer JAR into place before loading.

### REQ-112 — Bundled defaults overlay partial user file
**Ubiquitous.** THE SYSTEM SHALL resolve any key absent from the user locale file against the bundled default locale file, so partially-customised user files still serve every key.

### REQ-113 — Reload refreshes contents from disk
**Event-driven.** WHEN LangService.reload() is called THE SYSTEM SHALL re-read the locale file and replace its in-memory key map atomically.

---

## Resources (Phase 1)

### REQ-100 — Resource extraction never overwrites user files
**Ubiquitous.** THE SYSTEM SHALL never overwrite an existing on-disk file when `ResourceExtractor.extractIfMissing` or `ResourceExtractor.extractDirectory` is invoked.

### REQ-101 — Versioned resource overwrite is opt-in
**Event-driven.** WHEN `ResourceExtractor.overwriteIfNewerVersion` is called with `bundledVersion > currentVersion` THE SYSTEM SHALL replace the target file with the bundled resource; otherwise it SHALL leave the file untouched.

### REQ-102 — Directory extraction preserves structure
**Ubiquitous.** THE SYSTEM SHALL extract every classpath resource under a given prefix into the target directory while preserving the relative directory layout.

---

## Authoring rules

1. Every REQ has a single ID, a heading, and exactly one EARS-formatted sentence under a **pattern label**.
2. Use `spear:spec` to add or revise REQ entries — it runs the EARS validator and assigns the next free ID.
3. Never reuse an ID. When a requirement is obsolete, strike it through and note the deprecation date.
