package net.badgersmc.nexus.commands.adapters

import com.hypixel.hytale.component.Ref
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.server.core.Message
import com.hypixel.hytale.server.core.command.system.CommandContext
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractTargetEntityCommand
import com.hypixel.hytale.server.core.permissions.HytalePermissions
import com.hypixel.hytale.server.core.universe.world.World
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import it.unimi.dsi.fastutil.objects.ObjectList
import kotlinx.coroutines.runBlocking
import net.badgersmc.nexus.commands.CommandDefinition
import net.badgersmc.nexus.commands.CommandException
import net.badgersmc.nexus.commands.arguments.ArgumentResolvers
import org.slf4j.LoggerFactory
import javax.annotation.Nonnull
import kotlin.reflect.full.callSuspend

/**
 * Adapter for target entity commands (AbstractTargetEntityCommand).
 *
 * Target entity commands use raycasting to find entities in the player's view and run
 * on the world thread. The execute() signature (from JAR bytecode inspection) is:
 *
 *   execute(context, entities, world, store)
 *
 * Where:
 * - entities — ObjectList<Ref<EntityStore>> of entities in view (from fastutil, bundled in Hytale JAR)
 * - world    — World the command was executed in
 * - store    — Store<EntityStore> for the world
 *
 * Supported @Context types: CommandContext, World, Store<EntityStore>, ObjectList
 */
class TargetEntityCommandAdapter(
    private val definition: CommandDefinition,
    private val commandBean: Any
) : AbstractTargetEntityCommand(
    definition.annotation.name,
    definition.annotation.description
) {

    private val logger = LoggerFactory.getLogger(TargetEntityCommandAdapter::class.java)
    private val arguments = mutableListOf<Any>()

    init {
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

        if (definition.annotation.permission.isNotEmpty()) {
            requirePermission(HytalePermissions.fromCommand(definition.annotation.permission))
        }

        if (definition.annotation.aliases.isNotEmpty()) {
            addAliases(*definition.annotation.aliases)
        }

        logger.debug("Created TargetEntityCommandAdapter for command '{}' with {} arguments",
            definition.annotation.name, arguments.size)
    }

    // Actual signature from JAR bytecode (NameAndType #256):
    //   execute(context, entities: ObjectList<Ref<EntityStore>>, world, store)
    override fun execute(
        @Nonnull context: CommandContext,
        @Nonnull entities: ObjectList<Ref<EntityStore>>,
        @Nonnull world: World,
        @Nonnull store: Store<EntityStore>
    ) {
        try {
            val params = buildParameterArray(context, entities, world, store)
            invokeExecuteMethod(params)
        } catch (e: Exception) {
            logger.error("Command '{}' execution failed", definition.annotation.name, e)
            context.sendMessage(Message.raw("§cCommand failed: ${e.message ?: "Unknown error"}"))
        }
    }

    private fun buildParameterArray(
        context: CommandContext,
        entities: ObjectList<Ref<EntityStore>>,
        world: World,
        store: Store<EntityStore>
    ): Array<Any?> {
        val params = mutableListOf<Any?>()
        var argIndex = 0

        for (param in definition.parameters) {
            params.add(when {
                param.isArg -> {
                    val arg = arguments[argIndex++]
                    val method = arg::class.java.getMethod("get", CommandContext::class.java)
                    method.invoke(arg, context)
                }
                param.isContext -> {
                    when (param.type.simpleName) {
                        "CommandContext" -> context
                        "World" -> world
                        "Store" -> store
                        "ObjectList" -> entities
                        else -> throw CommandException(
                            "Unsupported @Context type '${param.type.simpleName}' in TARGET_ENTITY command '${definition.annotation.name}'. " +
                            "Supported: CommandContext, World, Store<EntityStore>, ObjectList<Ref<EntityStore>>"
                        )
                    }
                }
                else -> throw CommandException("Parameter has neither @Arg nor @Context")
            })
        }

        return params.toTypedArray()
    }

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
