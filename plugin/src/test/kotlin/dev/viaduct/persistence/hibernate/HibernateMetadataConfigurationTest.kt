package dev.viaduct.persistence.hibernate

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HibernateMetadataConfigurationTest {
    @Test
    fun `mappingFile defaults to the plugin's conventional generated location`() {
        assertEquals(
            File("build/generated/viaduct-persistence/resources/META-INF/orm.xml"),
            HibernateMetadataConfiguration.defaultMappingFile(),
        )
    }

    @Test
    fun `classpath defaults to the current JVM classpath`() {
        val classpath = HibernateMetadataConfiguration.defaultClasspath()

        assertTrue(classpath.isNotEmpty())
        assertTrue(classpath.all(File::exists))
    }

    @Test
    fun `mechanical fields default from an already-generated mapping file`() {
        val mappingFile =
            File.createTempFile("hibernate-metadata-configuration-defaults", ".xml").also {
                it.writeText(
                    """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <entity-mappings xmlns="https://jakarta.ee/xml/ns/persistence/orm" version="3.2">
                      <entity class="example.generated.Group" access="FIELD">
                        <table name="groups"/>
                      </entity>
                    </entity-mappings>
                    """.trimIndent(),
                )
            }

        try {
            val configuration = HibernateMetadataConfiguration(mappingFile = mappingFile)

            assertEquals(listOf("example.generated.Group"), configuration.managedClassNames)
            assertTrue(configuration.classpath.isNotEmpty())
        } finally {
            mappingFile.delete()
        }
    }

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
