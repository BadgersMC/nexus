package net.badgersmc.nexus.persistence

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.net.URL
import java.net.URLClassLoader
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MigrationRunnerTest {

    @TempDir
    lateinit var tempDir: File

    private lateinit var resourceRoot: File
    private lateinit var classLoader: URLClassLoader
    private lateinit var ds: com.zaxxer.hikari.HikariDataSource

    @BeforeEach
    fun setUp() {
        resourceRoot = Files.createTempDirectory("nexus-persistence-test-").toFile()
        classLoader = URLClassLoader(
            arrayOf<URL>(resourceRoot.toURI().toURL()),
            MigrationRunnerTest::class.java.classLoader
        )
        ds = DatabaseFactory.open(DatabaseSpec.Sqlite(File(tempDir, "mig.db")))
    }

    @AfterEach
    fun tearDown() {
        ds.close()
        classLoader.close()
        resourceRoot.deleteRecursively()
    }

    private fun stub(name: String, sql: String) {
        val target = File(resourceRoot, "migrations/$name")
        target.parentFile.mkdirs()
        target.writeText(sql)
    }

    @Test
    fun `runAll applies discovered migrations in version order`() {
        stub("V001__init.sql", "CREATE TABLE foo(id INT PRIMARY KEY);")
        stub("V002__add_bar.sql", "ALTER TABLE foo ADD COLUMN bar TEXT;")

        val runner = MigrationRunner(ds, classLoader = classLoader)
        val applied = runner.runAll()

        assertEquals(listOf(1, 2), applied.map { it.version })
        // schema_migration recorded both versions
        ds.connection.use { conn ->
            conn.createStatement().use { st ->
                st.executeQuery("SELECT version FROM schema_migration ORDER BY version").use { rs ->
                    val versions = mutableListOf<Int>()
                    while (rs.next()) versions.add(rs.getInt(1))
                    assertEquals(listOf(1, 2), versions)
                }
            }
        }
    }

    @Test
    fun `runAll is idempotent — second call applies nothing`() {
        stub("V001__init.sql", "CREATE TABLE foo(id INT PRIMARY KEY);")
        val runner = MigrationRunner(ds, classLoader = classLoader)
        assertEquals(1, runner.runAll().size)
        val second = runner.runAll()
        assertTrue(second.isEmpty())
    }

    @Test
    fun `runAll splits multi-statement scripts on semicolons`() {
        stub("V001__multi.sql", """
            CREATE TABLE a(id INT PRIMARY KEY);
            CREATE TABLE b(id INT PRIMARY KEY);
            INSERT INTO a(id) VALUES (1);
            INSERT INTO b(id) VALUES (2);
        """.trimIndent())

        MigrationRunner(ds, classLoader = classLoader).runAll()

        ds.connection.use { conn ->
            conn.createStatement().use { st ->
                val rs = st.executeQuery("SELECT (SELECT id FROM a) + (SELECT id FROM b) AS s")
                rs.next()
                assertEquals(3, rs.getInt("s"))
            }
        }
    }

    @Test
    fun `runAll ignores semicolons inside string literals`() {
        stub("V001__quoted.sql", """
            CREATE TABLE notes(id INT PRIMARY KEY, body TEXT);
            INSERT INTO notes(id, body) VALUES (1, 'hello; world');
        """.trimIndent())

        MigrationRunner(ds, classLoader = classLoader).runAll()

        ds.connection.use { conn ->
            conn.createStatement().use { st ->
                val rs = st.executeQuery("SELECT body FROM notes WHERE id=1")
                rs.next()
                assertEquals("hello; world", rs.getString("body"))
            }
        }
    }

    @Test
    fun `runAll ignores line comments`() {
        stub("V001__commented.sql", """
            -- This is a comment; with a semicolon
            CREATE TABLE foo(id INT PRIMARY KEY);
        """.trimIndent())

        val applied = MigrationRunner(ds, classLoader = classLoader).runAll()
        assertEquals(1, applied.size)
    }

    @Test
    fun `discover returns empty list when no migrations on classpath`() {
        val discovered = MigrationRunner(ds, classLoader = classLoader).discover()
        assertTrue(discovered.isEmpty())
    }

    @Test
    fun `discover parses version and name from filename`() {
        stub("V003__add_widgets.sql", "CREATE TABLE w(id INT);")
        val discovered = MigrationRunner(ds, classLoader = classLoader).discover()
        assertEquals(1, discovered.size)
        val migration = discovered.first()
        assertEquals(3, migration.version)
        assertEquals("add_widgets", migration.name)
        assertNotNull(migration.resource)
    }
}
