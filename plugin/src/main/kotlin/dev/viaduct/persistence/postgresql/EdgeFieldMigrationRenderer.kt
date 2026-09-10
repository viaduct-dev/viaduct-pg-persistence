package dev.viaduct.persistence.postgresql

/** Adds association payload columns that are not part of a many-to-many Hibernate join mapping. */
internal object EdgeFieldMigrationRenderer :
    MigrationRenderer<PostgresqlMigrationOperation.AddEdgeField> {
    override val operationType = PostgresqlMigrationOperation.AddEdgeField::class

    override fun render(operation: PostgresqlMigrationOperation.AddEdgeField): String =
        operation.field.run {
            """
            DO ${'$'}viaduct_edge_field${'$'}
            BEGIN
              IF NOT EXISTS (
                SELECT 1
                  FROM information_schema.columns
                 WHERE table_schema = ${quoteLiteral(schemaName)}
                   AND table_name = ${quoteLiteral(tableName)}
                   AND column_name = ${quoteLiteral(columnName)}
              ) THEN
                ALTER TABLE ${qualifiedTableName(schemaName, tableName)}
                  ADD COLUMN ${quoteIdentifier(columnName)} $sqlType${if (nullable) "" else " NOT NULL"};
              END IF;
            END
            ${'$'}viaduct_edge_field${'$'};
            """.trimIndent()
        }
}
