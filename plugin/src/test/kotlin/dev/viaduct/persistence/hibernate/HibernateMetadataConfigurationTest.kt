package dev.viaduct.persistence.hibernate

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HibernateMetadataConfigurationTest {
    @Test
    fun `mappingFile defaults to the plugin's conventional generated location`() {
        assertEquals(
            File("build/generated/viaduct-persistence/resources/META-INF/viaduct-persistence.hbm.xml"),
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
    fun `default builds a configuration from the conventional mapping file location`() {
        val mappingFile = HibernateMetadataConfiguration.defaultMappingFile()
        check(mappingFile.parentFile.isDirectory || mappingFile.parentFile.mkdirs())
        mappingFile.writeText(
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <hibernate-mapping xmlns="http://www.hibernate.org/xsd/orm/hbm">
              <class entity-name="Group" table="groups"/>
            </hibernate-mapping>
            """.trimIndent(),
        )

        try {
            val configuration = HibernateMetadataConfiguration.default()

            assertEquals(mappingFile, configuration.mappingFile)
            assertEquals(listOf("Group"), configuration.managedEntityNames)
            assertTrue(configuration.classpath.isNotEmpty())
        } finally {
            check(mappingFile.delete() || !mappingFile.exists())
        }
    }

    @Test
    fun `policy fields default to Viaduct's standard configuration`() {
        val configuration =
            HibernateMetadataConfiguration(
                mappingFile = File("orm.xml"),
                classpath = emptyList(),
                managedEntityNames = listOf("Entity"),
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
    fun `managedEntityNamesIn reads distinct dynamic entities out of a generated mapping file`() {
        val mappingFile =
            File.createTempFile("hibernate-metadata-configuration", ".xml").also {
                it.writeText(
                    """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <hibernate-mapping xmlns="http://www.hibernate.org/xsd/orm/hbm">
                      <class entity-name="Group" table="groups"/>
                      <class entity-name="GroupMembersAssociation" table="group_members"/>
                      <class entity-name="Group" table="groups"/>
                    </hibernate-mapping>
                    """.trimIndent(),
                )
            }

        try {
            assertEquals(
                listOf("Group", "GroupMembersAssociation"),
                HibernateMetadataConfiguration.managedEntityNamesIn(mappingFile),
            )
        } finally {
            check(mappingFile.delete() || !mappingFile.exists())
        }
    }
}
