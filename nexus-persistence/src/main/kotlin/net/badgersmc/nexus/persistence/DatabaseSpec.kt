package net.badgersmc.nexus.persistence

import java.io.File

/**
 * Declarative description of a database connection. Consumers map their plugin
 * config to one of these and hand it to [DatabaseFactory.open].
 */
sealed class DatabaseSpec {
    /** Local file-backed SQLite database. Pool size pinned to 1. */
    data class Sqlite(val file: File) : DatabaseSpec()

    data class MariaDB(
        val host: String,
        val port: Int = 3306,
        val database: String,
        val username: String,
        val password: String = "",
        val params: Map<String, String> = emptyMap()
    ) : DatabaseSpec()

    data class Postgres(
        val host: String,
        val port: Int = 5432,
        val database: String,
        val username: String,
        val password: String = "",
        val params: Map<String, String> = emptyMap()
    ) : DatabaseSpec()

    /** Pre-built JDBC URL escape hatch (for exotic drivers / custom test harnesses). */
    data class JdbcUrl(
        val url: String,
        val username: String? = null,
        val password: String? = null,
        val maxPoolSize: Int = 10,
        val driverClassName: String? = null
    ) : DatabaseSpec()
}
