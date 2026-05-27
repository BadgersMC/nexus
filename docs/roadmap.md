# Nexus Module Roadmap

**Status:** Phases 1–4 shipped in a single rollout (10 modules, `1.6.0` → `1.11.0`). Hytale support removed in `2.0.0`. Phase 5 (permissions Gradle plugin, archetype template) deferred.

**Goal:** absorb the boilerplate currently duplicated across EnthusiaMarket (EM), EnthusiaGiveaway (EG), and LumaSG into reusable Nexus modules. Land Nexus modules first; migrate consumers after.

**Versioning:** one minor bump per *phase rollout*, not per module. Phases 1–4 fit between `1.6.0` and `1.11.0`. `2.0.0` is the breaking Hytale-removal release — any new sub-modules going forward live under the `2.x` line until the next deliberate major.

---

## Phase 1 — i18n foundation

### `nexus-i18n` (target `1.7.0`)

Reusable MiniMessage-backed translation service. Lifted from EnthusiaMarket's `LangService`.

**Public API**
- `LangService` (`@Component`)
  - `msg(key, vararg Pair<String, Any?>): Component`
  - `legacy(key, ...): String` — §-serialised, for Cumulus/legacy paths
  - `raw(key): String` — unrendered MM template for placeholder embedding
  - `reload()`
- `@LangFile("path/inside/jar")` class-level annotation that drives default resource location (analogue to `@ConfigFile`).
- Locale resolved via a Nexus-managed `Locale` bean (consumer plugin registers it from its own config; falls back to `en_US`).

**Behaviour**
- On `reload()`: copy bundled default from JAR resources if user file missing; overlay bundled defaults onto user values so partial files still work.
- MiniMessage instance built with default tag set; consumer can register additional `TagResolver`s via `LangService.registerGlobalResolver(name, ...)`.
- `<prefix>` placeholder always available, sourced from `prefix` key.

**Acceptance**
- REQ: `WHEN a plugin registers @LangFile and a Locale bean THE SYSTEM SHALL expose a LangService bean that loads the matching YAML and reloads on demand.`

### `nexus-resources` (target `1.7.0`, shipped alongside i18n)

One-call resource extraction. Foundation for i18n + persistence migrations + default configs.

**Public API**
- `ResourceExtractor.extractIfMissing(plugin: JavaPlugin, resourcePath: String, target: File = … computed): File`
- `ResourceExtractor.extractDirectory(plugin, resourcePrefix, targetDir)` — extracts every resource under a JAR prefix.
- `ResourceExtractor.overwriteIfNewerVersion(plugin, resourcePath, target, currentVersion, bundledVersion)` — for templated defaults.

**Acceptance**
- REQ: `Ubiquitous. THE SYSTEM SHALL never overwrite an existing user file unless the caller explicitly opts in via overwriteIfNewerVersion.`

---

## Phase 2 — infrastructure

### `nexus-persistence` (target `1.8.0`)

JDBC bootstrap + versioned migration runner. Replaces EM `Database.kt`, EG persistence module, LumaSG `persistence/`.

**Public API**
- `DatabaseFactory.open(spec: DatabaseSpec): DataSource` where `DatabaseSpec` is a sealed type: `Sqlite(file)`, `MariaDB(host, port, db, user, pass)`, `Postgres(...)`.
- HikariCP pool sizing rules baked in: SQLite max=1, network DBs max≥10.
- `MigrationRunner(dataSource, resourcePrefix = "migrations")` discovers `V<number>__<name>.sql` files in JAR resources, applies in version order, records in `schema_migration` table, skips already-applied — idempotent.
- `@Repository` integration: `DataSource` resolved by qualifier.

**Acceptance**
- REQ: `Ubiquitous. THE SYSTEM SHALL apply migrations in versioned order and skip any whose version is already recorded.`

### `nexus-paper-loader` (target `1.9.0`)

Shared Paper `PluginLoader` declaring the standard runtime library set (kotlin-stdlib, kotlinx-coroutines, kaml, HikariCP, JDBC drivers, snakeyaml). Resolves Maven Central via `repo1.maven.org` directly to bypass mirror outages.

**Public API**
- `abstract class NexusPaperPluginLoader : PluginLoader` — consumer extends + optionally overrides `additionalLibraries()` / `additionalRepositories()`.

**Acceptance**
- REQ: `Event-driven. WHEN a Paper server starts a plugin extending NexusPaperPluginLoader THE SYSTEM SHALL declare the standard Nexus runtime libraries and any plugin-specific additions.`

### `nexus-scheduler` (target `1.10.0`)

Coroutine-aware Bukkit scheduler wrappers with auto-cancel on plugin disable.

**Public API**
- `NexusScheduler` (`@Component`)
  - `runRepeating(initialDelay, period, action): AutoCloseable`
  - `runDelayed(delay, action): AutoCloseable`
  - `runAsync(action): AutoCloseable`
  - `requireMainThread()` / `requireAsyncThread()` precondition checks
- All returned `AutoCloseable`s are tracked; `onDisable` cancels every outstanding task.

**Acceptance**
- REQ: `Event-driven. WHEN a plugin is disabled THE SYSTEM SHALL cancel every task scheduled via NexusScheduler.`

---

## Phase 3 — Paper UX

### `nexus-paper-gui` (target `1.11.0`)

IFramework + Adventure menu base. Replaces ChestGui copy-paste in EM `interaction/gui/`, EG menus, LumaSG GUIs.

**Public API**
- `MenuBase` interface (existing `Menu` from EM, moved + renamed).
- `ItemBuilder` DSL:
  ```kotlin
  itemStack(Material.EMERALD) {
      name(lang.msg("foo"))
      lore(lang.msg("bar"), lang.msg("baz"))
      glow()
  }
  ```
- `LivePollingMenu(plugin, refreshTicks)` — abstract; subclasses override `render(gui)`. Auto-cancels poll task on close.
- `PaginatedListMenu<T>` — generic paginated list with built-in sort/filter hooks and a controls row template.

**Acceptance**
- REQ: `WHEN a player closes a LivePollingMenu THE SYSTEM SHALL cancel its refresh task within one tick.`

### `nexus-paper-bedrock` (target `1.12.0`)

Bedrock / Floodgate / Cumulus integration.

**Public API**
- `PlatformDetectionService` — `isBedrockPlayer(uuid)`, `isCumulusAvailable()`, reflective probes so Floodgate is genuinely optional.
- `CumulusFormBase` — analogue of EM `BedrockMenuBase`, with `LangService` integration so form labels go through i18n.

**Acceptance**
- REQ: `Ubiquitous. THE SYSTEM SHALL detect Floodgate availability via reflection so plugins linking nexus-paper-bedrock can run on servers without Floodgate.`

### `nexus-paper-listeners` (target `1.13.0`)

Auto-register `@Listener`-annotated `@Component`s.

**Public API**
- `@Listener` annotation on Listener-implementing components.
- Scanner picks them up; registry calls `Bukkit.getPluginManager().registerEvents(it, plugin)` post-construct. No more boilerplate `@PostConstruct fun register()` blocks.

**Acceptance**
- REQ: `Event-driven. WHEN the Nexus context is initialised THE SYSTEM SHALL register every @Listener bean with Bukkit's event manager.`

---

## Phase 4 — integrations

### `nexus-vault` (target `1.14.0`)

Economy + permission integration via Vault.

**Public API**
- `EconomyProvider` interface (`withdraw`, `deposit`, `balance`, `format`).
- `VaultEconomyAdapter : EconomyProvider`.
- `VaultHealth` — startup probe; degraded mode emits a Bukkit event so consumers can disable features.

**Acceptance**
- REQ: `Unwanted. IF Vault is unavailable at plugin enable THE SYSTEM SHALL fire VaultDegradedEvent and EconomyProvider.withdraw/deposit SHALL return false.`

### `nexus-permissions` (target `1.15.0`)

Declarative permission tree DSL; generates `paper-plugin.yml` `permissions:` block at build time via a Gradle task.

**Public API**
- Kotlin DSL:
  ```kotlin
  permissionTree {
      node("foo.admin", default = Default.OP) {
          child("foo.admin.reload")
          child("foo.admin.import")
      }
  }
  ```
- Gradle plugin `nexus-permissions-gradle` writes the generated block into the merged `paper-plugin.yml`.

**Acceptance**
- REQ: `Ubiquitous. THE SYSTEM SHALL emit a permissions tree to paper-plugin.yml matching the declared DSL during shadowJar.`

### `nexus-papi` (target `1.16.0`)

PlaceholderAPI integration.

**Public API**
- `@PapiExpansion(identifier = "foo")` on a `@Component` implementing `PlaceholderResolver`.
- Auto-registers with PAPI if installed; no-ops gracefully if missing.

**Acceptance**
- REQ: `Event-driven. WHEN PlaceholderAPI is enabled THE SYSTEM SHALL register every @PapiExpansion bean.`

---

## Phase 5 — process + niche

### SPEAR plugin template + Konsist preset

Standalone `nexus-archetype` Gradle template + Konsist rule preset codifying hexagonal layering (domain ← application ← infrastructure). Documented under `docs/template.md`.

**Deliverables**
- `gradle init --type kotlin-application --template nexus-paper` produces a skeleton matching the EM/EG layout: `docs/{requirements,implementation,tasks,tech-stack}.md`, `src/main/kotlin/{domain,application,infrastructure}/`, Konsist test asserting layer boundaries.

### `nexus-discord` (target `1.17.0`, low priority)

Only LumaSG currently uses Discord; promote only if EG / EM add Discord features.

---

## Cross-cutting decisions

- **Module layout:** new modules become Gradle subprojects under `nexus/` (`nexus-i18n/`, `nexus-resources/`, etc.). Existing `nexus-core` / `nexus-paper` remain core dependencies.
- **Optional vs required:** each new module is **opt-in** — consumers add the dependency only if they want the feature. `nexus-core` stays minimal.
- **Backwards compat:** no existing `nexus-core` / `nexus-paper` API changes until `2.0.0`. New modules can move fast.
- **Testing:** every module ships with MockBukkit-based integration tests + a sample consumer plugin under `nexus/<module>/sample/` that doubles as the canonical usage example.

## Tracking

After this roadmap is approved:

1. Add REQs to `nexus/docs/requirements.md` in phase order — one REQ per public-API guarantee above.
2. Decompose each REQ into TDD tasks in `nexus/docs/tasks.md`.
3. Execute phases sequentially via `spear:prove → engine → arch → refine` per task.
4. Update `nexus/docs/implementation.md` per module with: module diagram, package layout, dependency policy.

No consumer migration (EM, EG, LumaSG) begins until all five phases land on Nexus `main`.
