package dev.viaduct.persistence.postgresql

/** Renders the id-column migration for generated global identifiers. */
internal object GlobalIdMigrationRenderer :
    MigrationRenderer<PostgresqlMigrationOperation.AddGlobalId> {
    override val operationType = PostgresqlMigrationOperation.AddGlobalId::class

    override fun render(operation: PostgresqlMigrationOperation.AddGlobalId): String {
        val entity = operation.globalId
        val internalIdColumn = entity.internalIdColumnName
        val globalIdColumn = entity.globalIdColumnName
        val schemaLiteral = quoteLiteral(entity.schemaName)
        val tableLiteral = quoteLiteral(entity.tableName)
        val columnLiteral = quoteLiteral(globalIdColumn)
        val globalIdBytes = globalIdByteaExpression(entity.graphqlName, internalIdColumn)
        return """
            DO ${'$'}viaduct_global_id${'$'}
            BEGIN
              IF EXISTS (
                SELECT 1
                  FROM information_schema.columns
                 WHERE table_schema = $schemaLiteral
                   AND table_name = $tableLiteral
                   AND column_name = $columnLiteral
                   AND is_generated = 'NEVER'
              ) THEN
                ALTER TABLE ${qualifiedTableName(entity.schemaName, entity.tableName)}
                  DROP COLUMN ${quoteIdentifier(globalIdColumn)};
              END IF;
              IF NOT EXISTS (
                SELECT 1
                  FROM information_schema.columns
                 WHERE table_schema = $schemaLiteral
                   AND table_name = $tableLiteral
                   AND column_name = $columnLiteral
              ) THEN
                ALTER TABLE ${qualifiedTableName(entity.schemaName, entity.tableName)}
                  ADD COLUMN ${quoteIdentifier(globalIdColumn)} TEXT GENERATED ALWAYS AS (
                    replace(
                      encode(
                        $globalIdBytes,
                        'base64'
                      ),
                      chr(10),
                      ''
                    )
                  ) STORED NOT NULL;
              END IF;
            END
            ${'$'}viaduct_global_id${'$'};
            """.trimIndent()
    }

    private fun globalIdByteaExpression(
        graphqlName: String,
        internalIdColumn: String,
    ): String {
        val prefixHex =
            "$graphqlName:"
                .encodeToByteArray()
                .joinToString(separator = "") { byte ->
                    byte.toUByte().toString(radix = 16).padStart(2, '0')
                }
        val uuidCharacters = "0123456789abcdef-"
        val placeholders = "GHIJKLMNOPQRSTUVW"
        val translatedUuid =
            """
            translate(
              ${quoteIdentifier(internalIdColumn)}::text,
              '$uuidCharacters',
              '$placeholders'
            )
            """.trimIndent()
        val uuidHex =
            uuidCharacters.zip(placeholders).fold(translatedUuid) { expression, (character, placeholder) ->
                val characterHex = character.code.toString(radix = 16).padStart(2, '0')
                "replace($expression, '$placeholder', '$characterHex')"
            }
        return "decode(${quoteLiteral(prefixHex)} || $uuidHex, 'hex')"
    }
}
