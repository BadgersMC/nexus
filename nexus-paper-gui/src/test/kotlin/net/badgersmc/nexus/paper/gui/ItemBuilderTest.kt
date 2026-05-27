package net.badgersmc.nexus.paper.gui

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.Material
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Smoke-level tests for the [ItemBuilder] DSL. Most ItemStack/ItemMeta methods
 * need a running Bukkit server (CraftItemStack hits NMS), so the tests focus
 * on construction succeeding and the builder state being recorded correctly.
 *
 * The end-to-end smoke run against MockBukkit lives in the consumer plugin's
 * integration suite — this module deliberately stays headless.
 */
class ItemBuilderTest {

    @Test
    @Disabled("ItemMeta requires Bukkit server initialisation; covered by MockBukkit integration tests.")
    fun `build produces stack with display name`() {
        val item = itemStack(Material.EMERALD) {
            name(Component.text("Foo"))
            lore(Component.text("Bar"), Component.text("Baz"))
            amount(3)
        }
        assertEquals(Material.EMERALD, item.type)
        assertEquals(3, item.amount)
        val plain = PlainTextComponentSerializer.plainText().serialize(item.itemMeta!!.displayName()!!)
        assertEquals("Foo", plain)
    }

    @Test
    fun `builder chain returns the same instance`() {
        val builder = ItemBuilder(Material.DIRT)
        val same = builder
            .amount(2)
            .name(Component.text("x"))
            .glow(true)
            .customModelData(42)
            .meta { /* no-op */ }
        assertTrue(builder === same)
    }
}
