package dev.viaduct.persistence.gradle

import dev.viaduct.persistence.hibernate.HibernateSchemaModelWriter
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PersistenceSchemaModelLoaderTest {
    @Test
    fun `denylist subtracts from discovered persistent types`() {
        val fixture = fixture()
        fixture.config.writeText("denyList:\n  types: [AuditEvent]\n")

        val model = PersistenceSchemaModelLoader.build(fixture.schemaDirectory, fixture.config)

        assertEquals(listOf("Group"), model.entities.map { it.graphqlName })
    }

    @Test
    fun `semantic non-null YAML overrides persistence nullability`() {
        val fixture = fixture()
        fixture.config.writeText("semanticNotNull:\n  fields: [Group.name]\n")

        val model = PersistenceSchemaModelLoader.build(fixture.schemaDirectory, fixture.config)

        val group = model.entities.single { it.graphqlName == "Group" }
        assertFalse(group.attributes.single { it.name == "name" }.nullable)
    }

    @Test
    fun `semantic non-null YAML reaches generated Kotlin and ORM mappings`() {
        val fixture = fixture()
        fixture.config.writeText("semanticNotNull:\n  fields: [Group.name]\n")
        val output = fixture.schemaDirectory.parentFile.resolve("generated")

        HibernateSchemaModelWriter().write(
            model = PersistenceSchemaModelLoader.build(fixture.schemaDirectory, fixture.config),
            outputDirectory = output,
            packageName = "test.generated",
        )

        val entity = output.resolve("kotlin/test/generated/GroupEntity.kt").readText()
        assertTrue(entity.contains("open var name: String = \"\""))
        val mapping = output.resolve("resources/META-INF/orm.xml").readText()
        assertTrue(mapping.contains("<basic name=\"name\" optional=\"false\">"))
        assertTrue(mapping.contains("<column name=\"name\" nullable=\"false\"/>"))
        assertEquals(
            "Group.name",
            output.resolve("resources/META-INF/viaduct-persistence-semantic-not-null.txt").readText().trim(),
        )
    }

    @Test
    fun `rejects ineligible denylist entries`() {
        val fixture = fixture()
        fixture.config.writeText("denyList:\n  types: [Missing]\n")

        val failure =
            assertFailsWith<IllegalArgumentException> {
                PersistenceSchemaModelLoader.build(fixture.schemaDirectory, fixture.config)
            }

        assertTrue(failure.message!!.contains("Missing"))
    }

    @Test
    fun `rejects persisted relationships to denied types`() {
        val fixture = fixture(groupField = "audit: AuditEvent")
        fixture.config.writeText("denyList:\n  types: [AuditEvent]\n")

        val failure =
            assertFailsWith<IllegalArgumentException> {
                PersistenceSchemaModelLoader.build(fixture.schemaDirectory, fixture.config)
            }

        assertTrue(failure.message!!.contains("Group.audit"))
        assertTrue(failure.message!!.contains("AuditEvent"))
    }

    private fun fixture(groupField: String = "name: String"): Fixture {
        val root = Files.createTempDirectory("persistence-policy").toFile()
        val schemaDirectory =
            root.resolve("schema").apply {
                check(mkdirs() || isDirectory)
            }
        schemaDirectory.resolve("Model.graphqls").writeText(
            """
            interface Node { id: ID! }

            type Group implements Node {
              id: ID
              $groupField
            }

            type AuditEvent implements Node {
              id: ID
            }
            """.trimIndent(),
        )
        return Fixture(schemaDirectory, root.resolve("persistence.yaml"))
    }

    private data class Fixture(
        val schemaDirectory: java.io.File,
        val config: java.io.File,
    )
}
