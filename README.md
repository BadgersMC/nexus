# Nexus - Application Framework for Hytale

**Nexus** is a Kotlin-first application framework for Hytale mods providing automatic dependency injection with classpath scanning, YAML configuration management, and coroutine infrastructure backed by Java 21 virtual threads.

## Features

### Dependency Injection with Classpath Scanning

- **Automatic Component Discovery**: Annotate classes with `@Component`, `@Service`, or `@Repository` and they're found at startup via [ClassGraph](https://github.com/classgraph/classgraph) - no registration lists to maintain
- **Constructor Injection**: Dependencies resolved automatically through primary constructors
- **Lifecycle Management**: `@PostConstruct` and `@PreDestroy` hooks (supports suspend functions)
- **Scopes**: Singleton (default) and Prototype scopes via `@Scope`
- **Polymorphic Resolution**: Beans resolved by interface or superclass type
- **Qualifier Support**: `@Qualifier("name")` to disambiguate multiple beans of the same type
- **Thread-safe**: Concurrent access with double-check locking for singletons

### Coroutine Infrastructure

- **Virtual Thread Dispatchers**: Java 21 virtual threads with automatic classloader propagation
- **Per-Plugin Scopes**: Each plugin gets its own `CoroutineScope` with `SupervisorJob`
- **Injectable**: Scope and dispatchers are auto-registered as beans
- **Lifecycle-Managed**: Scopes cancelled automatically on context shutdown
- **Shared Utilities**: `withIO` and `withDefault` dispatcher helpers

### Configuration System

- **YAML Format**: Human-friendly config files with comment preservation
- **Annotation-based**: `@ConfigFile`, `@ConfigName`, `@Comment`, `@Transient`
- **Type-safe Loading**: Automatic type conversion for primitives, collections, nested objects
- **Hot Reload**: Reload configs at runtime without restarting
- **Centralized Management**: `ConfigManager` for loading, saving, and caching all configs

## Quick Start

### 1. Add Nexus to your project

```kotlin
dependencies {
    implementation("net.badgersmc:nexus:1.4.0")
}
```

### 2. Annotate your classes

```kotlin
@Repository
class PlayerRepository(private val storage: Storage) {
    suspend fun findPlayer(id: UUID): Player? = withIO {
        // database query
    }
}

@Service
class PlayerService(private val repository: PlayerRepository) {
    @PostConstruct
    fun init() {
        println("PlayerService initialized!")
    }

    suspend fun getPlayer(id: UUID): Player? {
        return repository.findPlayer(id)
    }
}
```

### 3. Create a Nexus context

```kotlin
// Nexus scans net.example.mymod and all sub-packages for annotated classes
val context = NexusContext.create(
    basePackage = "net.example.mymod",
    classLoader = this::class.java.classLoader,
    contextName = "MyPlugin"
)

// Register any beans created outside the container
context.registerBean("storage", Storage::class, storage)

// Retrieve auto-discovered beans
val playerService = context.getBean<PlayerService>()
val player = playerService.getPlayer(playerId)

// Cleanup when done
context.close()
```

That's it. Add a new `@Service` or `@Repository` class anywhere under your base package and it's automatically available for injection on next startup.

## Coroutine Infrastructure

Nexus provides centralized coroutine support backed by Java 21 virtual threads. When you pass a `classLoader` to `NexusContext.create()`, Nexus automatically creates a virtual thread executor, coroutine dispatcher, and plugin-scoped `CoroutineScope`.

### Why this matters

Java 21 virtual threads inherit the system classloader, not the plugin's. When a coroutine continuation tries to load a plugin class on a virtual thread, it fails. Nexus wraps every virtual thread task to propagate the correct classloader automatically.

### Launching coroutines

```kotlin
// Launch coroutines on virtual threads with correct classloader
context.scope!!.launch {
    val data = withIO { database.query("SELECT ...") }
    processData(data)
}
```

### Injecting the scope into components

The `CoroutineScope` and `NexusDispatchers` are registered as beans, so any component can receive them via constructor injection:

```kotlin
@Service
class MyService(private val scope: CoroutineScope) {
    fun doAsyncWork() {
        scope.launch {
            // runs on virtual threads with correct classloader
        }
    }
}
```

### Suspend lifecycle methods

`@PostConstruct` and `@PreDestroy` methods can be suspend functions:

```kotlin
@Service
class CacheService {
    @PostConstruct
    suspend fun warmUp() {
        // async initialization
    }

    @PreDestroy
    suspend fun flush() {
        // async cleanup
    }
}
```

### Shutdown lifecycle

When `context.close()` is called, Nexus shuts down in order:

1. Cancel the coroutine scope (stops all running coroutines)
2. Invoke `@PreDestroy` on all singletons
3. Shutdown the virtual thread executor
4. Clear the bean registry

## Configuration System

### Define a config class

```kotlin
@ConfigFile("mymod")
@Comment("My Mod Configuration")
class MyModConfig {
    @Comment("Enable debug mode")
    var debug: Boolean = false

    @ConfigName("max-players")
    @Comment("Maximum players allowed")
    var maxPlayers: Int = 100

    @Comment("Database settings")
    var database: DatabaseSettings = DatabaseSettings()

    class DatabaseSettings {
        var host: String = "localhost"
        var port: Int = 3306
    }
}
```

### Load and use

```kotlin
val configManager = ConfigManager(dataDirectory)

// Load config (creates mymod.yaml with defaults if missing)
val config = configManager.load<MyModConfig>()

// Modify and save
config.debug = true
configManager.save(config)

// Reload from disk
configManager.reload<MyModConfig>()
```

### Generated YAML

```yaml
# My Mod Configuration

# Enable debug mode
debug: false

# Maximum players allowed
max-players: 100

# Database settings
database:
  host: "localhost"
  port: 3306
```

## Annotations Reference

### Component Discovery

| Annotation | Target | Description |
|---|---|---|
| `@Component` | Class | Generic managed component |
| `@Service` | Class | Service layer component |
| `@Repository` | Class | Data access layer component |

### Dependency Injection

| Annotation | Target | Description |
|---|---|---|
| `@Inject` | Constructor, field, param | Mark injection points (optional for constructors) |
| `@Qualifier("name")` | Parameter | Disambiguate between multiple beans of same type |

### Lifecycle

| Annotation | Target | Description |
|---|---|---|
| `@PostConstruct` | Function | Called after dependency injection (supports suspend) |
| `@PreDestroy` | Function | Called before container shutdown (supports suspend) |
| `@Scope(ScopeType)` | Class | SINGLETON (default) or PROTOTYPE |

### Configuration

| Annotation | Target | Description |
|---|---|---|
| `@ConfigFile("name")` | Class | Maps class to `name.yaml` |
| `@ConfigName("key")` | Property | Custom YAML key name |
| `@Comment("text")` | Class, property | YAML comment above the field |
| `@Transient` | Property | Excluded from save/load |

## Advanced Usage

### Manual bean registration

For beans created outside the container (database connections, plugin instances, configs):

```kotlin
val context = NexusContext.create(
    basePackage = "net.example.mymod",
    classLoader = this::class.java.classLoader
)

// These are available for injection into scanned components
context.registerBean("storage", Storage::class, storage)
context.registerBean("config", MyConfig::class, config)
```

Bean factories use lazy resolution, so manually registered beans are available even when registered after `create()`.

### Manual-only mode

If you don't need classpath scanning:

```kotlin
val context = NexusContext.create()
context.registerBean("myBean", MyBean::class, MyBean())
```

### Shadow JAR relocation

When shading Nexus into your plugin, relocate ClassGraph as well:

```kotlin
tasks.shadowJar {
    relocate("net.badgersmc.nexus", "com.example.mymod.shaded.nexus")
    relocate("io.github.classgraph", "com.example.mymod.shaded.classgraph")
    relocate("nonapi.io.github.classgraph", "com.example.mymod.shaded.nonapi.classgraph")
}
```

## Architecture

```
nexus/
├── core/
│   ├── NexusContext          Main container — creates context, manages lifecycle
│   ├── ComponentRegistry     Bean definitions + singleton cache, polymorphic type indexing
│   ├── BeanFactory           Constructor injection, PostConstruct/PreDestroy invocation
│   └── BeanDefinition        Bean metadata (name, type, scope, factory)
├── scanning/
│   └── ComponentScanner      ClassGraph-based classpath scanning
├── annotations/
│   ├── @Component, @Service, @Repository
│   ├── @Inject, @Qualifier, @Scope
│   └── @PostConstruct, @PreDestroy
├── coroutines/
│   ├── NexusDispatchers      Virtual thread executor + classloader propagation
│   ├── NexusScope            Per-plugin CoroutineScope with SupervisorJob
│   └── CoroutineExtensions   withIO, withDefault helpers
└── config/
    ├── ConfigManager          Centralized config loading, saving, caching
    ├── ConfigLoader           YAML serialization with reflection
    └── @ConfigFile, @ConfigName, @Comment, @Transient
```

## Requirements

- Java 21+ (for virtual threads)
- Kotlin 2.0+

## License

MIT License - See LICENSE file for details
