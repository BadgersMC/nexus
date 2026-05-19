# Implementation — Nexus

**Date:** 2026-05-19
**Status:** Active — v1.5.3 → v1.6.0 (Paper 1.21.11 support)
**Owner:** BadgersMC

## 1. Repo layout (canonical)

```
nexus/
├── nexus-core/              # DI container, config, coroutines, Hytale commands
│   ├── build.gradle.kts
│   └── src/main/kotlin/net/badgersmc/nexus/
│       ├── core/            # NexusContext, ComponentRegistry, BeanFactory, BeanDefinition
│       ├── scanning/        # ComponentScanner (ClassGraph)
│       ├── annotations/     # @Component, @Service, @Repository, @Inject, @Qualifier, @Scope, @PostConstruct, @PreDestroy
│       ├── coroutines/      # NexusDispatchers, NexusScope, CoroutineExtensions
│       ├── config/          # ConfigManager, ConfigLoader, @ConfigFile, @ConfigName, @Comment, @Transient
│       └── commands/        # CommandScanner, CommandRegistry, CommandDefinition, adapters/
├── nexus-paper/             # Paper Brigadier commands, BukkitDispatcher
│   ├── build.gradle.kts
│   └── src/main/kotlin/net/badgersmc/nexus/paper/
│       ├── BukkitDispatcher.kt
│       ├── PaperNexusExtensions.kt
│       ├── PaperExtensions.kt
│       └── commands/
│           ├── PaperCommandScanner.kt
│           ├── PaperCommandRegistry.kt
│           ├── PaperCommandDefinition.kt
│           ├── annotations/  # @Subcommand, @Permission, @PlayerOnly, @Async, @Suggests
│           └── arguments/    # PaperArgumentResolver, PaperArgumentResolvers
├── docs/
│   ├── tech-stack.md
│   ├── requirements.md
│   ├── implementation.md
│   └── tasks.md
├── build.gradle.kts         # Root build config
├── settings.gradle.kts
└── .claude/
    └── spear-state.json     # SPEAR state (gitignored)
```

## 2. Layer Dependency Rules

The three-layer discipline SPEAR enforces. `spear:arch` reads this exact section and blocks on violations.

| Layer | Concrete files | May depend on |
|---|---|---|
| `domain/` (rules-of-the-game) | `nexus-core/src/main/kotlin/.../core/**`, `.../annotations/**`, `.../scanning/**` | nothing outside `nexus-core` + Kotlin stdlib + coroutines + classgraph + kaml + slf4j |
| `application/` (use cases / workflow) | `nexus-paper/src/main/kotlin/.../paper/commands/**` | `nexus-core` (domain) only |
| `infrastructure/` (adapters, frameworks, I/O) | `nexus-paper/src/main/kotlin/.../paper/BukkitDispatcher.kt`, `PaperNexusExtensions.kt`, `PaperExtensions.kt` | anything (Paper API, Bukkit API) |

Key constraint: `nexus-core` has zero dependency on Paper/Bukkit APIs. `nexus-paper` depends on `nexus-core` and Paper API.

## Forbidden Domain Annotations

Framework annotations that must NOT appear on any type under `nexus-core/**` (the domain layer). `spear:arch` scans for these.

```yaml
# nexus-core must remain framework-agnostic (no Paper/Bukkit imports)
# nexus-paper is the infrastructure layer and may use any framework
forbidden: []
```

## 3. Component design

### 3.1 NexusContext (domain)

Main DI container. Manages component lifecycle, dependency resolution, bean creation, and optional coroutine infrastructure. Created via `NexusContext.create()` factory methods.

Key beans: `NexusDispatchers`, `CoroutineScope`, `ConfigManager`, all `@Component`/`@Service`/`@Repository` scanned classes.

### 3.2 BeanFactory (domain)

Handles constructor injection, lifecycle callbacks (`@PostConstruct`/`@PreDestroy`), singleton/prototype scoping, and qualifier resolution.

### 3.3 ComponentScanner (domain)

ClassGraph-based classpath scanning for `@Component`, `@Service`, `@Repository`, `@ConfigFile` annotations.

### 3.4 PaperCommandScanner (application)

Scans for `@Command` classes with `@Subcommand` methods in the Paper module. Validates argument types have registered resolvers.

### 3.5 PaperCommandRegistry (application)

Builds Brigadier command trees from scanned definitions and registers with Paper's `LifecycleEvents.COMMANDS`.

### 3.6 BukkitDispatcher (infrastructure)

Coroutine dispatcher that runs on Paper's main thread. Skips dispatch when already on main thread.

## 4. Data flows

### 4.1 Plugin startup (Paper)

```
Plugin.onEnable()
  → NexusContext.create(basePackage, classLoader, configDirectory, externalBeans)
    → registerCoroutineBeans()
    → register external beans
    → loadAndRegisterConfigs()
    → initialize() → ComponentScanner.scan() → register all @Component/@Service/@Repository
  → nexus.registerPaperCommands(basePackage, classLoader, plugin)
    → PaperCommandScanner.scanCommands() → List<PaperCommandDefinition>
    → PaperCommandRegistry.registerAll()
      → LifecycleEvents.COMMANDS handler
        → buildCommandTree() → Brigadier LiteralCommandNode
        → event.registrar().register()
```

### 4.2 Command execution (Paper)

```
Player runs /cmd sub [args]
  → Brigadier dispatches to PaperCommandRegistry.executeSubcommand()
    → buildParams() → resolve @Arg via PaperArgumentResolvers, @Context via CommandSourceStack
    → if @Async: nexusScope.launch { method.call(bean, *params) }
    → else: method.call(bean, *params)
```

## 5. Briefing contract for subagent dispatch

Every `delegate_task` dispatch for implementation work carries:

- Exact file paths to create / modify.
- Pre-verified signatures (from context7, library source on disk, or codebase search).
- The failing test (path + test name) for TDD tasks.
- Acceptance criteria — which test goes green; which files MUST NOT change.
- Forbidden actions — scope fences.
- The task's `Evidence:` block verbatim.

Tasks whose full briefing exceeds ~1500 tokens are decomposed further by `spear:spec` before dispatch.

## 6. Versioning

Semantic versioning. Current: `1.5.3`. Bump major on breaking public-API change. This update (Paper 1.21.11 support) is a minor bump to `1.6.0`.

## 7. Out of scope (this doc)

- Hytale-specific command adapter internals — owned by `nexus-core/src/main/kotlin/.../commands/adapters/`.
- CI workflow — owned by `.github/workflows/`.
- Konsist test — Nexus is a library, not a consumer project; architecture tests are the consumer's responsibility.
