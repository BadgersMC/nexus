# Hytale API Package Mappings

This file documents the correct package paths for Hytale's command API as of version 2026.02.11.

## Command Base Classes
- `com.hypixel.hytale.server.core.command.system.basecommands.AbstractAsyncCommand`
- `com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand`
- `com.hypixel.hytale.server.core.command.system.basecommands.AbstractTargetPlayerCommand`
- `com.hypixel.hytale.server.core.command.system.basecommands.AbstractTargetEntityCommand`
- `com.hypixel.hytale.server.core.command.system.basecommands.CommandBase`
- `com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection`

## Command System
- `com.hypixel.hytale.server.core.command.system.CommandContext`
- `com.hypixel.hytale.server.core.command.system.CommandRegistry`
- `com.hypixel.hytale.server.core.command.system.CommandSender`

## Arguments
- `com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes`
- `com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg`
- `com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg`
- `com.hypixel.hytale.server.core.command.system.arguments.system.DefaultArg`
- `com.hypixel.hytale.server.core.command.system.arguments.system.FlagArg`

## Permissions
- Look for HytalePermissions in the JAR (not documented yet)

## ECS Types
- `com.hypixel.hytale.server.core.universe.PlayerRef`
- `com.hypixel.hytale.server.core.universe.world.World`
- `com.hypixel.hytale.server.core.universe.world.storage.EntityStore`
- Ref<EntityStore> - need to find exact import

Note: The official documentation at hytalemodding.dev may be outdated and show old package names.
