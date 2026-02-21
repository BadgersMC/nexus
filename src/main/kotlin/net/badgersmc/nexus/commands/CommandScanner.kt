package net.badgersmc.nexus.commands

import io.github.classgraph.ClassGraph
import net.badgersmc.nexus.commands.annotations.Arg
import net.badgersmc.nexus.commands.annotations.Command
import net.badgersmc.nexus.commands.annotations.CommandType
import net.badgersmc.nexus.commands.annotations.Context
import net.badgersmc.nexus.commands.arguments.ArgumentResolvers
import org.slf4j.LoggerFactory
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.functions
import kotlin.reflect.full.hasAnnotation

/**
 * Scans the classpath for classes annotated with @Command.
 *
 * Uses ClassGraph for reliable classpath scanning across custom classloaders.
 * Validates command structure and fails fast on errors.
 *
 * Validation rules:
 * 1. Command class must have exactly one method named 'execute'
 * 2. All @Arg parameters must have registered ArgumentResolver
 * 3. Required arguments must come before optional arguments
 * 4. @Context parameters must be valid injectable types
 * 5. Command names must be unique across all discovered commands
 */
class CommandScanner {

    private val logger = LoggerFactory.getLogger(CommandScanner::class.java)

    /**
     * Scan the classpath for @Command classes.
     *
     * @param basePackage Base package to scan (e.g., "net.badgersmc.hycore")
     * @param classLoader The classloader to scan (typically the plugin's classloader)
     * @return List of CommandDefinition for each valid command
     * @throws CommandException if validation fails
     */
    fun scanCommands(basePackage: String, classLoader: ClassLoader): List<CommandDefinition> {
        logger.debug("Scanning classpath for @Command classes in package: {}", basePackage)

        val commandClasses = discoverCommandClasses(basePackage, classLoader)
        val definitions = commandClasses.mapNotNull { klass ->
            try {
                processCommandClass(klass)
            } catch (e: CommandException) {
                logger.error("Failed to process command class: ${klass.simpleName}", e)
                throw e
            } catch (e: Exception) {
                logger.warn("Failed to process command class: ${klass.simpleName}", e)
                null
            }
        }

        // Validate unique command names
        validateUniqueCommandNames(definitions)

        logger.info("Classpath scan found {} valid commands in package '{}'", definitions.size, basePackage)
        return definitions
    }

    /**
     * Discover all classes annotated with @Command in the given package.
     */
    private fun discoverCommandClasses(basePackage: String, classLoader: ClassLoader): List<KClass<*>> {
        return ClassGraph()
            .acceptPackages(basePackage)
            .addClassLoader(classLoader)
            .enableAnnotationInfo()
            .scan()
            .use { scanResult ->
                scanResult.getClassesWithAnnotation(Command::class.java.name)
                    .filter { !it.isAbstract && !it.isInterface }
                    .mapNotNull { classInfo ->
                        try {
                            classInfo.loadClass().kotlin
                        } catch (e: Exception) {
                            logger.warn("Failed to load @Command class: ${classInfo.name}", e)
                            null
                        }
                    }
            }
    }

    /**
     * Process a single command class and create its CommandDefinition.
     */
    private fun processCommandClass(klass: KClass<*>): CommandDefinition {
        val annotation = klass.findAnnotation<Command>()
            ?: throw CommandException("Class ${klass.simpleName} is missing @Command annotation")

        // Find the execute method
        val executeMethod = findExecuteMethod(klass, annotation.name)

        // Extract parameter metadata
        val parameters = extractParameters(executeMethod, klass.simpleName ?: "Unknown", annotation.name)

        // Validate argument order
        validateArgumentOrder(parameters, annotation.name)

        // Validate all @Arg types have resolvers
        validateArgumentResolvers(parameters, annotation.name)

        // Validate @Context types
        validateContextTypes(parameters, annotation.name, annotation.type)

        return CommandDefinition(
            commandClass = klass,
            annotation = annotation,
            executeMethod = executeMethod,
            parameters = parameters
        )
    }

    /**
     * Find the execute() method in the command class.
     */
    private fun findExecuteMethod(klass: KClass<*>, commandName: String): KFunction<*> {
        val executeMethods = klass.functions.filter { it.name == "execute" }

        return when (executeMethods.size) {
            0 -> throw CommandException(
                "Command '$commandName' in ${klass.simpleName} must have exactly one method named 'execute'"
            )
            1 -> executeMethods.first()
            else -> throw CommandException(
                "Command '$commandName' in ${klass.simpleName} has multiple methods named 'execute'. " +
                "Only one execute method is allowed."
            )
        }
    }

    /**
     * Extract parameter metadata from the execute method.
     */
    private fun extractParameters(
        executeMethod: KFunction<*>,
        className: String,
        commandName: String
    ): List<CommandParameter> {
        return executeMethod.parameters
            .drop(1) // Skip 'this' parameter
            .mapIndexed { index, param ->
                val argAnnotation = param.findAnnotation<Arg>()
                val contextAnnotation = param.findAnnotation<Context>()

                // Validate exactly one annotation
                when {
                    argAnnotation == null && contextAnnotation == null -> {
                        throw CommandException(
                            "Command '$commandName' parameter '${param.name}' must have either @Arg or @Context annotation"
                        )
                    }
                    argAnnotation != null && contextAnnotation != null -> {
                        throw CommandException(
                            "Command '$commandName' parameter '${param.name}' has both @Arg and @Context annotations. " +
                            "Only one is allowed."
                        )
                    }
                }

                val paramType = param.type.classifier as? KClass<*>
                    ?: throw CommandException(
                        "Command '$commandName' parameter '${param.name}' has unresolvable type"
                    )

                CommandParameter(
                    name = param.name ?: "param$index",
                    type = paramType,
                    index = index,
                    argAnnotation = argAnnotation,
                    contextAnnotation = contextAnnotation
                )
            }
    }

    /**
     * Validate that required arguments come before optional arguments.
     */
    private fun validateArgumentOrder(parameters: List<CommandParameter>, commandName: String) {
        var foundOptional = false

        for (param in parameters.filter { it.isArg }) {
            if (param.isOptional) {
                foundOptional = true
            } else if (foundOptional) {
                throw CommandException(
                    "Command '$commandName' has required argument '${param.name}' after optional argument. " +
                    "Required arguments must come before optional arguments."
                )
            }
        }
    }

    /**
     * Validate that all @Arg parameter types have registered ArgumentResolvers.
     */
    private fun validateArgumentResolvers(parameters: List<CommandParameter>, commandName: String) {
        for (param in parameters.filter { it.isArg }) {
            if (!ArgumentResolvers.hasResolver(param.type)) {
                throw CommandException(
                    "Command '$commandName' has no ArgumentResolver for parameter '${param.name}' of type '${param.type.simpleName}'. " +
                    "Register a resolver via ArgumentResolvers.register() before creating the context."
                )
            }
        }
    }

    /**
     * Validate that @Context parameter types are supported for the command type.
     *
     * Allowed types per command type (matched by simple class name):
     * - ASYNC:         CommandContext
     * - PLAYER:        CommandContext, World, Store, PlayerRef, Ref
     * - TARGET_PLAYER: CommandContext, World, Store, PlayerRef, Ref
     * - TARGET_ENTITY: CommandContext, World, Store, ObjectList
     */
    private fun validateContextTypes(
        parameters: List<CommandParameter>,
        commandName: String,
        commandType: CommandType
    ) {
        val allowed = when (commandType) {
            CommandType.ASYNC -> setOf("CommandContext")
            CommandType.PLAYER -> setOf("CommandContext", "World", "Store", "PlayerRef", "Ref")
            CommandType.TARGET_PLAYER -> setOf("CommandContext", "World", "Store", "PlayerRef", "Ref")
            CommandType.TARGET_ENTITY -> setOf("CommandContext", "World", "Store", "ObjectList")
        }

        for (param in parameters.filter { it.isContext }) {
            val typeName = param.type.simpleName ?: "Unknown"
            if (typeName !in allowed) {
                throw CommandException(
                    "Command '$commandName' (${commandType.name}) has unsupported @Context type '$typeName' " +
                    "for parameter '${param.name}'. Allowed types: ${allowed.joinToString()}"
                )
            }
        }
    }

    /**
     * Validate that command names are unique.
     */
    private fun validateUniqueCommandNames(definitions: List<CommandDefinition>) {
        val nameToClass = mutableMapOf<String, String>()

        for (definition in definitions) {
            val name = definition.annotation.name
            val className = definition.commandClass.simpleName ?: "Unknown"

            val existing = nameToClass.putIfAbsent(name, className)
            if (existing != null) {
                throw CommandException(
                    "Duplicate command name '$name' found in classes $existing and $className. " +
                    "Command names must be unique."
                )
            }
        }
    }
}
