package dev.viaduct.persistence.hibernate

import org.w3c.dom.Document

/** Creates the persistence.xml document for the generated dynamic Hibernate mapping. */
internal class PersistenceXmlWriter {
    fun document(persistenceUnitName: String): Document =
        StringTemplateXmlRenderer.document(
            TEMPLATE_RESOURCE,
            mapOf(
                "persistenceUnitName" to persistenceUnitName,
                "implicitNamingStrategy" to ViaductImplicitNamingStrategy::class.java.name,
                "physicalNamingStrategy" to ViaductPhysicalNamingStrategy::class.java.name,
            ),
        )

    private companion object {
        private const val TEMPLATE_RESOURCE =
            "/dev/viaduct/persistence/hibernate/viaduct-persistence.xml.stg"
    }
}
