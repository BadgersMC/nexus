# Tech Stack — Nexus

**Date:** 2026-05-19
**Status:** Active development
**Owner:** BadgersMC

## 1. What this project is

Nexus is a Kotlin-first application framework providing automatic dependency injection with classpath scanning, YAML configuration management, command auto-discovery, and coroutine infrastructure for Minecraft server platforms (Hytale and Paper).

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
| paper-api | 1.21.4-R0.1-SNAPSHOT | Paper server API (compile-only) |
| hytale-server | 2026.02.11 | Hytale server API (compile-only) |

## 4. Module structure

| Module | Artifact | Purpose |
|---|---|---|
| nexus-core | nexus-core | DI container, config, coroutines, Hytale commands |
| nexus-paper | nexus-paper | Paper Brigadier commands, BukkitDispatcher |
| (root) | nexus | Aggregator/publishing root |

## 5. AI / agent rules

1. **Verify, don't guess.** Before writing code, confirm library APIs via context7 MCP, library source on disk, official docs, or codebase search. Record consulted sources in the task's `Evidence:` block.
2. **Use context7 MCP** for up-to-date library docs.
3. **Briefing contract.** Any subagent dispatch carries: file paths, pre-verified signatures, the failing test (for TDD tasks), acceptance criteria, forbidden actions, and the task's Evidence block.
4. **Task sizing.** If a worker briefing exceeds ~1500 tokens, decompose further before dispatch.

## 6. Versioning

Semantic versioning. Current: `1.5.3`. Bump major on breaking public-API change.

## 7. CI

GitHub Actions — build + test on push/PR.

## 8. Out of stack

- No runtime bytecode manipulation
- No reflection-based DI (uses ClassGraph scanning + constructor injection only)
- No support for Paper versions below 1.21.1
