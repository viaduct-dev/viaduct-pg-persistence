package dev.viaduct.persistence.postgresql

import dev.viaduct.persistence.hibernate.EffectiveHibernateComputedRelationship
import dev.viaduct.persistence.hibernate.EffectiveHibernateEdgeField

/** Adds association payload columns that are not part of a many-to-many JPA join mapping. */
internal object EdgeFieldMigrationRenderer {
    fun render(
        relationship: EffectiveHibernateComputedRelationship,
        field: EffectiveHibernateEdgeField,
    ): String =
        if (field.collection != null) {
            ""
        } else {
            val column = renderColumn(relationship, field)
            val foreignKey = renderForeignKey(relationship, field)
            listOfNotNull(column, foreignKey).joinToString("\n")
        }

    private fun renderColumn(
        relationship: EffectiveHibernateComputedRelationship,
        field: EffectiveHibernateEdgeField,
    ): String =
        """
        DO ${'$'}viaduct_edge_field${'$'}
        BEGIN
          IF NOT EXISTS (
            SELECT 1
              FROM information_schema.columns
             WHERE table_schema = ${quoteLiteral(relationship.joinSchemaName)}
               AND table_name = ${quoteLiteral(relationship.joinTableName)}
               AND column_name = ${quoteLiteral(field.columnName)}
          ) THEN
            ALTER TABLE ${qualifiedTableName(relationship.joinSchemaName, relationship.joinTableName)}
              ADD COLUMN ${quoteIdentifier(field.columnName)} ${field.sqlType}${if (field.nullable) "" else " NOT NULL"};
          END IF;
        END
        ${'$'}viaduct_edge_field${'$'};
        """.trimIndent()

    private fun renderForeignKey(
        relationship: EffectiveHibernateComputedRelationship,
        field: EffectiveHibernateEdgeField,
    ): String? {
        val targetSchema = field.targetSchemaName
        val targetTable = field.targetTableName
        val targetIdColumn = field.targetIdColumnName
        return if (targetSchema == null || targetTable == null || targetIdColumn == null) {
            null
        } else {
            """
            DO ${'$'}viaduct_edge_field_fk${'$'}
            BEGIN
              IF NOT EXISTS (
                SELECT 1
                  FROM pg_constraint constraint_def
                  JOIN pg_attribute column_def
                    ON column_def.attrelid = constraint_def.conrelid
                   AND column_def.attnum = ANY (constraint_def.conkey)
                 WHERE constraint_def.contype = 'f'
                   AND constraint_def.conrelid =
                       ${quoteLiteral(qualifiedTableName(relationship.joinSchemaName, relationship.joinTableName))}::regclass
                   AND column_def.attname = ${quoteLiteral(field.columnName)}
                   AND constraint_def.confrelid =
                       ${quoteLiteral(qualifiedTableName(targetSchema, targetTable))}::regclass
              ) THEN
                ALTER TABLE ${qualifiedTableName(relationship.joinSchemaName, relationship.joinTableName)}
                  ADD CONSTRAINT ${quoteIdentifier(edgeFieldForeignKeyName(relationship.joinTableName, field.columnName))}
                  FOREIGN KEY (${quoteIdentifier(field.columnName)})
                  REFERENCES ${qualifiedTableName(targetSchema, targetTable)} (${quoteIdentifier(targetIdColumn)});
              END IF;
            END
            ${'$'}viaduct_edge_field_fk${'$'};
            """.trimIndent()
        }
    }
}
