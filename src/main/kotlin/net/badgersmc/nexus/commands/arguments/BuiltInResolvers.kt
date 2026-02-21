package net.badgersmc.nexus.commands.arguments

import com.hypixel.hytale.server.core.command.system.AbstractCommand
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes
import kotlin.reflect.KClass

/**
 * Built-in argument resolvers for primitive types.
 * These are automatically registered when ArgumentResolvers is initialized.
 */

object StringArgumentResolver : ArgumentResolver<String> {
    override val type: KClass<String> = String::class

    override fun createRequiredArg(command: Any, name: String, description: String): Any {
        return (command as AbstractCommand).withRequiredArg(name, description, ArgTypes.STRING)
    }

    override fun createOptionalArg(command: Any, name: String, description: String): Any {
        return (command as AbstractCommand).withOptionalArg(name, description, ArgTypes.STRING)
    }

    override fun createDefaultArg(command: Any, name: String, description: String, defaultValue: String): Any {
        return (command as AbstractCommand).withDefaultArg(name, description, ArgTypes.STRING, defaultValue, "Default: $defaultValue")
    }
}

object IntArgumentResolver : ArgumentResolver<Int> {
    override val type: KClass<Int> = Int::class

    override fun createRequiredArg(command: Any, name: String, description: String): Any {
        return (command as AbstractCommand).withRequiredArg(name, description, ArgTypes.INTEGER)
    }

    override fun createOptionalArg(command: Any, name: String, description: String): Any {
        return (command as AbstractCommand).withOptionalArg(name, description, ArgTypes.INTEGER)
    }

    override fun createDefaultArg(command: Any, name: String, description: String, defaultValue: String): Any {
        val intValue = defaultValue.toIntOrNull()
            ?: throw IllegalArgumentException("Default value '$defaultValue' is not a valid integer")
        return (command as AbstractCommand).withDefaultArg(name, description, ArgTypes.INTEGER, intValue, "Default: $defaultValue")
    }
}

object DoubleArgumentResolver : ArgumentResolver<Double> {
    override val type: KClass<Double> = Double::class

    override fun createRequiredArg(command: Any, name: String, description: String): Any {
        return (command as AbstractCommand).withRequiredArg(name, description, ArgTypes.DOUBLE)
    }

    override fun createOptionalArg(command: Any, name: String, description: String): Any {
        return (command as AbstractCommand).withOptionalArg(name, description, ArgTypes.DOUBLE)
    }

    override fun createDefaultArg(command: Any, name: String, description: String, defaultValue: String): Any {
        val doubleValue = defaultValue.toDoubleOrNull()
            ?: throw IllegalArgumentException("Default value '$defaultValue' is not a valid double")
        return (command as AbstractCommand).withDefaultArg(name, description, ArgTypes.DOUBLE, doubleValue, "Default: $defaultValue")
    }
}

object FloatArgumentResolver : ArgumentResolver<Float> {
    override val type: KClass<Float> = Float::class

    override fun createRequiredArg(command: Any, name: String, description: String): Any {
        return (command as AbstractCommand).withRequiredArg(name, description, ArgTypes.FLOAT)
    }

    override fun createOptionalArg(command: Any, name: String, description: String): Any {
        return (command as AbstractCommand).withOptionalArg(name, description, ArgTypes.FLOAT)
    }

    override fun createDefaultArg(command: Any, name: String, description: String, defaultValue: String): Any {
        val floatValue = defaultValue.toFloatOrNull()
            ?: throw IllegalArgumentException("Default value '$defaultValue' is not a valid float")
        return (command as AbstractCommand).withDefaultArg(name, description, ArgTypes.FLOAT, floatValue, "Default: $defaultValue")
    }
}

object BooleanArgumentResolver : ArgumentResolver<Boolean> {
    override val type: KClass<Boolean> = Boolean::class

    override fun createRequiredArg(command: Any, name: String, description: String): Any {
        return (command as AbstractCommand).withRequiredArg(name, description, ArgTypes.BOOLEAN)
    }

    override fun createOptionalArg(command: Any, name: String, description: String): Any {
        return (command as AbstractCommand).withOptionalArg(name, description, ArgTypes.BOOLEAN)
    }

    override fun createDefaultArg(command: Any, name: String, description: String, defaultValue: String): Any {
        val boolValue = defaultValue.toBooleanStrictOrNull()
            ?: throw IllegalArgumentException("Default value '$defaultValue' is not a valid boolean")
        return (command as AbstractCommand).withDefaultArg(name, description, ArgTypes.BOOLEAN, boolValue, "Default: $defaultValue")
    }
}

/**
 * Register all built-in resolvers.
 * Called automatically when ArgumentResolvers is initialized.
 */
internal fun registerBuiltInResolvers() {
    ArgumentResolvers.register(String::class, StringArgumentResolver)
    ArgumentResolvers.register(Int::class, IntArgumentResolver)
    ArgumentResolvers.register(Double::class, DoubleArgumentResolver)
    ArgumentResolvers.register(Float::class, FloatArgumentResolver)
    ArgumentResolvers.register(Boolean::class, BooleanArgumentResolver)
}
