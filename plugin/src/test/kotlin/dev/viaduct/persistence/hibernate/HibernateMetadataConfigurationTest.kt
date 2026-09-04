package dev.viaduct.persistence.hibernate

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

class HibernateMetadataConfigurationTest {
    @Test
    fun `policy fields default to Viaduct's standard configuration`() {
        val configuration =
            HibernateMetadataConfiguration(
                mappingFile = File("orm.xml"),
                classpath = emptyList(),
                managedClassNames = listOf("example.Entity"),
            )

        assertEquals(
            ViaductImplicitNamingStrategy::class.java.name,
            configuration.implicitNamingStrategyClassName,
        )
        assertEquals(
            ViaductPhysicalNamingStrategy::class.java.name,
            configuration.physicalNamingStrategyClassName,
        )
        assertEquals(emptyList(), configuration.metadataCustomizerClassNames)
        assertEquals(HibernateMetadataConfiguration.DEFAULT_DIALECT, configuration.dialectClassName)
        assertEquals(HibernateMetadataConfiguration.defaultSettings(), configuration.hibernateSettings)
    }

    @Test
    fun `managedClassNamesIn reads distinct entity classes out of a generated mapping file`() {
        val mappingFile =
            File.createTempFile("hibernate-metadata-configuration", ".xml").also {
                it.writeText(
                    """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <entity-mappings xmlns="https://jakarta.ee/xml/ns/persistence/orm" version="3.2">
                      <entity class="example.generated.Group" access="FIELD">
                        <table name="groups"/>
                      </entity>
                      <entity class="example.generated.GroupMembersAssociation" access="FIELD">
                        <table name="group_members"/>
                      </entity>
                      <entity class="example.generated.Group" access="FIELD">
                        <table name="groups"/>
                      </entity>
                    </entity-mappings>
                    """.trimIndent(),
                )
            }

        try {
            assertEquals(
                listOf("example.generated.Group", "example.generated.GroupMembersAssociation"),
                HibernateMetadataConfiguration.managedClassNamesIn(mappingFile),
            )
        } finally {
            mappingFile.delete()
        }
    }
}
