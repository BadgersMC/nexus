package net.badgersmc.nexus.persistence

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DatabaseFactoryTest {

    @TempDir
    lateinit var tempDir: File

    private val openedSources = mutableListOf<com.zaxxer.hikari.HikariDataSource>()

    @AfterEach
    fun tearDown() {
        openedSources.forEach { runCatching { it.close() } }
        openedSources.clear()
    }

    @Test
    fun `sqlite spec creates pool of size 1 and persists data file`() {
        val dbFile = File(tempDir, "test.db")
        val ds = DatabaseFactory.open(DatabaseSpec.Sqlite(dbFile)).also { openedSources.add(it) }

        assertEquals(1, ds.maximumPoolSize)
        ds.connection.use { conn ->
            conn.createStatement().use { it.execute("CREATE TABLE foo(id INT PRIMARY KEY)") }
            conn.createStatement().use { it.execute("INSERT INTO foo(id) VALUES (42)") }
        }
        assertTrue(dbFile.exists())
    }

    @Test
    fun `JdbcUrl spec honours explicit pool size`() {
        val ds = DatabaseFactory.open(
            DatabaseSpec.JdbcUrl(
                url = "jdbc:sqlite:${File(tempDir, "x.db").absolutePath}",
                maxPoolSize = 4,
                driverClassName = "org.sqlite.JDBC"
            )
        ).also { openedSources.add(it) }
        assertEquals(4, ds.maximumPoolSize)
    }
}
