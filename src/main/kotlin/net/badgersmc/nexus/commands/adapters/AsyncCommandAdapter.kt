package net.badgersmc.nexus.commands.adapters

import com.hypixel.hytale.server.core.Message
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractAsyncCommand
import com.hypixel.hytale.server.core.command.system.CommandContext
import com.hypixel.hytale.server.core.permissions.HytalePermissions
import kotlinx.coroutines.runBlocking
import net.badgersmc.nexus.commands.CommandDefinition
import net.badgersmc.nexus.commands.CommandException
import net.badgersmc.nexus.commands.arguments.ArgumentResolvers
import org.slf4j.LoggerFactory
import java.util.concurrent.CompletableFuture
import javax.annotation.Nonnull
import kotlin.reflect.full.callSuspend

/**
 * Adapter for async commands (AbstractAsyncCommand).
 *
 * Async commands run on a background thread and have no access to world/entity state.
 * They're suitable for commands that don't need world data (e.g., rules, help).
 *
 * This adapter:
 * 1. Creates Hytale arguments in the constructor
 * 2. Extracts argument values from CommandContext in executeAsync()
 * 3. Invokes the user's execute() method with proper parameters
 * 4. Handles suspend functions via runBlocking
 */
class AsyncCommandAdapter(
    private val definition: CommandDefinition,
    private val commandBean: Any
) : AbstractAsyncCommand(
    definition.annotation.name,
    definition.annotation.description
) {

    private val logger = LoggerFactory.getLogger(AsyncCommandAdapter::class.java)
    private val arguments = mutableListOf<Any>() // Hytale Argument instances

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

        logger.debug("Created AsyncCommandAdapter for command '{}' with {} arguments",
            definition.annotation.name, arguments.size)
    }

    override fun executeAsync(@Nonnull context: CommandContext): CompletableFuture<Void> {
        return CompletableFuture.supplyAsync {
            try {
                val params = buildParameterArray(context)
                invokeExecuteMethod(params)
            } catch (e: Exception) {
                logger.error("Command '{}' execution failed", definition.annotation.name, e)
                context.sendMessage(Message.raw("§cCommand failed: ${e.message ?: "Unknown error"}"))
            }
            null
        }
    }

    /**
     * Build the parameter array for the execute() method.
     */
    private fun buildParameterArray(context: CommandContext): Array<Any?> {
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
                    // Inject context value
                    when (param.type.simpleName) {
                        "CommandContext" -> context
                        else -> throw CommandException(
                            "Async commands only support @Context CommandContext. " +
                            "Found: ${param.type.simpleName} in command '${definition.annotation.name}'"
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
