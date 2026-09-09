package dev.viaduct.persistence.hibernate

import org.w3c.dom.Element
import kotlin.test.Test
import kotlin.test.assertEquals

class PersistenceXmlWriterTest {
    @Test
    fun `renders persistence configuration from the StringTemplate resource`() {
        val document = PersistenceXmlWriter().document("unit & integration")
        val unit = document.elements("persistence-unit").single()

        assertEquals("unit & integration", unit.getAttribute("name"))
        assertEquals("RESOURCE_LOCAL", unit.getAttribute("transaction-type"))
        assertEquals(
            "META-INF/viaduct-persistence.hbm.xml",
            document.elements("mapping-file").single().textContent,
        )
        val properties =
            document.elements("property").associate {
                it.getAttribute("name") to it.getAttribute("value")
            }
        assertEquals(
            ViaductImplicitNamingStrategy::class.java.name,
            properties["hibernate.implicit_naming_strategy"],
        )
        assertEquals(
            ViaductPhysicalNamingStrategy::class.java.name,
            properties["hibernate.physical_naming_strategy"],
        )
    }

    private fun org.w3c.dom.Document.elements(name: String): List<Element> =
        documentElement
            .getElementsByTagNameNS(HibernateXmlDocuments.PERSISTENCE_NS, name)
            .let { nodes -> (0 until nodes.length).map { nodes.item(it) as Element } }
}
