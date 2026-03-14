package net.badgersmc.nexus.paper.commands

import com.mojang.brigadier.Command
import com.mojang.brigadier.builder.ArgumentBuilder
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.tree.LiteralCommandNode
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import net.badgersmc.nexus.commands.CommandException
import net.badgersmc.nexus.core.BeanFactory
import net.badgersmc.nexus.paper.commands.arguments.PaperArgumentResolvers
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import org.slf4j.LoggerFactory
import kotlin.reflect.full.callSuspend

class PaperCommandRegistry(
    private val plugin: JavaPlugin,
    private val nexusScope: CoroutineScope,
    private val beanFactory: BeanFactory
) {
    private val logger = LoggerFactory.getLogger(PaperCommandRegistry::class.java)

    fun registerAll(definitions: List<PaperCommandDefinition>) {
        plugin.lifecycleManager.registerEventHandler(LifecycleEvents.COMMANDS) { event ->
            for (def in definitions) {
                try {
                    val bean = beanFactory.createFactory(def.commandClass).invoke()
                    val tree = buildCommandTree(def, bean)
                    event.registrar().register(
                        tree,
                        def.annotation.description,
                        def.annotation.aliases.toList()
                    )
                    logger.debug("Registered Paper command '/{}'", def.annotation.name)
                } catch (e: Exception) {
                    logger.error("Failed to register command '${def.annotation.name}'", e)
                    throw CommandException("Command registration failed: ${e.message}", e)
                }
            }
            logger.info("Registered {} Paper commands", definitions.size)
        }
    }

    private fun buildCommandTree(
        def: PaperCommandDefinition,
        bean: Any
    ): LiteralCommandNode<CommandSourceStack> {
        val root = Commands.literal(def.annotation.name)

        if (def.annotation.permission.isNotEmpty()) {
            root.requires { it.sender.hasPermission(def.annotation.permission) }
        }

        for (sub in def.subcommands) {
            attachSubcommand(root, sub, bean)
        }

        return root.build()
    }

    private fun attachSubcommand(
        root: LiteralArgumentBuilder<CommandSourceStack>,
        sub: PaperSubcommandDefinition,
        bean: Any
    ) {
        var current = Commands.literal(sub.path.first())
        for (segment in sub.path.drop(1)) {
            val next = Commands.literal(segment)
            current.then(next)
            current = next
        }

        sub.permission?.let { perm ->
            current.requires { it.sender.hasPermission(perm) }
        }

        if (sub.isPlayerOnly) {
            current.requires { it.sender is Player }
        }

        var argNode: ArgumentBuilder<CommandSourceStack, *> = current
        for (param in sub.parameters.filter { it.isArg }) {
            val resolver = PaperArgumentResolvers.get(param.type)
                ?: throw CommandException("No resolver for ${param.type.simpleName}")
            val argBuilder = Commands.argument(param.name, resolver.argumentType())
            argNode.then(argBuilder)
            argNode = argBuilder
        }

        argNode.executes { ctx ->
            executeSubcommand(ctx, sub, bean)
            Command.SINGLE_SUCCESS
        }

        root.then(current)
    }

    private fun executeSubcommand(
        ctx: CommandContext<CommandSourceStack>,
        sub: PaperSubcommandDefinition,
        bean: Any
    ) {
        val source = ctx.source
        val params = buildParams(ctx, sub, source)

        if (sub.isAsync) {
            nexusScope.launch {
                try {
                    if (sub.method.isSuspend) {
                        sub.method.callSuspend(bean, *params)
                    } else {
                        sub.method.call(bean, *params)
                    }
                } catch (e: Exception) {
                    logger.error("Async command '${sub.path}' failed", e)
                }
            }
        } else {
            try {
                sub.method.call(bean, *params)
            } catch (e: Exception) {
                logger.error("Command '${sub.path}' failed", e)
            }
        }
    }

    private fun buildParams(
        ctx: CommandContext<CommandSourceStack>,
        sub: PaperSubcommandDefinition,
        source: CommandSourceStack
    ): Array<Any?> = sub.parameters.map { param ->
        when {
            param.isArg -> {
                val resolver = PaperArgumentResolvers.get(param.type)!!
                resolver.extract(ctx, param.name)
            }
            param.isContext -> when (param.type.simpleName) {
                "CommandSourceStack" -> source
                "Player" -> source.sender as? Player
                    ?: throw CommandException("Command requires a player sender")
                "CommandSender" -> source.sender
                "Server" -> source.sender.server
                else -> throw CommandException(
                    "Unsupported @Context type '${param.type.simpleName}'"
                )
            }
            else -> throw CommandException("Parameter '${param.name}' has no annotation")
        }
    }.toTypedArray()
}
