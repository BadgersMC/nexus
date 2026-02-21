package net.badgersmc.nexus.commands

import com.hypixel.hytale.server.core.command.system.CommandRegistry as HytaleCommandRegistry
import net.badgersmc.nexus.commands.adapters.AsyncCommandAdapter
import net.badgersmc.nexus.commands.adapters.PlayerCommandAdapter
import net.badgersmc.nexus.commands.adapters.TargetEntityCommandAdapter
import net.badgersmc.nexus.commands.adapters.TargetPlayerCommandAdapter
import net.badgersmc.nexus.commands.annotations.CommandType
import net.badgersmc.nexus.core.BeanFactory
import org.slf4j.LoggerFactory

/**
 * Orchestrates command scanning, adapter creation, and registration with Hytale.
 *
 * This class bridges between Nexus's command definitions and Hytale's command system.
 * For each CommandDefinition:
 * 1. Create command bean via BeanFactory (resolves dependencies)
 * 2. Create appropriate adapter (Async, Player, TargetPlayer, TargetEntity)
 * 3. Register adapter with Hytale's CommandRegistry
 *
 * **Thread Safety:** This class is not thread-safe. Call registerAll() once during initialization.
 */
class CommandRegistry(
    private val hytaleRegistry: HytaleCommandRegistry,
    private val beanFactory: BeanFactory
) {

    private val logger = LoggerFactory.getLogger(CommandRegistry::class.java)

    /**
     * Register all command definitions with Hytale's command system.
     *
     * For each definition:
     * 1. Create command bean with dependency injection
     * 2. Create appropriate adapter
     * 3. Register adapter with Hytale
     *
     * @param definitions List of command definitions from CommandScanner
     * @throws CommandException if bean creation or adapter creation fails
     */
    fun registerAll(definitions: List<CommandDefinition>) {
        logger.info("Registering {} commands with Hytale", definitions.size)

        for (definition in definitions) {
            try {
                registerCommand(definition)
            } catch (e: Exception) {
                logger.error("Failed to register command '${definition.annotation.name}'", e)
                throw CommandException(
                    "Failed to register command '${definition.annotation.name}': ${e.message}",
                    e
                )
            }
        }

        logger.info("Successfully registered {} commands", definitions.size)
    }

    /**
     * Register a single command definition.
     */
    private fun registerCommand(definition: CommandDefinition) {
        // Create command bean with dependency injection
        val commandBean = beanFactory.getBean(definition.commandClass)

        // Create appropriate adapter based on command type
        val adapter = createAdapter(definition, commandBean)

        // Register with Hytale (adapters all extend AbstractCommand)
        hytaleRegistry.registerCommand(adapter as com.hypixel.hytale.server.core.command.system.AbstractCommand)

        logger.debug("Registered command '{}' of type {}",
            definition.annotation.name, definition.annotation.type)
    }

    /**
     * Create the appropriate adapter for the command type.
     */
    private fun createAdapter(definition: CommandDefinition, commandBean: Any): Any {
        return when (definition.annotation.type) {
            CommandType.ASYNC -> AsyncCommandAdapter(definition, commandBean)
            CommandType.PLAYER -> PlayerCommandAdapter(definition, commandBean)
            CommandType.TARGET_PLAYER -> TargetPlayerCommandAdapter(definition, commandBean)
            CommandType.TARGET_ENTITY -> TargetEntityCommandAdapter(definition, commandBean)
        }
    }
}
