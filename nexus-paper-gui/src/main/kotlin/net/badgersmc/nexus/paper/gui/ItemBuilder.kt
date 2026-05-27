package net.badgersmc.nexus.paper.gui

import net.kyori.adventure.text.Component
import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta

/**
 * Small DSL for building Adventure-aware [ItemStack]s. Replaces the repeated
 * `ItemStack(material).apply { itemMeta = itemMeta?.apply { ... } }` pattern
 * sprinkled across plugin menus.
 *
 * ```
 * val item = itemStack(Material.EMERALD) {
 *     name(lang.msg("foo"))
 *     lore(lang.msg("line.1"), lang.msg("line.2"))
 *     amount(3)
 * }
 * ```
 */
class ItemBuilder(private val material: Material) {
    private var amount: Int = 1
    private var name: Component? = null
    private val lore: MutableList<Component> = mutableListOf()
    private var glow: Boolean = false
    private var customModelData: Int? = null
    private val metaActions: MutableList<(ItemMeta) -> Unit> = mutableListOf()

    fun amount(count: Int) = apply { this.amount = count }

    fun name(component: Component) = apply { this.name = component }

    fun lore(vararg components: Component) = apply {
        lore.addAll(components)
    }

    fun lore(components: List<Component>) = apply {
        lore.addAll(components)
    }

    /** Adds an enchantment glow effect without granting a real enchantment. */
    fun glow(enabled: Boolean = true) = apply { this.glow = enabled }

    fun customModelData(value: Int) = apply { this.customModelData = value }

    /** Escape hatch for tweaks not directly exposed by this builder. */
    fun meta(action: (ItemMeta) -> Unit) = apply { metaActions.add(action) }

    fun build(): ItemStack {
        val stack = ItemStack(material, amount.coerceAtLeast(1))
        val meta = stack.itemMeta ?: return stack
        name?.let { meta.displayName(it) }
        if (lore.isNotEmpty()) meta.lore(lore)
        if (glow) {
            meta.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING, 1, true)
            meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS)
        }
        customModelData?.let { meta.setCustomModelData(it) }
        for (action in metaActions) action(meta)
        stack.itemMeta = meta
        return stack
    }
}

/** DSL entry point. */
inline fun itemStack(material: Material, block: ItemBuilder.() -> Unit): ItemStack =
    ItemBuilder(material).apply(block).build()
