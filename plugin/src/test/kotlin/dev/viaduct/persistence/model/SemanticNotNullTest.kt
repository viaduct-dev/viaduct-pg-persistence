package dev.viaduct.persistence.model

import viaduct.graphql.schema.graphqljava.extensions.ViaductSchemaFactory
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SemanticNotNullTest {
    private val schema =
        ViaductSchemaFactory.fromTypeDefinitionRegistry(
            """
            directive @resolver on FIELD_DEFINITION

            type Group {
              id: ID
              name: String
              description: String
              members: [Person]
              computed: String @resolver
            }

            type Person {
              id: ID
              group: Group
            }
            """.trimIndent(),
        )

    @Test
    fun `field coordinate overrides SDL nullability`() {
        val model =
            PersistenceModelBuilder().build(
                schema,
                setOf("Group", "Person"),
                policy =
                    PersistenceModelPolicy(
                        semanticNotNullFieldCoordinates = setOf("Group.name", "Person.group"),
                    ),
            )

        val group = model.entities.single { it.graphqlName == "Group" }
        assertFalse(group.attributes.single { it.name == "name" }.nullable)
        assertTrue(group.attributes.single { it.name == "description" }.nullable)
        val person = model.entities.single { it.graphqlName == "Person" }
        assertFalse(person.attributes.single { it.name == "group" }.nullable)
    }

    @Test
    fun `type coordinate affects stored singular fields but not to-many fields`() {
        val model =
            PersistenceModelBuilder().build(
                schema,
                setOf("Group", "Person"),
                policy = PersistenceModelPolicy(semanticNotNullTypeNames = setOf("Group")),
            )

        val group = model.entities.single { it.graphqlName == "Group" }
        assertFalse(group.attributes.single { it.name == "name" }.nullable)
        assertTrue(group.attributes.single { it.name == "members" }.nullable)
    }

    @Test
    fun `rejects invalid semantic coordinates`() {
        assertFailsWith<IllegalArgumentException> {
            PersistenceModelBuilder().build(
                schema,
                setOf("Group", "Person"),
                policy = PersistenceModelPolicy(semanticNotNullFieldCoordinates = setOf("Group.missing")),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            PersistenceModelBuilder().build(
                schema,
                setOf("Group", "Person"),
                policy = PersistenceModelPolicy(semanticNotNullFieldCoordinates = setOf("Group.members")),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            PersistenceModelBuilder().build(
                schema,
                setOf("Group", "Person"),
                policy = PersistenceModelPolicy(semanticNotNullFieldCoordinates = setOf("Group.computed")),
            )
        }
    }
}
