package net.badgersmc.nexus.permissions

/**
 * Bukkit-aligned permission default. Maps one-to-one to
 * `org.bukkit.permissions.PermissionDefault` at serialization time so the
 * DSL can stay free of any Paper / Bukkit dependency. See REQ-201.
 */
enum class Default {
    OP,
    NOT_OP,
    TRUE,
    FALSE,
}

/**
 * A single node in a permission tree. Names are fully-qualified
 * (dotted) — children carry their parent's prefix already applied.
 */
data class PermissionNode(
    val name: String,
    val default: Default,
    val description: String?,
    val children: List<PermissionNode>,
)

/**
 * Root container for a built permission tree. Exposed by
 * [permissionTree]; produced by the DSL builder. See REQ-200.
 */
class PermissionTree internal constructor(
    val roots: List<PermissionNode>,
) {
    /** Locate any node in the tree by fully-qualified name, or null. */
    fun find(name: String): PermissionNode? {
        fun walk(nodes: List<PermissionNode>): PermissionNode? {
            for (node in nodes) {
                if (node.name == name) return node
                val hit = walk(node.children)
                if (hit != null) return hit
            }
            return null
        }
        return walk(roots)
    }
}

@DslMarker
annotation class PermissionDsl

/** Entry point of the DSL. See REQ-200. */
fun permissionTree(block: PermissionTreeBuilder.() -> Unit): PermissionTree {
    val builder = PermissionTreeBuilder()
    builder.block()
    return PermissionTree(builder.build())
}

@PermissionDsl
class PermissionTreeBuilder internal constructor() {
    private val roots = mutableListOf<PermissionNode>()

    fun node(
        name: String,
        default: Default = Default.OP,
        description: String? = null,
        block: PermissionNodeBuilder.() -> Unit = {},
    ) {
        val nb = PermissionNodeBuilder(name)
        nb.block()
        roots += PermissionNode(name, default, description, nb.build())
    }

    internal fun build(): List<PermissionNode> = roots.toList()
}

@PermissionDsl
class PermissionNodeBuilder internal constructor(private val parentName: String) {
    private val children = mutableListOf<PermissionNode>()

    fun child(
        name: String,
        default: Default = Default.OP,
        description: String? = null,
        block: PermissionNodeBuilder.() -> Unit = {},
    ) {
        // Accept both forms: relative `child("reload")` under
        // `node("foo.admin")` produces `foo.admin.reload`; fully-qualified
        // `child("foo.admin.reload")` is left alone (matches the example
        // shape in docs/roadmap.md §150-167).
        val qualified = if (name == parentName || name.startsWith("$parentName.")) {
            name
        } else {
            "$parentName.$name"
        }
        val nb = PermissionNodeBuilder(qualified)
        nb.block()
        children += PermissionNode(qualified, default, description, nb.build())
    }

    internal fun build(): List<PermissionNode> = children.toList()
}
