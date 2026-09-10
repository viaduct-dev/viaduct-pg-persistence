package dev.viaduct.persistence.pggraphql.overlay

import dev.viaduct.persistence.hibernate.EffectiveHibernateComputedRelationship
import dev.viaduct.persistence.hibernate.EffectiveHibernateEdgeField
import dev.viaduct.persistence.hibernate.EffectiveHibernateJoinTable
import dev.viaduct.persistence.hibernate.EffectiveHibernateModel
import dev.viaduct.persistence.hibernate.EffectiveHibernateRelationship
import dev.viaduct.persistence.hibernate.EffectiveHibernateTable
import dev.viaduct.persistence.hibernate.GraphqlNameKind
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PgGraphqlConstraintRendererTest {
    @Test
    fun `names object-valued association edge relationships`() {
        val model =
            EffectiveHibernateModel(
                entities = emptyList(),
                relationships = emptyList(),
                computedRelationships =
                    listOf(
                        EffectiveHibernateComputedRelationship(
                            ownerTypeName = "Group",
                            fieldName = "members",
                            owner = EffectiveHibernateTable("application", "groups", "id"),
                            target = EffectiveHibernateTable("application", "persons", "id"),
                            join =
                                EffectiveHibernateJoinTable(
                                    "viaduct_internal",
                                    "group_members_associations",
                                    "group_id",
                                    "person_id",
                                ),
                            edgeFields =
                                listOf(
                                    EffectiveHibernateEdgeField(
                                        name = "invitedBy",
                                        columnName = "invited_by_id",
                                        sqlType = "uuid",
                                        nullable = true,
                                        targetSchemaName = "application",
                                        targetTableName = "persons",
                                        targetIdColumnName = "id",
                                    ),
                                ),
                        ),
                    ),
                arrays = emptyList(),
            )

        assertEquals(
            """@graphql({"foreign_name": "invitedBy"})""",
            PgGraphqlConstraintRenderer.commentValue(
                model,
                "viaduct_internal",
                "group_members_associations",
                "invited_by_id",
            ),
        )
    }

    @Test
    fun `renders a foreign-key comment and exposes its value directly`() {
        val model = modelOf(foreignRelationship())

        val comment = PgGraphqlConstraintRenderer.commentValue(model, "public", "groups", "owner_id")
        assertEquals("""@graphql({"foreign_name": "owner"})""", comment)

        val sql = PgGraphqlConstraintRenderer.render(model)
        assertContains(sql, "COMMENT ON CONSTRAINT")
        assertContains(sql, comment!!)
    }

    @Test
    fun `merges a foreign and local relationship sharing the same constraint column`() {
        val model =
            modelOf(
                foreignRelationship(),
                EffectiveHibernateRelationship(
                    ownerTypeName = "User",
                    fieldName = "ownedGroups",
                    schemaName = "public",
                    tableName = "groups",
                    columnName = "owner_id",
                    graphqlNameKind = GraphqlNameKind.LOCAL,
                ),
            )

        val comment = PgGraphqlConstraintRenderer.commentValue(model, "public", "groups", "owner_id")
        assertEquals("""@graphql({"foreign_name": "owner", "local_name": "ownedGroups"})""", comment)
    }

    @Test
    fun `returns null for a column with no relationship`() {
        assertNull(PgGraphqlConstraintRenderer.commentValue(modelOf(), "public", "groups", "owner_id"))
    }

    @Test
    fun `synthesizes local_name for two unpaired foreign keys to the same target table`() {
        val model =
            modelOf(
                foreignRelationship(
                    ownerTypeName = "DiscordServerRoleGroup",
                    fieldName = "externalGroup",
                    tableName = "discord_server_role_groups",
                    columnName = "external_group_id",
                    targetTableName = "external_groups",
                ),
                foreignRelationship(
                    ownerTypeName = "DiscordServerRoleGroup",
                    fieldName = "server",
                    tableName = "discord_server_role_groups",
                    columnName = "server_id",
                    targetTableName = "external_groups",
                ),
            )

        assertEquals(
            """@graphql({"foreign_name": "externalGroup", "local_name": "discordServerRoleGroupsByExternalGroup"})""",
            PgGraphqlConstraintRenderer.commentValue(
                model,
                "public",
                "discord_server_role_groups",
                "external_group_id",
            ),
        )
        assertEquals(
            """@graphql({"foreign_name": "server", "local_name": "discordServerRoleGroupsByServer"})""",
            PgGraphqlConstraintRenderer.commentValue(model, "public", "discord_server_role_groups", "server_id"),
        )
    }

    @Test
    fun `does not synthesize local_name for a single unambiguous foreign key`() {
        val model =
            modelOf(
                foreignRelationship(targetTableName = "users"),
            )

        assertEquals(
            """@graphql({"foreign_name": "owner"})""",
            PgGraphqlConstraintRenderer.commentValue(model, "public", "groups", "owner_id"),
        )
    }

    private fun foreignRelationship(
        ownerTypeName: String = "Group",
        fieldName: String = "owner",
        tableName: String = "groups",
        columnName: String = "owner_id",
        targetTableName: String? = null,
    ): EffectiveHibernateRelationship =
        EffectiveHibernateRelationship(
            ownerTypeName = ownerTypeName,
            fieldName = fieldName,
            schemaName = "public",
            tableName = tableName,
            columnName = columnName,
            graphqlNameKind = GraphqlNameKind.FOREIGN,
            targetSchemaName = targetTableName?.let { "public" },
            targetTableName = targetTableName,
        )

    private fun modelOf(vararg relationships: EffectiveHibernateRelationship): EffectiveHibernateModel =
        EffectiveHibernateModel(
            entities = emptyList(),
            relationships = relationships.toList(),
            computedRelationships = emptyList(),
            arrays = emptyList(),
        )
}
