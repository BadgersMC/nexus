package net.badgersmc.nexus.permissions

import org.yaml.snakeyaml.DumperOptions
import org.yaml.snakeyaml.Yaml

/**
 * Serializes a [PermissionTree] into the flat-keyed YAML shape Paper's
 * `paper-plugin.yml` expects under its `permissions:` block. See REQ-202
 * and https://docs.papermc.io/paper/dev/plugin-yml/#permissions.
 *
 * Output is deterministic: top-level node keys and per-node `children`
 * lists are alphabetically sorted, so repeated runs against an unchanged
 * tree produce byte-identical strings (foundation for REQ-205).
 */
object PermissionTreeSerializer {

    fun toYaml(tree: PermissionTree): String {
        val flat = sortedMapOf<String, MutableMap<String, Any>>()
        flatten(tree.roots, flat)

        val options = DumperOptions().apply {
            defaultFlowStyle = DumperOptions.FlowStyle.BLOCK
            indent = 2
            isPrettyFlow = false
        }
        return Yaml(options).dump(flat)
    }

    private fun flatten(
        nodes: List<PermissionNode>,
        sink: MutableMap<String, MutableMap<String, Any>>,
    ) {
        for (node in nodes) {
            val entry = linkedMapOf<String, Any>()
            entry["default"] = node.default.toYamlValue()
            if (node.description != null) {
                entry["description"] = node.description
            }
            if (node.children.isNotEmpty()) {
                entry["children"] = node.children.map { it.name }.sorted()
            }
            val prev = sink.put(node.name, entry)
            require(prev == null) {
                "Duplicate permission node name: ${node.name}. " +
                    "Each fully-qualified permission may only appear once in a tree."
            }
            flatten(node.children, sink)
        }
    }

    private fun Default.toYamlValue(): String = when (this) {
        Default.OP -> "op"
        Default.NOT_OP -> "not op"
        Default.TRUE -> "true"
        Default.FALSE -> "false"
    }
}
