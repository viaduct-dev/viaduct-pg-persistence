package dev.viaduct.persistence.hibernate

import dev.viaduct.persistence.model.PersistenceBasicAttribute
import dev.viaduct.persistence.model.PersistenceModelBuilder
import viaduct.graphql.schema.graphqljava.extensions.ViaductSchemaFactory
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

/**
 * Viaduct's `BigDecimal` scalar coerces to `java.math.BigDecimal`, and pg_graphql exposes the
 * backing Postgres `numeric` column under its own scalar name (`BigFloat`), serialized as a JSON
 * string to preserve precision. Verified directly against a local pg_graphql instance that a
 * `numeric` column round-trips through that string encoding without loss, and that the runtime's
 * response decoder has a dedicated `java.math.BigDecimal` branch (`content.toBigDecimal()`) that
 * doesn't care which scalar name pg_graphql used. This test locks in the persistence plugin's own
 * half of that chain: schema -> model -> generated Hibernate mapping.
 */
class BigDecimalAttributeTest {
    @Test
    fun `a BigDecimal field maps to a plain numeric column with no precision override`() {
        val schema =
            ViaductSchemaFactory.fromTypeDefinitionRegistry(
                """
                scalar BigDecimal

                type Invoice {
                  id: ID!
                  amount: BigDecimal
                  total: BigDecimal!
                }
                """.trimIndent(),
            )
        val model = PersistenceModelBuilder().build(schema, setOf("Invoice"))
        val invoice = model.entities.single { it.graphqlName == "Invoice" }

        val amount = invoice.attributes.single { it.name == "amount" } as PersistenceBasicAttribute
        assertEquals("java.math.BigDecimal", amount.kotlinType)
        assertEquals(true, amount.nullable)
        assertEquals(null, amount.columnDefinition)

        val total = invoice.attributes.single { it.name == "total" } as PersistenceBasicAttribute
        assertEquals("java.math.BigDecimal", total.kotlinType)
        assertEquals(false, total.nullable)

        val outputDirectory = Files.createTempDirectory("hibernate-bigdecimal").toFile()
        try {
            HibernateSchemaModelWriter().write(
                model = model,
                outputDirectory = outputDirectory,
            )
            val mapping =
                outputDirectory
                    .resolve("resources/META-INF/viaduct-persistence.hbm.xml")
                    .readText()
            assertContains(mapping, """<property name="amount" not-null="false" type="java.math.BigDecimal">""")
            assertContains(mapping, """<column name="amount" not-null="false"/>""")
            assertContains(mapping, """<property name="total" not-null="true" type="java.math.BigDecimal">""")
            assertContains(mapping, """<column name="total" not-null="true"/>""")
        } finally {
            outputDirectory.deleteRecursively()
        }
    }
}
