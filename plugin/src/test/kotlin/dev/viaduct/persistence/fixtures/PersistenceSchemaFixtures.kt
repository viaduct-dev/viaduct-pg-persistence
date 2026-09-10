package dev.viaduct.persistence.fixtures

object PersistenceSchemaFixtures {
    val relationshipsAndArrays: String =
        """
        directive @resolver on FIELD_DEFINITION

        interface Node {
          id: ID!
        }

        type Group implements Node {
          id: ID!
          name: String!
          labels: [String!]
          members: [GroupMember!]!
        }

        type GroupMember {
          id: ID!
          group: Group!
          computed: String @resolver
        }
        """.trimIndent()
}
