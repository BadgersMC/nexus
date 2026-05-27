package net.badgersmc.nexus.permissions

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

/**
 * Regression coverage for code-review findings on PR #9:
 *
 * - PermissionTreeBuilder.child must accept already-fully-qualified
 *   names (matches the example shape in docs/roadmap.md §150-167)
 *   without double-prefixing.
 * - PermissionTreeSerializer must fail fast when the same fully-
 *   qualified name appears twice in a tree, rather than silently
 *   overwriting the earlier entry.
 */
class CodeReviewRegressionsTest {

    @Test
    fun `child accepts fully-qualified name without double-prefixing`() {
        val tree = permissionTree {
            node("foo.admin", default = Default.OP) {
                child("foo.admin.reload")
                child("foo.admin.import", default = Default.NOT_OP)
            }
        }

        assertNotNull(tree.find("foo.admin.reload"))
        assertNotNull(tree.find("foo.admin.import"))
        // The buggy version produced foo.admin.foo.admin.reload.
        assertEquals(null, tree.find("foo.admin.foo.admin.reload"))
    }

    @Test
    fun `child still qualifies a bare relative name`() {
        val tree = permissionTree {
            node("foo.admin") {
                child("reload")
            }
        }
        assertNotNull(tree.find("foo.admin.reload"))
    }

    @Test
    fun `serializer rejects duplicate fully-qualified node names`() {
        // Construct a tree where two siblings collide once flattened.
        // The DSL itself doesn't prevent this — the serializer is the
        // gate, because that's where the YAML map keys are written.
        val tree = permissionTree {
            node("foo.admin", default = Default.OP)
            node("foo.admin", default = Default.NOT_OP)
        }

        val err = assertFailsWith<IllegalArgumentException> {
            PermissionTreeSerializer.toYaml(tree)
        }
        assert(err.message!!.contains("foo.admin")) {
            "Expected message to name the duplicate. Got: ${err.message}"
        }
    }
}
