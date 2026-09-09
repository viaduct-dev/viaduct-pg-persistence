package dev.viaduct.persistence.hibernate

import org.w3c.dom.Element
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class HbmXmlWriterTest {
    @Test
    fun `renders every mapping shape from the StringTemplate resource`() {
        val mapping =
            HbmMappingDocument(
                listOf(
                    HbmEntityMapping(
                        entityName = "Team & Core",
                        tableName = "teams",
                        schemaName = "tenant",
                        attributes =
                            listOf(
                                HbmBasicMapping("internalId", "uuid", "internal_id", false, true),
                                HbmBasicMapping(
                                    "displayName",
                                    "string",
                                    "display_name",
                                    true,
                                    false,
                                    insertable = false,
                                    updatable = false,
                                    columnDefinition = "text",
                                ),
                                HbmToOneMapping("owner", "Person", "owner_id", false, "fk_owner"),
                                HbmToManyMapping("members", "Person", "team_id", inverse = true),
                                HbmToManyMapping(
                                    "labels",
                                    "Label",
                                    "team_id",
                                    inverse = false,
                                    joinTableName = "team_labels",
                                    joinSchemaName = "links",
                                    targetColumnName = "label_id",
                                    targetForeignKeyName = "fk_label",
                                ),
                            ),
                    ),
                ),
            )

        val document = HbmXmlWriter().document(mapping)
        val entity = document.elements("class").single()
        assertEquals("Team & Core", entity.getAttribute("entity-name"))
        assertEquals("tenant", entity.getAttribute("schema"))

        assertEquals("assigned", document.elements("generator").single().getAttribute("class"))
        val property = document.elements("property").single()
        assertEquals("false", property.getAttribute("insert"))
        assertEquals("false", property.getAttribute("update"))
        assertEquals("text", property.elements("column").single().getAttribute("sql-type"))

        val toOne = document.elements("many-to-one").single()
        assertEquals("true", toOne.getAttribute("not-null"))
        assertEquals("fk_owner", toOne.getAttribute("foreign-key"))
        assertNotNull(document.elements("one-to-many").single())

        val bag = document.elements("bag").single { it.getAttribute("name") == "labels" }
        assertEquals("team_labels", bag.getAttribute("table"))
        assertEquals("links", bag.getAttribute("schema"))
        val manyToMany = bag.elements("many-to-many").single()
        assertEquals("label_id", manyToMany.getAttribute("column"))
        assertEquals("fk_label", manyToMany.getAttribute("foreign-key"))
    }

    private fun org.w3c.dom.Document.elements(name: String): List<Element> = documentElement.elements(name)

    private fun Element.elements(name: String): List<Element> =
        getElementsByTagNameNS(HibernateXmlDocuments.HBM_NS, name)
            .let { nodes -> (0 until nodes.length).map { nodes.item(it) as Element } }
}
