package net.badgersmc.nexus.paper.commands

import io.github.classgraph.ClassGraph
import net.badgersmc.nexus.commands.CommandException
import net.badgersmc.nexus.commands.CommandParameter
import net.badgersmc.nexus.commands.annotations.Arg
import net.badgersmc.nexus.commands.annotations.Command
import net.badgersmc.nexus.commands.annotations.Context
import net.badgersmc.nexus.paper.commands.annotations.Async
import net.badgersmc.nexus.paper.commands.annotations.Permission
import net.badgersmc.nexus.paper.commands.annotations.PlayerOnly
import net.badgersmc.nexus.paper.commands.annotations.Subcommand
import net.badgersmc.nexus.paper.commands.annotations.Suggests
import net.badgersmc.nexus.paper.commands.arguments.PaperArgumentResolvers
import org.slf4j.LoggerFactory
import kotlin.reflect.KClass
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.functions
import kotlin.reflect.full.hasAnnotation

class PaperCommandScanner {
    private val logger = LoggerFactory.getLogger(PaperCommandScanner::class.java)

    fun scanCommands(basePackage: String, classLoader: ClassLoader): List<PaperCommandDefinition> {
        val commandClasses: List<KClass<*>> = ClassGraph()
            .acceptPackages(basePackage)
            .addClassLoader(classLoader)
            .enableAnnotationInfo()
            .scan()
            .use { result ->
                result.getClassesWithAnnotation(Command::class.java.name)
                    .filter { !it.isAbstract && !it.isInterface }
                    .mapNotNull { classInfo -> classInfo.loadClass().kotlin }
            }

        return commandClasses.map { processClass(it) }.also { list ->
            logger.info("Discovered {} Paper commands in package '{}'", list.size, basePackage)
        }
    }

    private fun processClass(klass: KClass<*>): PaperCommandDefinition {
        val annotation = klass.findAnnotation<Command>()
            ?: throw CommandException("Class ${klass.simpleName} missing @Command")

        val subcommands = klass.functions
            .filter { it.hasAnnotation<Subcommand>() }
            .map { method ->
                val subAnnotation = method.findAnnotation<Subcommand>()!!
                val path = subAnnotation.value.trim().split(" ").filter { it.isNotEmpty() }
                val permission = method.findAnnotation<Permission>()?.value
                    ?: annotation.permission.ifEmpty { null }
                val isPlayerOnly = method.hasAnnotation<PlayerOnly>()
                val isAsync = method.hasAnnotation<Async>() || method.isSuspend

                val suggestionsMap = mutableMapOf<String, String>()
                val parameters = method.parameters.drop(1) // skip 'this'
                    .mapIndexed { idx, param ->
                        buildParameter(klass, method, param, idx, suggestionsMap)
                    }

                PaperSubcommandDefinition(path, method, parameters, permission, isPlayerOnly, isAsync, suggestionsMap)
            }

        if (subcommands.isEmpty()) throw CommandException(
            "Command class ${klass.simpleName} has no @Subcommand methods"
        )

        return PaperCommandDefinition(klass, annotation, subcommands)
    }

    private fun buildParameter(
        klass: KClass<*>,
        method: kotlin.reflect.KFunction<*>,
        param: kotlin.reflect.KParameter,
        idx: Int,
        suggestionsMap: MutableMap<String, String>
    ): CommandParameter {
        val arg = param.findAnnotation<Arg>()
        val ctx = param.findAnnotation<Context>()
        val suggests = param.findAnnotation<Suggests>()
        if (arg == null && ctx == null) throw CommandException(
            "Method ${method.name} parameter '${param.name}' needs @Arg or @Context"
        )
        if (arg != null && !PaperArgumentResolvers.hasResolver(param.type.classifier as KClass<*>)) {
            throw CommandException(
                "No PaperArgumentResolver for type '${(param.type.classifier as KClass<*>).simpleName}' " +
                    "in ${klass.simpleName}::${method.name}"
            )
        }
        if (suggests != null && arg != null) {
            suggestionsMap[param.name ?: "param$idx"] = suggests.value
        }
        return CommandParameter(
            name = param.name ?: "param$idx",
            type = param.type.classifier as KClass<*>,
            index = idx,
            argAnnotation = arg,
            contextAnnotation = ctx
        )
    }
}
