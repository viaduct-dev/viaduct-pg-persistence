package dev.viaduct.persistence.postgresql

import dev.viaduct.persistence.hibernate.EffectiveHibernateRelationship

internal data class ForeignKeySpec(
    val schemaName: String,
    val tableName: String,
    val columnName: String,
    val targetSchemaName: String,
    val targetTableName: String,
    val targetColumnName: String,
)

/** Creates the physical constraint omitted by Hibernate's dynamic-map HBM schema model. */
internal object ForeignKeyMigrationRenderer {
    private const val POSTGRESQL_IDENTIFIER_LIMIT = 63

    fun render(relationship: EffectiveHibernateRelationship): String =
        render(
            ForeignKeySpec(
                schemaName = relationship.schemaName,
                tableName = relationship.tableName,
                columnName = relationship.columnName,
                targetSchemaName = requireNotNull(relationship.targetSchemaName),
                targetTableName = requireNotNull(relationship.targetTableName),
                targetColumnName = requireNotNull(relationship.targetIdColumnName),
            ),
        )

    fun render(foreignKey: ForeignKeySpec): String =
        foreignKey.run {
            val constraintName = constraintName(tableName, columnName)
            """
            DO ${'$'}viaduct_foreign_key${'$'}
            BEGIN
              IF NOT EXISTS (
                SELECT 1
                  FROM pg_constraint constraint_def
                  JOIN pg_attribute column_def
                    ON column_def.attrelid = constraint_def.conrelid
                   AND column_def.attnum = ANY (constraint_def.conkey)
                 WHERE constraint_def.contype = 'f'
                   AND constraint_def.conrelid =
                       ${quoteLiteral("$schemaName.$tableName")}::regclass
                   AND column_def.attname = ${quoteLiteral(columnName)}
              ) THEN
                ALTER TABLE ${qualifiedTableName(schemaName, tableName)}
                  ADD CONSTRAINT ${quoteIdentifier(constraintName)}
                  FOREIGN KEY (${quoteIdentifier(columnName)})
                  REFERENCES ${qualifiedTableName(targetSchemaName, targetTableName)} (${quoteIdentifier(targetColumnName)});
              END IF;
            END
            ${'$'}viaduct_foreign_key${'$'};
            """.trimIndent()
        }

    private fun constraintName(
        tableName: String,
        columnName: String,
    ): String {
        val suffix = "_${columnName}_fkey"
        val maximumTableLength = (POSTGRESQL_IDENTIFIER_LIMIT - suffix.length).coerceAtLeast(0)
        return tableName.take(maximumTableLength) + suffix
    }
}
