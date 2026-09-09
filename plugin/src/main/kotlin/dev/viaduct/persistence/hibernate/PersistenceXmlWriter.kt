package dev.viaduct.persistence.hibernate

import org.w3c.dom.Document

/** Creates the persistence.xml document for the generated dynamic Hibernate mapping. */
internal class PersistenceXmlWriter {
    fun document(persistenceUnitName: String): Document =
        StringTemplateXmlRenderer.document(
            TEMPLATE_RESOURCE,
            PersistenceXmlModel(
                persistenceUnitName = persistenceUnitName,
                implicitNamingStrategy = ViaductImplicitNamingStrategy::class.java.name,
                physicalNamingStrategy = ViaductPhysicalNamingStrategy::class.java.name,
            ),
        )

    private companion object {
        private const val TEMPLATE_RESOURCE =
            "/dev/viaduct/persistence/hibernate/viaduct-persistence.xml.stg"
    }
}

private data class PersistenceXmlModel(
    val persistenceUnitName: String,
    val implicitNamingStrategy: String,
    val physicalNamingStrategy: String,
)
