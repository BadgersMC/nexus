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
        val all = mutableListOf<Migration>()
        val prefix = resourcePrefix.trim('/')

        // Fast path: enumerate every JAR on the classloader whose entries
        // start with the prefix. Walking the entire classpath catches
        // migrations that ship in dependency JARs (a single-JAR probe would
        // miss them — only the runner's own JAR was visible before).
        for (jar in locateClassLoaderJars()) {
            try {
                ZipFile(jar).use { zip ->
                    val entries = zip.entries()
                    while (entries.hasMoreElements()) {
                        val entry = entries.nextElement()
                        if (entry.isDirectory) continue
                        val name = entry.name
                        if (!name.startsWith("$prefix/")) continue
                        val fileName = name.substringAfterLast('/')
                        val match = migrationPattern.matchEntire(fileName) ?: continue
                        val version = match.groupValues[1].toInt()
                        val title = match.groupValues[2]
                        all += Migration(version, title, name)
                    }
                }
            } catch (_: Exception) {
                // skip unreadable jars (signed-jar quirks, etc.)
            }
        }

        // Exploded classpath fallback (also used by unit tests).
        val dirUrls = classLoader.getResources(prefix).toList()
        for (url in dirUrls) {
            if (url.protocol != "file") continue
            val root = try { java.io.File(url.toURI()) } catch (_: Exception) { continue }
            if (!root.isDirectory) continue
            root.walkTopDown().filter { it.isFile }.forEach { file ->
                val match = migrationPattern.matchEntire(file.name) ?: return@forEach
                val version = match.groupValues[1].toInt()
                val title = match.groupValues[2]
                val resourceName = "$prefix/${file.relativeTo(root).path.replace(java.io.File.separatorChar, '/')}"
                all += Migration(version, title, resourceName)
            }
        }

        // Deduplicate identical entries (same resource path discovered via
        // both the JAR walk and the exploded-classpath walk) and then assert
        // there are no remaining version collisions.
        val unique = all.distinctBy { it.resource }
        val byVersion = unique.groupBy { it.version }
        val collisions = byVersion.filterValues { it.size > 1 }
        if (collisions.isNotEmpty()) {
            val msg = collisions.entries.joinToString("; ") { (v, list) ->
                "version $v -> [${list.joinToString { it.resource }}]"
            }
            error("Duplicate migration versions discovered: $msg")
        }
        return unique.sortedBy { it.version }
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
    private fun splitStatements(sql: String): List<String> {
        val statements = mutableListOf<String>()
        val current = StringBuilder()
        var inSingle = false
        var inDouble = false
        var i = 0
        while (i < sql.length) {
            val c = sql[i]
            // Line comment: skip to newline.
            if (!inSingle && !inDouble && c == '-' && i + 1 < sql.length && sql[i + 1] == '-') {
                val nl = sql.indexOf('\n', i)
                i = if (nl == -1) sql.length else nl
                continue
            }
            when {
                inSingle && c == '\'' -> { current.append(c); inSingle = false }
                inDouble && c == '"' -> { current.append(c); inDouble = false }
                !inSingle && !inDouble && c == '\'' -> { current.append(c); inSingle = true }
                !inSingle && !inDouble && c == '"' -> { current.append(c); inDouble = true }
                !inSingle && !inDouble && c == ';' -> {
                    val trimmed = current.toString().trim()
                    if (trimmed.isNotEmpty()) statements.add(trimmed)
                    current.clear()
                }
                else -> current.append(c)
            }
            i++
        }
        val tail = current.toString().trim()
        if (tail.isNotEmpty()) statements.add(tail)
        return statements
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
