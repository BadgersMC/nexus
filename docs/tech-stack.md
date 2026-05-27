# Tech Stack — Nexus

**Date:** 2026-05-19
**Status:** Active development
**Owner:** BadgersMC

## 1. What this project is

Nexus is a Kotlin-first application framework providing automatic dependency injection with classpath scanning, YAML configuration management, command auto-discovery, and coroutine infrastructure for Paper plugins. Hytale support was removed in 2.0.0.

## 2. Runtimes & languages

| Layer | Language / Tool | Min version | Reason |
|---|---|---|---|
| Primary | Kotlin | 2.0.21 | Language choice for all modules |
| Build tool | Gradle (Kotlin DSL) | 8.x | Multi-module JVM build |
| Test framework | JUnit 5 + kotlin-test | 5.10.0 / 2.0.21 | Unit testing |
| JVM | JDK 21 | — | Virtual threads, classloader propagation |
| CI | GitHub Actions | — | Build + test |

## 3. Runtime dependencies

| Package | Version | Why |
|---|---|---|
| kotlinx-coroutines-core | 1.8.0 | Coroutine infrastructure |
| classgraph | 4.8.174 | Classpath scanning for DI |
| kaml | 0.59.0 | YAML config serialization |
| slf4j-api | 2.0.9 | Logging facade |
| paper-api | 1.21.11-R0.1-SNAPSHOT | Paper server API (compile-only) |
| HikariCP | 5.1.0 | Connection pooling for nexus-persistence |
| inventoryframework | 0.11.6 | GUI library backing nexus-paper-gui |
| floodgate-api / cumulus | 2.2.5 / 2.0.0 | Bedrock support (compile-only, optional at runtime) |
| VaultAPI | 1.7.1 | Economy integration (compile-only) |
| placeholderapi | 2.11.6 | Placeholder expansion (compile-only) |

## 4. Module structure

| Module | Artifact | Purpose |
|---|---|---|
| nexus-core | nexus-core | DI container, config, coroutines, command annotations |
| nexus-paper | nexus-paper | Paper Brigadier commands, BukkitDispatcher |
| nexus-resources | nexus-resources | Bundled-resource extraction |
| nexus-i18n | nexus-i18n | MiniMessage-backed YAML translator |
| nexus-persistence | nexus-persistence | HikariCP DataSource + versioned migration runner |
| nexus-scheduler | nexus-scheduler | Bukkit scheduler facade with cancel-on-disable |
| nexus-paper-loader | nexus-paper-loader | Java PluginLoader base for runtime libraries |
| nexus-paper-gui | nexus-paper-gui | IFramework-backed Adventure-aware menu helpers |
| nexus-paper-bedrock | nexus-paper-bedrock | Floodgate / Cumulus integration |
| nexus-paper-listeners | nexus-paper-listeners | @Listener marker + auto-register |
| nexus-vault | nexus-vault | EconomyProvider port + Vault adapter |
| nexus-papi | nexus-papi | PlaceholderAPI integration |
| (root) | — | Pure aggregator, no publishable artifact |

## 5. AI / agent rules

1. **Verify, don't guess.** Before writing code, confirm library APIs via context7 MCP, library source on disk, official docs, or codebase search. Record consulted sources in the task's `Evidence:` block.
2. **Use context7 MCP** for up-to-date library docs.
3. **Briefing contract.** Any subagent dispatch carries: file paths, pre-verified signatures, the failing test (for TDD tasks), acceptance criteria, forbidden actions, and the task's Evidence block.
4. **Task sizing.** If a worker briefing exceeds ~1500 tokens, decompose further before dispatch.

## 6. Versioning

Semantic versioning. Current: `2.0.0`. Bump major on breaking public-API change (e.g. the Hytale removal in 2.0.0). Sub-modules share the root version.

## 7. CI

GitHub Actions — build + test on push/PR.

## 8. Out of stack

- No runtime bytecode manipulation
- No reflection-based DI (uses ClassGraph scanning + constructor injection only)
- No support for Paper versions below 1.21.1
