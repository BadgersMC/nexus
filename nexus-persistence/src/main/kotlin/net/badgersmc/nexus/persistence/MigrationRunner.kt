package net.badgersmc.nexus.persistence

import java.time.Instant
import java.util.zip.ZipFile
import javax.sql.DataSource

/**
 * Versioned, idempotent SQL migration runner. Migrations are SQL files on the
 * classpath whose names follow `V<number>__<name>.sql`. The runner discovers
 * them under [resourcePrefix], applies any whose `version` has not yet been
 * recorded in `schema_migration`, in ascending version order.
 *
 * A migration may contain multiple `;`-separated statements; each is executed
 * individually so that drivers without multi-statement support still work.
 *
 * The migration table is created if missing and looks like:
 *
 * ```
 * CREATE TABLE schema_migration (
 *     version    INTEGER PRIMARY KEY,
 *     name       VARCHAR(255) NOT NULL,
 *     applied_at BIGINT NOT NULL
 * )
 * ```
 */
class MigrationRunner(
    private val dataSource: DataSource,
    private val resourcePrefix: String = "migrations",
    private val classLoader: ClassLoader = MigrationRunner::class.java.classLoader
) {

    data class Migration(val version: Int, val name: String, val resource: String)

    private val migrationPattern = Regex("""V(\d+)__(.+)\.sql""")

    /**
     * Discover, plan, and apply every migration. Returns the list of
     * migrations actually applied during this invocation (excludes ones that
     * were already recorded).
     */
    fun runAll(): List<Migration> {
        val discovered = discover().sortedBy { it.version }
        dataSource.connection.use { conn ->
            conn.autoCommit = false
            ensureMigrationTable(conn)
            val applied = readAppliedVersions(conn)
            val pending = discovered.filterNot { it.version in applied }
            for (migration in pending) {
                applyMigration(conn, migration)
            }
            conn.commit()
            return pending
        }
    }

    /**
     * Discover migrations on the classpath. Public for tooling — callers can
     * sanity-check the discovered set before running.
     */
    fun discover(): List<Migration> {
        // Track all (version, migration) pairs first so we can detect
        // duplicate version numbers across JARs / exploded classpath roots
        // and fail loudly. Silently overwriting one migration with another
        // would make `runAll()` nondeterministic w.r.t. classpath ordering.
        val prefix = resourcePrefix.trim('/')
        val all = scanJars(prefix) + scanExplodedDirs(prefix)

        // Deduplicate identical entries (same resource path discovered via
        // both the JAR walk and the exploded-classpath walk) and then assert
        // there are no remaining version collisions.
        val unique = all.distinctBy { it.resource }
        val collisions = unique.groupBy { it.version }.filterValues { it.size > 1 }
        if (collisions.isNotEmpty()) {
            val msg = collisions.entries.joinToString("; ") { (v, list) ->
                "version $v -> [${list.joinToString { it.resource }}]"
            }
            error("Duplicate migration versions discovered: $msg")
        }
        return unique.sortedBy { it.version }
    }

    /**
     * Fast path: enumerate every JAR on the classloader whose entries start
     * with the prefix. Walking the entire classpath catches migrations that
     * ship in dependency JARs (a single-JAR probe would miss them).
     */
    private fun scanJars(prefix: String): List<Migration> =
        locateClassLoaderJars().flatMap { jar ->
            // skip unreadable jars (signed-jar quirks, etc.)
            runCatching {
                ZipFile(jar).use { zip ->
                    zip.entries().asSequence()
                        .filterNot { it.isDirectory }
                        .filter { it.name.startsWith("$prefix/") }
                        .mapNotNull { entry -> toMigration(entry.name.substringAfterLast('/'), entry.name) }
                        .toList()
                }
            }.getOrDefault(emptyList())
        }

    /** Exploded classpath fallback (also used by unit tests). */
    private fun scanExplodedDirs(prefix: String): List<Migration> =
        classLoader.getResources(prefix).toList()
            .filter { it.protocol == "file" }
            .mapNotNull { url -> runCatching { java.io.File(url.toURI()) }.getOrNull() }
            .filter { it.isDirectory }
            .flatMap { root ->
                root.walkTopDown().filter { it.isFile }.mapNotNull { file ->
                    val resource = "$prefix/${file.relativeTo(root).path.replace(java.io.File.separatorChar, '/')}"
                    toMigration(file.name, resource)
                }.toList()
            }

    /** Parse a migration filename into a [Migration], or null if it doesn't match. */
    private fun toMigration(fileName: String, resource: String): Migration? {
        val match = migrationPattern.matchEntire(fileName) ?: return null
        return Migration(match.groupValues[1].toInt(), match.groupValues[2], resource)
    }

    private fun ensureMigrationTable(conn: java.sql.Connection) {
        conn.createStatement().use {
            it.execute(
                """CREATE TABLE IF NOT EXISTS schema_migration (
                       version    INTEGER PRIMARY KEY,
                       name       VARCHAR(255) NOT NULL,
                       applied_at BIGINT NOT NULL
                   )""".trimIndent()
            )
        }
    }

    private fun readAppliedVersions(conn: java.sql.Connection): Set<Int> {
        val applied = mutableSetOf<Int>()
        conn.createStatement().use { st ->
            st.executeQuery("SELECT version FROM schema_migration").use { rs ->
                while (rs.next()) applied.add(rs.getInt(1))
            }
        }
        return applied
    }

    private fun applyMigration(conn: java.sql.Connection, migration: Migration) {
        val sql = classLoader.getResourceAsStream(migration.resource)?.bufferedReader()?.use { it.readText() }
            ?: error("Missing migration resource: ${migration.resource}")
        for (statement in splitStatements(sql)) {
            conn.createStatement().use { it.execute(statement) }
        }
        conn.prepareStatement(
            "INSERT INTO schema_migration(version, name, applied_at) VALUES (?, ?, ?)"
        ).use { ps ->
            ps.setInt(1, migration.version)
            ps.setString(2, migration.name)
            ps.setLong(3, Instant.now().toEpochMilli())
            ps.executeUpdate()
        }
    }

    /**
     * Split a SQL script into individual statements. Ignores `;` inside single-
     * or double-quoted string literals and `--` line comments. Sufficient for
     * the kinds of DDL these migrations contain — we deliberately do NOT try
     * to handle stored procedures or `$$` quoting.
     */
    private fun splitStatements(sql: String): List<String> = StatementSplitter().split(sql)

    /**
     * Tiny state machine that walks a SQL script char-by-char, tracking quote
     * state so `;` separators and `--` line comments inside string literals are
     * ignored. State lives in fields so each step stays simple.
     */
    private class StatementSplitter {
        private val statements = mutableListOf<String>()
        private val current = StringBuilder()
        private var inSingle = false
        private var inDouble = false

        fun split(sql: String): List<String> {
            var i = 0
            while (i < sql.length) {
                i = if (isLineCommentStart(sql, i)) skipToNewline(sql, i) else { consume(sql[i]); i + 1 }
            }
            flush()
            return statements
        }

        private fun isLineCommentStart(sql: String, i: Int): Boolean =
            !inSingle && !inDouble && sql[i] == '-' && i + 1 < sql.length && sql[i + 1] == '-'

        private fun skipToNewline(sql: String, i: Int): Int {
            val nl = sql.indexOf('\n', i)
            return if (nl == -1) sql.length else nl
        }

        private fun consume(c: Char) {
            when {
                inSingle -> { current.append(c); if (c == '\'') inSingle = false }
                inDouble -> { current.append(c); if (c == '"') inDouble = false }
                c == '\'' -> { current.append(c); inSingle = true }
                c == '"' -> { current.append(c); inDouble = true }
                c == ';' -> flush()
                else -> current.append(c)
            }
        }

        private fun flush() {
            val trimmed = current.toString().trim()
            if (trimmed.isNotEmpty()) statements.add(trimmed)
            current.clear()
        }
    }

    /**
     * Enumerate every jar visible on [classLoader] that contains the configured
     * resource prefix as a top-level entry. This catches migration files that
     * ship in dependency jars, not just the runner's own jar.
     */
    private fun locateClassLoaderJars(): List<java.io.File> {
        val jars = LinkedHashSet<java.io.File>()
        val prefix = resourcePrefix.trim('/')
        try {
            val markers = classLoader.getResources("$prefix/").toList() + classLoader.getResources(prefix).toList()
            for (url in markers) {
                val raw = url.toString()
                if (!raw.startsWith("jar:")) continue
                val jarPart = raw.substringAfter("jar:").substringBefore("!/").removePrefix("file:")
                val decoded = java.net.URLDecoder.decode(jarPart, Charsets.UTF_8)
                val file = java.io.File(decoded)
                if (file.isFile) jars.add(file)
            }
        } catch (_: Exception) {
            // best effort
        }
        return jars.toList()
    }
}
