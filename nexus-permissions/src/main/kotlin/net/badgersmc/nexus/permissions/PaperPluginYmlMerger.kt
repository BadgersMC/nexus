package net.badgersmc.nexus.permissions

/**
 * Surgically rewrites the `permissions:` block of a `paper-plugin.yml`
 * while leaving every other top-level key, comment, and blank line
 * byte-identical (REQ-204). Idempotent: re-running [merge] against its
 * own output with an unchanged tree produces the same bytes (REQ-205).
 *
 * Strategy: range-based string replacement keyed on column-0 boundaries
 * rather than a full YAML round-trip — round-trips drop comments and
 * normalise whitespace, which REQ-204 forbids.
 */
object PaperPluginYmlMerger {

    fun merge(source: String, tree: PermissionTree): String {
        val block = renderBlock(tree)
        val lines = source.split("\n")

        val startIdx = lines.indexOfFirst { it.startsWith("permissions:") }
        if (startIdx < 0) {
            // No existing block — append after the source, preserving its
            // trailing newline behaviour.
            return if (source.isEmpty() || source.endsWith("\n")) source + block
            else source + "\n" + block
        }

        // Find the end of the existing block: the next column-0 entry
        // that isn't a continuation (i.e. doesn't start with space/tab)
        // and isn't a blank line. Comments at column 0 are treated as
        // siblings, terminating the block.
        val endIdx = ((startIdx + 1) until lines.size).firstOrNull { i ->
            val line = lines[i]
            line.isNotEmpty() && line[0] != ' ' && line[0] != '\t'
        } ?: lines.size

        val before = lines.subList(0, startIdx).joinToString("\n")
        val after = lines.subList(endIdx, lines.size).joinToString("\n")
        val sb = StringBuilder()
        if (before.isNotEmpty()) {
            sb.append(before)
            sb.append("\n")
        }
        sb.append(block)
        if (after.isNotEmpty()) {
            sb.append(after)
        }
        return sb.toString()
    }

    /**
     * Render `permissions:` block (header + 2-space-indented body).
     * Always ends with a single `\n` so callers can splice the block
     * into a source that ends with a newline without producing a
     * double-newline.
     */
    private fun renderBlock(tree: PermissionTree): String {
        val body = PermissionTreeSerializer.toYaml(tree)
        val indented = body.split("\n").joinToString("\n") { line ->
            if (line.isEmpty()) line else "  $line"
        }
        return "permissions:\n" + indented
    }
}
