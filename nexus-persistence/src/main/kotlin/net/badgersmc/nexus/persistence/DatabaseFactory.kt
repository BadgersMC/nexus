package net.badgersmc.nexus.persistence

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import javax.sql.DataSource

/**
 * Build a HikariCP-backed [DataSource] from a [DatabaseSpec]. Pool sizing is
 * picked automatically:
 *
 * - SQLite: `maximumPoolSize = 1` (SQLite serialises writes).
 * - Networked DBs: `maximumPoolSize = 10` unless overridden by [JdbcUrl].
 *
 * No driver registration is performed — the JDBC driver must be on the
 * runtime classpath (provided either by Paper's runtime library loader or via
 * shadow). The class names below match the Maven artifacts declared in this
 * module's `compileOnly` block.
 */
object DatabaseFactory {

    fun open(spec: DatabaseSpec): HikariDataSource {
        val hc = HikariConfig()
        when (spec) {
            is DatabaseSpec.Sqlite -> {
                spec.file.parentFile?.mkdirs()
                hc.jdbcUrl = "jdbc:sqlite:${spec.file.absolutePath}"
                hc.maximumPoolSize = 1
                hc.driverClassName = "org.sqlite.JDBC"
            }
            is DatabaseSpec.MariaDB -> {
                hc.jdbcUrl = buildUrl("jdbc:mariadb://${spec.host}:${spec.port}/${spec.database}", spec.params)
                hc.username = spec.username
                hc.password = spec.password
                hc.maximumPoolSize = 10
                hc.driverClassName = "org.mariadb.jdbc.Driver"
            }
            is DatabaseSpec.Postgres -> {
                hc.jdbcUrl = buildUrl("jdbc:postgresql://${spec.host}:${spec.port}/${spec.database}", spec.params)
                hc.username = spec.username
                hc.password = spec.password
                hc.maximumPoolSize = 10
                hc.driverClassName = "org.postgresql.Driver"
            }
            is DatabaseSpec.JdbcUrl -> {
                hc.jdbcUrl = spec.url
                spec.username?.let { hc.username = it }
                spec.password?.let { hc.password = it }
                hc.maximumPoolSize = spec.maxPoolSize
                spec.driverClassName?.let { hc.driverClassName = it }
            }
        }
        return HikariDataSource(hc)
    }

    private fun buildUrl(base: String, params: Map<String, String>): String {
        if (params.isEmpty()) return base
        val query = params.entries.joinToString("&") { (k, v) ->
            "${encode(k)}=${encode(v)}"
        }
        return "$base?$query"
    }

    private fun encode(value: String): String =
        java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8)
}

/**
 * Provider port for resolving a [DataSource] by name, in case a plugin runs
 * against multiple databases (e.g. analytics + game state). Consumers can
 * register a single named instance via `nexus.registerBean("dataSource", ...)`.
 */
interface DataSourceProvider {
    fun get(name: String = "default"): DataSource
}
