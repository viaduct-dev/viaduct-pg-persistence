package dev.viaduct.persistence.postgresql

/** Adds association payload columns that are not part of a many-to-many Hibernate join mapping. */
internal object EdgeFieldMigrationRenderer {
    fun render(field: EdgeFieldSpec): String =
        """
        DO ${'$'}viaduct_edge_field${'$'}
        BEGIN
          IF NOT EXISTS (
            SELECT 1
              FROM information_schema.columns
             WHERE table_schema = ${quoteLiteral(field.schemaName)}
               AND table_name = ${quoteLiteral(field.tableName)}
               AND column_name = ${quoteLiteral(field.columnName)}
          ) THEN
            ALTER TABLE ${qualifiedTableName(field.schemaName, field.tableName)}
              ADD COLUMN ${quoteIdentifier(field.columnName)} ${field.sqlType}${if (field.nullable) "" else " NOT NULL"};
          END IF;
        END
        ${'$'}viaduct_edge_field${'$'};
        """.trimIndent()

}
