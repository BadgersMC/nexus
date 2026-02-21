package net.badgersmc.nexus.commands.adapters

import com.hypixel.hytale.server.core.Message
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand
import com.hypixel.hytale.server.core.command.system.CommandContext
import com.hypixel.hytale.server.core.universe.PlayerRef
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import com.hypixel.hytale.component.Ref
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.server.core.permissions.HytalePermissions
import com.hypixel.hytale.server.core.universe.world.World
import kotlinx.coroutines.runBlocking
import net.badgersmc.nexus.commands.CommandDefinition
import net.badgersmc.nexus.commands.CommandException
import net.badgersmc.nexus.commands.arguments.ArgumentResolvers
import org.slf4j.LoggerFactory
import javax.annotation.Nonnull
import kotlin.reflect.full.callSuspend

/**
 * Adapter for player commands (AbstractPlayerCommand).
 *
 * Player commands run on the world thread and have access to:
 * - CommandContext (sender, messaging)
 * - World (the world where command was executed)
 * - Store<EntityStore> (entity component store)
 * - PlayerRef (reference to the player who executed the command)
 * - Ref<EntityStore> (entity reference to the player)
 *
 * This adapter:
 * 1. Creates Hytale arguments in the constructor
 * 2. Extracts argument values from CommandContext in execute()
 * 3. Injects context parameters based on type
 * 4. Invokes the user's execute() method with proper parameters
 * 5. Handles suspend functions via runBlocking
 */
class PlayerCommandAdapter(
    private val definition: CommandDefinition,
    private val commandBean: Any
) : AbstractPlayerCommand(
    definition.annotation.name,
    definition.annotation.description
) {

    private val logger = LoggerFactory.getLogger(PlayerCommandAdapter::class.java)
    private val arguments = mutableListOf<Any>()

    init {
        // Create Hytale arguments for each @Arg parameter
        for (param in definition.parameters.filter { it.isArg }) {
            val resolver = ArgumentResolvers.get(param.type)
                ?: throw CommandException(
                    "No ArgumentResolver for type ${param.type.simpleName} in command '${definition.annotation.name}'"
                )

            val argAnnotation = param.argAnnotation!!
            val hytaleArg = when {
                argAnnotation.required && argAnnotation.defaultValue.isEmpty() ->
                    resolver.createRequiredArg(this, argAnnotation.name, argAnnotation.description)

                argAnnotation.defaultValue.isNotEmpty() ->
                    resolver.createDefaultArg(this, argAnnotation.name, argAnnotation.description, argAnnotation.defaultValue)

                else ->
                    resolver.createOptionalArg(this, argAnnotation.name, argAnnotation.description)
            }

            arguments.add(hytaleArg)
        }

        // Register permission if specified
        if (definition.annotation.permission.isNotEmpty()) {
            requirePermission(HytalePermissions.fromCommand(definition.annotation.permission))
        }

        // Register aliases
        if (definition.annotation.aliases.isNotEmpty()) {
            addAliases(*definition.annotation.aliases)
        }

        logger.debug("Created PlayerCommandAdapter for command '{}' with {} arguments",
            definition.annotation.name, arguments.size)
    }

    override fun execute(
        @Nonnull context: CommandContext,
        @Nonnull store: Store<EntityStore>,
        @Nonnull ref: Ref<EntityStore>,
        @Nonnull playerRef: PlayerRef,
        @Nonnull world: World
    ) {
        try {
            val params = buildParameterArray(context, store, ref, playerRef, world)
            invokeExecuteMethod(params)
        } catch (e: Exception) {
            logger.error("Command '{}' execution failed", definition.annotation.name, e)
            context.sendMessage(Message.raw("§cCommand failed: ${e.message ?: "Unknown error"}"))
        }
    }

    /**
     * Build the parameter array for the execute() method.
     */
    private fun buildParameterArray(
        context: CommandContext,
        store: Store<EntityStore>,
        ref: Ref<EntityStore>,
        playerRef: PlayerRef,
        world: World
    ): Array<Any?> {
        val params = mutableListOf<Any?>()
        var argIndex = 0

        for (param in definition.parameters) {
            params.add(when {
                param.isArg -> {
                    // Extract argument value from context
                    val arg = arguments[argIndex++]
                    val method = arg::class.java.getMethod("get", CommandContext::class.java)
                    method.invoke(arg, context)
                }
                param.isContext -> {
                    // Inject context value based on type
                    when (param.type.simpleName) {
                        "CommandContext" -> context
                        "World" -> world
                        "Store" -> store
                        "PlayerRef" -> playerRef
                        "Ref" -> ref
                        else -> throw CommandException(
                            "Unsupported @Context type '${param.type.simpleName}' in command '${definition.annotation.name}'. " +
                            "Supported: CommandContext, World, Store<EntityStore>, PlayerRef, Ref<EntityStore>"
                        )
                    }
                }
                else -> throw CommandException("Parameter has neither @Arg nor @Context")
            })
        }

        return params.toTypedArray()
    }

    /**
     * Invoke the user's execute() method (handles suspend functions).
     */
    private fun invokeExecuteMethod(params: Array<Any?>) {
        if (definition.executeMethod.isSuspend) {
            runBlocking {
                definition.executeMethod.callSuspend(commandBean, *params)
            }
        } else {
            definition.executeMethod.call(commandBean, *params)
        }
    }
}
