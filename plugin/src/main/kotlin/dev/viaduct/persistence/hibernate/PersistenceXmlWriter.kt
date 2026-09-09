package dev.viaduct.persistence.hibernate

import org.w3c.dom.Document

/** Creates the persistence.xml document for the generated dynamic Hibernate mapping. */
internal class PersistenceXmlWriter {
    fun document(persistenceUnitName: String): Document {
        val document = HibernateXmlDocuments.newDocument()
        val persistence =
            document
                .createElementNS(HibernateXmlDocuments.PERSISTENCE_NS, "persistence")
                .apply { setAttribute("version", "3.2") }
        document.appendChild(persistence)
        val unit =
            persistence.child("persistence-unit").apply {
                setAttribute("name", persistenceUnitName)
                setAttribute("transaction-type", "RESOURCE_LOCAL")
            }
        unit.child("mapping-file").textContent = "META-INF/viaduct-persistence.hbm.xml"
        unit.child("exclude-unlisted-classes").textContent = "true"
        val properties = unit.child("properties")
        properties.property("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect")
        properties.property("hibernate.boot.allow_jdbc_metadata_access", "false")
        properties.property("hibernate.implicit_naming_strategy", ViaductImplicitNamingStrategy::class.java.name)
        properties.property("hibernate.physical_naming_strategy", ViaductPhysicalNamingStrategy::class.java.name)
        return document
    }

    private fun org.w3c.dom.Element.property(
        name: String,
        value: String,
    ) {
        child("property").apply {
            setAttribute("name", name)
            setAttribute("value", value)
        }
    }
}
