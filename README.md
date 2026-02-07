# Nexus - Dependency Injection & Configuration for Hytale

**Nexus** is a lightweight, Kotlin-first framework for Hytale mods providing dependency injection, configuration management, and coroutine infrastructure. Inspired by Spring Framework, Nexus offers familiar annotations and patterns while remaining simple and performant.

## Features

### Dependency Injection
- **Annotation-based DI**: Use `@Component`, `@Service`, `@Repository` to mark managed beans
- **Constructor Injection**: Automatic dependency resolution through primary constructors
- **Lifecycle Management**: `@PostConstruct` and `@PreDestroy` hooks (supports suspend functions)
- **Scopes**: Singleton (default) and Prototype scopes
- **Polymorphic Resolution**: Beans can be resolved by their interface or superclass type
- **Type-safe**: Leverages Kotlin's type system for compile-time safety
- **Thread-safe**: Built with concurrent access in mind

### Coroutine Infrastructure
- **Virtual Thread Dispatchers**: Java 21 virtual threads with automatic classloader propagation
- **Per-Plugin Scopes**: Each plugin gets its own `CoroutineScope` with `SupervisorJob`
- **Injectable**: Scope and dispatchers are auto-registered as beans for DI
- **Lifecycle-Managed**: Scopes are automatically cancelled on context shutdown
- **Shared Utilities**: `withIO` and `withDefault` dispatcher helpers

### Configuration System
- **YAML Format**: Human-friendly config files with comments
- **Annotation-based**: `@ConfigFile`, `@ConfigName`, `@Comment` annotations
- **Type-safe Loading**: Automatic type conversion and validation
- **Nested Objects**: Full support for hierarchical configurations
- **Hot Reload**: Reload configs without restarting
- **Reflection-based**: Automatic field mapping

## Quick Start

### 1. Add Nexus to your project

```kotlin
dependencies {
    implementation("net.badgersmc:nexus:1.2.0")
}
```

### 2. Annotate your classes

```kotlin
@Repository
class PlayerRepository(private val storage: Storage) {
    fun findPlayer(id: UUID): Player? {
        // ...
    }
}

@Service
class PlayerService(private val repository: PlayerRepository) {
    @PostConstruct
    fun init() {
        println("PlayerService initialized!")
    }

    fun getPlayer(id: UUID): Player? {
        return repository.findPlayer(id)
    }
}
```

### 3. Create a Nexus context

```kotlin
val context = NexusContext.create(
    basePackage = "com.example.mymod",
    classes = listOf(PlayerService::class, PlayerRepository::class),
    classLoader = this::class.java.classLoader,
    contextName = "MyPlugin"
)

val playerService = context.getBean<PlayerService>()

// Use your service
val player = playerService.getPlayer(playerId)

// Cleanup when done
context.close()
```

## Coroutine Infrastructure

Nexus provides centralized coroutine support backed by Java 21 virtual threads. When you pass a `classLoader` to `NexusContext.create()`, Nexus automatically creates a virtual thread executor, coroutine dispatcher, and plugin-scoped `CoroutineScope`.

### Why this matters

Java 21 virtual threads inherit the system classloader, not the plugin's. When a coroutine continuation tries to load a plugin class on a virtual thread, it fails. Nexus wraps every virtual thread task to propagate the correct classloader automatically.

### Launching coroutines

```kotlin
val context = NexusContext.create(
    basePackage = "com.example.mymod",
    classes = components,
    classLoader = this::class.java.classLoader,
    contextName = "MyPlugin"
)

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
            // runs on virtual threads
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

### No classloader? No problem

If you don't pass a `classLoader`, Nexus works exactly as before — `scope` and `dispatchers` will be `null`, and no coroutine infrastructure is created. Fully backward compatible.

## Annotations

### Component Annotations
- `@Component` - Generic managed component
- `@Service` - Service layer component
- `@Repository` - Data access layer component

### Dependency Injection
- `@Inject` - Mark injection points (optional for constructors)
- `@Qualifier("name")` - Disambiguate between multiple beans

### Lifecycle
- `@PostConstruct` - Called after dependency injection
- `@PreDestroy` - Called before container shutdown

### Scoping
- `@Scope(ScopeType.SINGLETON)` - One instance per container (default)
- `@Scope(ScopeType.PROTOTYPE)` - New instance per injection

## Advanced Usage

### Manual Bean Registration

```kotlin
val context = NexusContext.create()
val myBean = MyCustomBean()
context.registerBean("myBean", MyCustomBean::class, myBean)
```

### Qualified Dependencies

```kotlin
@Service
class DataService(
    @Qualifier("primaryDatabase") val primary: Database,
    @Qualifier("cacheDatabase") val cache: Database
)
```

## Configuration System Usage

### Define Config Class

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

### Load and Use

```kotlin
// Initialize config manager
val configManager = ConfigManager(Paths.get("config"))

// Load config (creates mymod.yaml if missing)
val config = configManager.load<MyModConfig>()

// Use values
println("Debug: ${config.debug}")
println("Max players: ${config.maxPlayers}")

// Modify and save
config.debug = true
configManager.save(config)

// Reload from disk
configManager.reload<MyModConfig>()
```

### Generated YAML File

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

## Architecture

Nexus consists of three core systems:

**Dependency Injection:**

1. **NexusContext** - Main container managing the lifecycle
2. **ComponentRegistry** - Stores bean definitions and instances (with polymorphic type indexing)
3. **BeanFactory** - Creates and injects dependencies
4. **ComponentScanner** - Discovers annotated classes

**Coroutines:**

5. **NexusDispatchers** - Virtual thread executor with classloader propagation
6. **NexusScope** - Per-plugin CoroutineScope with SupervisorJob

**Configuration:**

7. **ConfigManager** - Centralized config management
8. **ConfigLoader** - Loads/saves YAML files with reflection
9. **Config Annotations** - @ConfigFile, @ConfigName, @Comment, @Transient

## Why Nexus?

Nexus connects your components together - like a nexus point in a network. It's lightweight, Kotlin-native, and designed specifically for Hytale's plugin ecosystem.

## License

MIT License - See LICENSE file for details
