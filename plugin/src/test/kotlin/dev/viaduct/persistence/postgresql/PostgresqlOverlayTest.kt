package dev.viaduct.persistence.postgresql

import dev.viaduct.persistence.hibernate.EffectiveHibernateArray
import dev.viaduct.persistence.hibernate.EffectiveHibernateComputedRelationship
import dev.viaduct.persistence.hibernate.EffectiveHibernateEntity
import dev.viaduct.persistence.hibernate.EffectiveHibernateJoinTable
import dev.viaduct.persistence.hibernate.EffectiveHibernateModel
import dev.viaduct.persistence.hibernate.EffectiveHibernateRelationship
import dev.viaduct.persistence.hibernate.EffectiveHibernateTable
import dev.viaduct.persistence.hibernate.GraphqlNameKind
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class PostgresqlOverlayTest {
    @Test
    fun `rejects duplicate renderers for an operation class`() {
        val error =
            assertFailsWith<IllegalArgumentException> {
                MigrationRendererRegistry.create(
                    listOf(EdgeFieldMigrationRenderer, EdgeFieldMigrationRenderer),
                )
            }

        assertContains(error.message.orEmpty(), "AddEdgeField")
    }

    @Test
    fun `rejects a registry missing an operation renderer`() {
        val error =
            assertFailsWith<IllegalArgumentException> {
                MigrationRendererRegistry.create(
                    listOf(
                        EdgeFieldMigrationRenderer,
                        ForeignKeyMigrationRenderer,
                        GlobalIdMigrationRenderer,
                    ),
                )
            }

        assertContains(error.message.orEmpty(), "AddArrayCheck")
    }

    @Test
    fun `dispatches each operation to the renderer registered for its class`() {
        val plan =
            PostgresqlMigrationPlan(
                listOf(
                    PostgresqlMigrationOperation.AddEdgeField(
                        EdgeFieldSpec("public", "memberships", "role", "text", true),
                    ),
                    PostgresqlMigrationOperation.AddForeignKey(
                        ForeignKeySpec("public", "teams", "owner_id", "public", "people", "id"),
                    ),
                    PostgresqlMigrationOperation.AddGlobalId(
                        GlobalIdSpec("Team", "public", "teams", "internal_id", "id"),
                    ),
                    PostgresqlMigrationOperation.AddArrayCheck(
                        ArrayCheckSpec("public", "teams", "labels"),
                    ),
                ),
            )

        val sql = PostgresqlMigrationRenderer.render(plan)

        assertContains(sql, "viaduct_edge_field")
        assertContains(sql, "viaduct_foreign_key")
        assertContains(sql, "viaduct_global_id")
        assertContains(sql, "viaduct_array_check")
    }

    @Test
    fun `creates physical foreign keys for dynamic relationships`() {
        val model =
            EffectiveHibernateModel(
                entities = emptyList(),
                relationships =
                    listOf(
                        EffectiveHibernateRelationship(
                            ownerTypeName = "Team",
                            fieldName = "owner",
                            schemaName = "public",
                            tableName = "teams",
                            columnName = "owner_id",
                            graphqlNameKind = GraphqlNameKind.FOREIGN,
                            targetSchemaName = "public",
                            targetTableName = "persons",
                            targetIdColumnName = "id",
                        ),
                    ),
                computedRelationships = emptyList(),
                arrays = emptyList(),
            )

        val sql = PostgresqlOverlay.renderMigration(model)
        val operation =
            EffectiveModelToMigrationPlanMapper.map(model).operations.single()
                as PostgresqlMigrationOperation.AddForeignKey
        kotlin.test.assertEquals(
            ForeignKeySpec("public", "teams", "owner_id", "public", "persons", "id"),
            operation.foreignKey,
        )
        assertContains(sql, "ADD CONSTRAINT \"teams_owner_id_fkey\"")
        assertContains(sql, "FOREIGN KEY (\"owner_id\")")
        assertContains(sql, "REFERENCES \"public\".\"persons\" (\"id\")")
        assertContains(sql, "IF NOT EXISTS")
    }

    @Test
    fun `creates both join-table foreign keys`() {
        val relationship =
            EffectiveHibernateComputedRelationship(
                ownerTypeName = "Group",
                fieldName = "members",
                owner = EffectiveHibernateTable("public", "groups", "id"),
                target = EffectiveHibernateTable("public", "persons", "id"),
                join =
                    EffectiveHibernateJoinTable(
                        "viaduct_internal",
                        "group_members",
                        "group_id",
                        "person_id",
                    ),
            )
        val model =
            EffectiveHibernateModel(
                entities = emptyList(),
                relationships = emptyList(),
                computedRelationships = listOf(relationship),
                arrays = emptyList(),
            )

        val sql = PostgresqlOverlay.renderMigration(model)
        assertContains(sql, "FOREIGN KEY (\"group_id\")")
        assertContains(sql, "REFERENCES \"public\".\"groups\" (\"id\")")
        assertContains(sql, "FOREIGN KEY (\"person_id\")")
        assertContains(sql, "REFERENCES \"public\".\"persons\" (\"id\")")
    }

    @Test
    fun `adds element null checks only for non-null GraphQL list elements`() {
        val model =
            EffectiveHibernateModel(
                entities = emptyList(),
                relationships = emptyList(),
                computedRelationships = emptyList(),
                arrays =
                    listOf(
                        EffectiveHibernateArray(
                            ownerTypeName = "Group",
                            fieldName = "labels",
                            schemaName = "public",
                            tableName = "groups",
                            columnName = "labels",
                            elementNullable = false,
                        ),
                        EffectiveHibernateArray(
                            ownerTypeName = "Group",
                            fieldName = "notes",
                            schemaName = "public",
                            tableName = "groups",
                            columnName = "notes",
                            elementNullable = true,
                        ),
                    ),
            )

        val sql = PostgresqlOverlay.renderMigration(model)
        assertContains(sql, """array_position("labels", NULL) IS NULL""")
        assertContains(sql, """ADD CONSTRAINT "viaduct_groups_labels_no_null_elements"""")
        assertFalse(sql.contains("""array_position("notes", NULL)"""))
    }

    @Test
    fun `global ids are self contained and repeatable`() {
        val model =
            EffectiveHibernateModel(
                entities =
                    listOf(
                        EffectiveHibernateEntity(
                            graphqlName = "Group",
                            schemaName = "application",
                            tableName = "groups",
                            generatedGlobalId = true,
                            internalIdColumnName = "_uuid_id",
                            globalIdColumnName = "id",
                        ),
                    ),
                relationships = emptyList(),
                computedRelationships = emptyList(),
                arrays = emptyList(),
            )

        val sql = PostgresqlOverlay.renderMigration(model)
        assertContains(sql, "is_generated = 'NEVER'")
        assertContains(sql, "encode(")
        assertContains(sql, "decode(")
        assertContains(sql, "translate(")
        assertFalse(sql.contains("encode_global_id"))
        assertFalse(sql.contains("convert_to("))
        assertFalse(sql.contains("textsend("))
        assertFalse(sql.contains("public."))
    }

    @Test
    fun `creates internal association schemas as prerequisites`() {
        val model =
            EffectiveHibernateModel(
                entities = emptyList(),
                relationships = emptyList(),
                computedRelationships =
                    listOf(
                        EffectiveHibernateComputedRelationship(
                            ownerTypeName = "Person",
                            fieldName = "friends",
                            owner = EffectiveHibernateTable("public", "persons", "id"),
                            target = EffectiveHibernateTable("public", "persons", "id"),
                            join =
                                EffectiveHibernateJoinTable(
                                    "viaduct_internal",
                                    "person_friends_associations",
                                    "owner_person_id",
                                    "target_person_id",
                                ),
                        ),
                    ),
                arrays = emptyList(),
            )

        assertContains(
            PostgresqlOverlay.renderPrerequisites(model),
            """CREATE SCHEMA IF NOT EXISTS "viaduct_internal";""",
        )
    }
}
