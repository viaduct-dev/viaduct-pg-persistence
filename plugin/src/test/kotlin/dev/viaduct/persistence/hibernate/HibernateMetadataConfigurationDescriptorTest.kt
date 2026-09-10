package dev.viaduct.persistence.hibernate

import dev.viaduct.persistence.model.PersistenceBasicAttribute
import dev.viaduct.persistence.model.PersistenceEdgeMapping
import dev.viaduct.persistence.model.PersistenceEntity
import dev.viaduct.persistence.model.PersistenceEnum
import dev.viaduct.persistence.model.PersistenceModel
import dev.viaduct.persistence.model.PersistenceToManyAttribute
import dev.viaduct.persistence.model.PersistenceToManyStorage
import dev.viaduct.persistence.model.PersistenceToOneAttribute
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HibernateMetadataConfigurationDescriptorTest {
    @Test
    fun `round trips a configuration without a semantic model`() {
        val mappingFile = File("orm.xml")
        val configuration =
            HibernateMetadataConfiguration(
                mappingFile = mappingFile,
                classpath = listOf(File("build/classes/kotlin/main")),
                managedEntityNames = listOf("Group"),
                metadataCustomizerClassNames = listOf("example.CustomMetadataCustomizer"),
                dialectClassName = "org.hibernate.dialect.H2Dialect",
                hibernateSettings = mapOf("hibernate.show_sql" to "true"),
            )

        val roundTripped = roundTrip(configuration)

        assertEquals(mappingFile.absolutePath, roundTripped.mappingFile.absolutePath)
        assertEquals(
            configuration.classpath.map(File::getAbsolutePath),
            roundTripped.classpath.map(File::getAbsolutePath),
        )
        assertEquals(configuration.managedEntityNames, roundTripped.managedEntityNames)
        assertEquals(configuration.implicitNamingStrategyClassName, roundTripped.implicitNamingStrategyClassName)
        assertEquals(configuration.physicalNamingStrategyClassName, roundTripped.physicalNamingStrategyClassName)
        assertEquals(configuration.metadataCustomizerClassNames, roundTripped.metadataCustomizerClassNames)
        assertEquals(configuration.dialectClassName, roundTripped.dialectClassName)
        assertEquals(configuration.hibernateSettings, roundTripped.hibernateSettings)
        assertNull(roundTripped.semanticModel)
    }

    @Test
    fun `round trips a semantic model with nested associations and enums`() {
        val model = groupModelWithNestedAssociationAndEnum()
        val configuration =
            HibernateMetadataConfiguration(
                mappingFile = File("orm.xml"),
                classpath = emptyList(),
                managedEntityNames = listOf("Group"),
                semanticModel = model,
            )

        val roundTripped = roundTrip(configuration)

        assertEquals(model, roundTripped.semanticModel)
    }

    private fun groupModelWithNestedAssociationAndEnum(): PersistenceModel {
        val edgeMapping =
            PersistenceEdgeMapping(
                typeName = "GroupMemberAssociation",
                attributes =
                    listOf(
                        PersistenceBasicAttribute(
                            name = "role",
                            nullable = false,
                            kotlinType = "com.example.Role",
                            enumTypeName = "Role",
                        ),
                    ),
            )
        val group =
            PersistenceEntity(
                graphqlName = "Group",
                generatedGlobalId = true,
                attributes =
                    listOf(
                        PersistenceBasicAttribute(
                            name = "name",
                            nullable = false,
                            kotlinType = "kotlin.String",
                            collection = true,
                            elementNullable = true,
                            columnDefinition = "text",
                        ),
                        PersistenceToOneAttribute(
                            name = "owner",
                            nullable = true,
                            targetTypeName = "Person",
                            idOfDirected = true,
                        ),
                        PersistenceToManyAttribute(
                            name = "members",
                            nullable = false,
                            targetTypeName = "GroupMember",
                            inverseFieldName = "group",
                            storage = PersistenceToManyStorage.JOIN_TABLE_OWNER,
                            joinTableName = "group_members",
                            edgeMapping = edgeMapping,
                        ),
                    ),
            )
        return PersistenceModel(
            entities = listOf(group),
            enums = listOf(PersistenceEnum(graphqlName = "Role", values = listOf("ADMIN", "MEMBER"))),
        )
    }

    private fun roundTrip(configuration: HibernateMetadataConfiguration): HibernateMetadataConfiguration {
        val file = File.createTempFile("hibernate-metadata-configuration-descriptor", ".yaml")
        return try {
            HibernateMetadataConfigurationDescriptor.write(configuration, file)
            HibernateMetadataConfigurationDescriptor.read(file)
        } finally {
            check(file.delete() || !file.exists())
        }
    }
}
