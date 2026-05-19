# Tasks — Nexus

**Date:** 2026-05-19
**Status:** Active — v1.5.3 → v1.6.0 (Paper 1.21.11 support)

Tags: `TDD` (failing test before code), `DOC` (markdown / template authoring), `INFRA` (manifests, CI, repo plumbing).
State legend: `[ ]` not started, `[~]` in progress, `[x]` done, `[!]` blocked.

Each task carries `References:` (REQ-IDs + spec sections consulted) and `Evidence:` (sources consulted as work proceeds).

---

## Milestone 1.6.0 — Paper 1.21.11 Support

### INFRA tasks

- [ ] **INFRA-01** — Update root build.gradle.kts Paper dependency
  References: REQ-001 (Paper Platform Support), REQ-001 (Dependency Management)
  Tag: INFRA
  Description: Update the root `build.gradle.kts` to reference Paper API `1.21.11-R0.1-SNAPSHOT`. Update the Hytale Maven repository URL if needed. Ensure Kotlin 2.0.21 and JVM toolchain 21 are preserved.
  Evidence: ` `

- [ ] **INFRA-02** — Update nexus-paper build.gradle.kts for Paper 1.21.11
  References: REQ-001 (Paper Platform Support), REQ-001 (Dependency Management)
  Tag: INFRA
  Description: Update `nexus-paper/build.gradle.kts` to change `io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT` to `1.21.11-R0.1-SNAPSHOT` in both `compileOnly` and `testImplementation` blocks.
  Evidence: ` `

### TDD tasks

- [ ] **TDD-01** — Verify Paper 1.21.11 API compatibility in PaperCommandRegistry
  References: REQ-002, REQ-003, REQ-004
  Tag: TDD
  Description: Update `PaperCommandRegistry.kt` if any Brigadier API signatures changed in 1.21.11. Key areas: `CommandSourceStack`, `ArgumentTypes.player()`, `LifecycleEvents.COMMANDS`, `Commands.literal()`. Write a test that verifies command registration and execution works with the new API. Run existing `PaperCommandScannerTest` to confirm green.
  Evidence: ` `

- [ ] **TDD-02** — Verify BukkitDispatcher works with Paper 1.21.11
  References: REQ-005
  Tag: TDD
  Description: Verify `BukkitDispatcher.kt` compiles and functions correctly with Paper 1.21.11. The `Bukkit.isPrimaryThread()` and `plugin.server.scheduler.runTask()` APIs should be verified against 1.21.11. Write a test if possible.
  Evidence: ` `

- [ ] **TDD-03** — Verify PaperArgumentResolvers work with Paper 1.21.11
  References: REQ-004
  Tag: TDD
  Description: Verify `PaperArgumentResolvers.kt` compiles and functions with Paper 1.21.11. Key areas: `ArgumentTypes.player()` return type, `PlayerSelectorArgumentResolver`, `StringArgumentType.word()`. Write tests for each resolver.
  Evidence: ` `

- [ ] **TDD-04** — Full test suite green after 1.21.11 update
  References: REQ-001 (Testing), REQ-002 (Testing)
  Tag: TDD
  Description: Run the full Gradle test suite (`./gradlew test`) after all code changes. All tests that passed before must still pass. Fix any failures introduced by the API update.
  Evidence: ` `

### DOC tasks

- [ ] **DOC-01** — Update README.md for Paper 1.21.11
  References: REQ-001 (Paper Platform Support)
  Tag: DOC
  Description: Update the README to reflect Paper 1.21.11 as the supported version. Update the version badge and any API references.
  Evidence: ` `

- [ ] **DOC-02** — Bump version to 1.6.0
  References: REQ-001 (Paper Platform Support)
  Tag: DOC
  Description: Update `build.gradle.kts` root `version` from `1.5.3` to `1.6.0`.
  Evidence: ` `

---

## Task authoring rules

1. Every task has exactly ONE tag (`TDD`, `DOC`, or `INFRA`).
2. `References:` cites at least one REQ-ID from `requirements.md`.
3. `Evidence:` starts empty. It must be filled before any skill past `spec-done` will run.
4. Task size ceiling: ~1500 tokens of full briefing. If larger, split.
5. Mark state as work proceeds: `[~]` when entering `spec`; `[x]` only when `spear:refine` has cleared state to `idle`.
