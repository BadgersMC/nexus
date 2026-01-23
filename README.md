# Nexus - Dependency Injection & Configuration for Hytale

**Nexus** is a lightweight, Kotlin-first framework for Hytale mods providing dependency injection and configuration management. Inspired by Spring Framework, Nexus offers familiar annotations and patterns while remaining simple and performant.

## Features

### Dependency Injection
- **Annotation-based DI**: Use `@Component`, `@Service`, `@Repository` to mark managed beans
- **Constructor Injection**: Automatic dependency resolution through primary constructors
- **Lifecycle Management**: `@PostConstruct` and `@PreDestroy` hooks
- **Scopes**: Singleton (default) and Prototype scopes
- **Type-safe**: Leverages Kotlin's type system for compile-time safety
- **Thread-safe**: Built with concurrent access in mind

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
    implementation("net.badgersmc:nexus:1.1.0")
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
fun main() {
    val context = NexusContext.create(
        basePackage = "com.example.mymod",
        classes = listOf(PlayerService::class, PlayerRepository::class)
    )

    val playerService = context.getBean<PlayerService>()

    // Use your service
    val player = playerService.getPlayer(playerId)

    // Cleanup when done
    context.close()
}
```

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

Nexus consists of seven core components:

**Dependency Injection:**

1. **NexusContext** - Main container managing the lifecycle
2. **ComponentRegistry** - Stores bean definitions and instances
3. **BeanFactory** - Creates and injects dependencies
4. **ComponentScanner** - Discovers annotated classes

**Configuration:**

5. **ConfigManager** - Centralized config management
6. **ConfigLoader** - Loads/saves YAML files with reflection
7. **Config Annotations** - @ConfigFile, @ConfigName, @Comment, @Transient

## Why Nexus?

Nexus connects your components together - like a nexus point in a network. It's lightweight, Kotlin-native, and designed specifically for Hytale's plugin ecosystem.

## License

MIT License - See LICENSE file for details

