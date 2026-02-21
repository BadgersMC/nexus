# Nexus - Application Framework for Hytale

**Nexus** is a Kotlin-first application framework for Hytale mods providing automatic dependency injection with classpath scanning, YAML configuration management, command auto-discovery, and coroutine infrastructure backed by Java 21 virtual threads.

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

- **Auto-Discovery**: `@ConfigFile` classes found by classpath scanning, loaded, and registered as injectable beans
- **YAML Format**: Human-friendly config files with comment preservation
- **Annotation-based**: `@ConfigFile`, `@ConfigName`, `@Comment`, `@Transient`
- **Type-safe Loading**: Automatic type conversion for primitives, collections, nested objects
- **Hot Reload**: Reload configs at runtime without restarting
- **Centralized Management**: `ConfigManager` for loading, saving, and caching all configs

### Command Auto-Discovery (Beta)

- **Annotation-based Commands**: `@Command` classes with `@Arg` parameters auto-discovered
- **Type-safe Arguments**: Map Kotlin types to Hytale arguments via `ArgumentResolver`
- **Dependency Injection**: Commands can inject services, configs, and other beans
- **Fail-fast Validation**: Missing argument resolvers detected at startup, not runtime
- **Suspend Support**: Command execute methods can be suspend functions
- **Context Injection**: `@Context` parameters for CommandContext, World, Store, etc.
- **Status**: AsyncCommand ✅ | PlayerCommand ✅ | TargetPlayerCommand ✅ | TargetEntityCommand ✅

## Quick Start

### 1. Add Nexus to your project

```kotlin
dependencies {
    implementation("net.badgersmc:nexus:1.5.0")
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
// Nexus scans net.example.mymod and all sub-packages for annotated classes.
// Passing configDirectory enables automatic @ConfigFile discovery and loading.
// Passing commandRegistry enables automatic @Command discovery and registration.
val context = NexusContext.create(
    basePackage = "net.example.mymod",
    classLoader = this::class.java.classLoader,
    configDirectory = dataDirectory,
    commandRegistry = this.commandRegistry,  // Hytale's CommandRegistry
    contextName = "MyPlugin"
)

// Register any beans created outside the container
context.registerBean("storage", Storage::class, storage)

// Retrieve auto-discovered beans — configs and commands are injectable too
val playerService = context.getBean<PlayerService>()
val config = context.getBean<MyModConfig>()

// Cleanup when done
context.close()
```

That's it. Add a new `@Service`, `@Repository`, or `@ConfigFile` class anywhere under your base package and it's automatically available for injection on next startup.

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

## Command System (Beta)

Nexus automatically discovers `@Command` annotated classes and registers them with Hytale's command system. Commands support dependency injection, type-safe arguments, and suspend functions.

**Current Status:**
- ✅ **AsyncCommand** - Background tasks (no world/entity access)
- ✅ **PlayerCommand** - World-thread commands with full ECS access
- ✅ **TargetPlayerCommand** - Adds optional `--player` arg, runs on world thread
- ✅ **TargetEntityCommand** - Raycasts to find entities in view, runs on world thread

### Define a command

```kotlin
@Command(
    name = "heal",
    description = "Heal a player",
    permission = "admin.heal",
    type = CommandType.PLAYER
)
class HealCommand(
    private val healthService: HealthService  // DI works!
) {
    fun execute(
        @Context context: CommandContext,
        @Context world: World,
        @Context store: Store<EntityStore>,
        @Context ref: Ref<EntityStore>,
        @Arg("amount", "Amount of health to restore", required = false, defaultValue = "20") amount: Int
    ) {
        healthService.heal(store, ref, amount)
        context.sendMessage(Message.raw("Healed for $amount HP"))
    }
}
```

### Automatic discovery

When you pass `commandRegistry` to `NexusContext.create()`, Nexus scans for all `@Command`-annotated classes, validates them, creates instances with dependency injection, and registers them with Hytale:

```kotlin
val context = NexusContext.create(
    basePackage = "net.example.mymod",
    classLoader = this::class.java.classLoader,
    commandRegistry = this.commandRegistry  // Hytale's CommandRegistry
)
```

### Async commands

Async commands run on a background thread and have no access to world/entity state. They're perfect for commands that don't need game data (help, rules, backups):

```kotlin
@Command(
    name = "backup",
    description = "Backup the server",
    permission = "admin.backup",
    type = CommandType.ASYNC
)
class BackupCommand(
    private val backupService: BackupService
) {
    suspend fun execute(
        @Context context: CommandContext,
        @Arg("world", "World to backup") worldName: String
    ) {
        context.sendMessage(Message.raw("Starting backup of $worldName..."))
        backupService.backupWorld(worldName)
        context.sendMessage(Message.raw("Backup complete!"))
    }
}
```

### Player commands

Player commands run on the world thread and have full access to the Entity Component System:

```kotlin
@Command(
    name = "settime",
    description = "Set the world time",
    permission = "admin.time",
    type = CommandType.PLAYER
)
class SetTimeCommand {
    fun execute(
        @Context context: CommandContext,
        @Context world: World,
        @Arg("time", "Time of day (0-24000)") time: Int
    ) {
        world.setTime(time)
        context.sendMessage(Message.raw("Time set to $time"))
    }
}
```

### Custom argument types

Register custom `ArgumentResolver` implementations before creating the context:

```kotlin
object PlayerArgumentResolver : ArgumentResolver<Player> {
    override val type = Player::class

    override fun createRequiredArg(command: Any, name: String, description: String): Any {
        return (command as AbstractCommand).withRequiredArg(name, description, ArgTypes.PLAYER)
    }

    override fun createOptionalArg(command: Any, name: String, description: String): Any {
        return (command as AbstractCommand).withOptionalArg(name, description, ArgTypes.PLAYER)
    }

    override fun createDefaultArg(command: Any, name: String, description: String, defaultValue: String): Any {
        return (command as AbstractCommand).withDefaultArg(name, description, defaultValue, ArgTypes.PLAYER)
    }
}

// Register before creating context
ArgumentResolvers.register(Player::class, PlayerArgumentResolver)

val context = NexusContext.create(
    basePackage = "net.example.mymod",
    classLoader = this::class.java.classLoader,
    commandRegistry = this.commandRegistry
)
```

### Supported @Context types

| Command Type | Supported Context Parameters |
|---|---|
| `CommandType.ASYNC` | `CommandContext` |
| `CommandType.PLAYER` | `CommandContext`, `World`, `Store<EntityStore>`, `PlayerRef`, `Ref<EntityStore>` |
| `CommandType.TARGET_PLAYER` | `CommandContext`, `World`, `Store<EntityStore>`, `PlayerRef`, `Ref<EntityStore>` (target player's ref) |
| `CommandType.TARGET_ENTITY` | `CommandContext`, `World`, `Store<EntityStore>`, `ObjectList<Ref<EntityStore>>` (entities in view) |

### Built-in argument types

Nexus provides resolvers for these Kotlin types out of the box:
- `String`
- `Int`
- `Double`
- `Float`
- `Boolean`

### Validation

Command scanning performs fail-fast validation at startup:
- ✅ Verifies `execute()` method exists
- ✅ Checks all `@Arg` types have registered `ArgumentResolver`
- ✅ Validates required arguments come before optional arguments
- ✅ Ensures `@Context` parameters are supported types for the command type
- ✅ Detects duplicate command names

If validation fails, `NexusContext.create()` throws `CommandException` with a detailed error message.

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

### Automatic discovery (recommended)

When you pass `configDirectory` to `NexusContext.create()`, Nexus scans for all `@ConfigFile`-annotated classes, loads them (creating YAML files with defaults if missing), and registers them as singleton beans. Services can then inject configs directly:

```kotlin
@Service
class GameService(private val config: MyModConfig) {
    fun getMaxPlayers() = config.maxPlayers
}
```

A `ConfigManager` bean is also registered automatically, so any service can inject it for runtime reloads:

```kotlin
@Service
class AdminService(private val configManager: ConfigManager) {
    fun reloadAll() = configManager.reloadAll()
}
```

### Manual usage

If you need config management without classpath scanning:

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

### Commands (Beta)

| Annotation | Target | Description |
|---|---|---|
| `@Command(...)` | Class | Marks a command class with metadata (name, description, permission, aliases, type) |
| `@Arg("name", ...)` | Parameter | Marks a parameter as a user-provided argument |
| `@Context` | Parameter | Marks a parameter for runtime injection (CommandContext, World, Store, etc.) |

## Advanced Usage

### Manual bean registration

For beans created outside the container (database connections, plugin instances):

```kotlin
val context = NexusContext.create(
    basePackage = "net.example.mymod",
    classLoader = this::class.java.classLoader,
    configDirectory = dataDirectory
)

// These are available for injection into scanned components
context.registerBean("storage", Storage::class, storage)
context.registerBean("plugin", MyPlugin::class, this)
```

Bean factories use lazy resolution, so manually registered beans are available even when registered after `create()`. Note that `@ConfigFile` classes no longer need manual registration — they're discovered and loaded automatically when `configDirectory` is provided.

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
├── config/
│   ├── ConfigManager          Centralized config loading, saving, caching
│   ├── ConfigLoader           YAML serialization with reflection
│   └── @ConfigFile, @ConfigName, @Comment, @Transient
└── commands/ (Beta)
    ├── CommandScanner         ClassGraph-based command discovery
    ├── CommandRegistry        Bridges to Hytale's CommandRegistry
    ├── CommandDefinition      Command metadata (annotation, execute method, parameters)
    ├── @Command, @Arg, @Context
    ├── adapters/
    │   ├── AsyncCommandAdapter        ✅ Working
    │   ├── PlayerCommandAdapter       ✅ Working
    │   ├── TargetPlayerCommandAdapter ✅ Working
    │   └── TargetEntityCommandAdapter ✅ Working
    └── arguments/
        ├── ArgumentResolver       Interface for type → Hytale arg mapping
        ├── ArgumentResolvers      Registry of resolvers
        └── BuiltInResolvers       String, Int, Double, Float, Boolean
```

## Requirements

- Java 21+ (for virtual threads)
- Kotlin 2.0+

## License

MIT License - See LICENSE file for details
