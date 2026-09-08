package dev.viaduct.persistence.postgresql

import dev.viaduct.persistence.hibernate.EffectiveHibernateComputedRelationship
import dev.viaduct.persistence.hibernate.EffectiveHibernateEdgeCollection
import dev.viaduct.persistence.hibernate.EffectiveHibernateEdgeField
import dev.viaduct.persistence.hibernate.EffectiveHibernateJoinTable
import dev.viaduct.persistence.hibernate.EffectiveHibernateTable
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class EdgeFieldMigrationRendererTest {
    @Test
    fun `renders scalar payload nullability`() {
        val required = EdgeFieldMigrationRenderer.render(relationship(), edgeField(nullable = false))
        val optional = EdgeFieldMigrationRenderer.render(relationship(), edgeField(nullable = true))

        assertContains(required, "ADD COLUMN \"role\" varchar(255) NOT NULL")
        assertContains(optional, "ADD COLUMN \"role\" varchar(255);")
        assertFalse(optional.contains("NOT NULL"))
    }

    @Test
    fun `renders object payload foreign key idempotently`() {
        val sql =
            EdgeFieldMigrationRenderer.render(
                relationship(),
                edgeField(name = "invitedBy", columnName = "invited_by_id").copy(
                    targetSchemaName = "application",
                    targetTableName = "persons",
                    targetIdColumnName = "_uuid_id",
                ),
            )

        assertContains(sql, "constraint_def.contype = 'f'")
        assertContains(sql, "column_def.attname = 'invited_by_id'")
        assertContains(sql, "'\"application\".\"persons\"'::regclass")
        assertContains(sql, "ADD CONSTRAINT \"viaduct_group_members_associations_invited_by_id_fkey\"")
        assertContains(sql, "FOREIGN KEY (\"invited_by_id\")")
        assertContains(sql, "REFERENCES \"application\".\"persons\" (\"_uuid_id\")")
    }

    @Test
    fun `does not render collection mappings as columns on the parent association`() {
        val sql =
            EdgeFieldMigrationRenderer.render(
                relationship(),
                edgeField(name = "reviewers", columnName = "association_id").copy(
                    collection =
                        EffectiveHibernateEdgeCollection(
                            target = EffectiveHibernateTable("application", "persons", "_uuid_id"),
                            ownerColumnName = "association_id",
                        ),
                ),
            )

        assertEquals("", sql)
    }

    private fun relationship() =
        EffectiveHibernateComputedRelationship(
            ownerTypeName = "Group",
            fieldName = "members",
            owner = EffectiveHibernateTable("application", "groups", "_uuid_id"),
            target = EffectiveHibernateTable("application", "persons", "_uuid_id"),
            join =
                EffectiveHibernateJoinTable(
                    "viaduct_internal",
                    "group_members_associations",
                    "group_id",
                    "person_id",
                ),
        )

    private fun edgeField(
        name: String = "role",
        columnName: String = "role",
        nullable: Boolean = true,
    ) = EffectiveHibernateEdgeField(
        name = name,
        columnName = columnName,
        sqlType = "varchar(255)",
        nullable = nullable,
    )
}
