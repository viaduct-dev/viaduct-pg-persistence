package dev.viaduct.persistence.postgresql

import dev.viaduct.persistence.hibernate.EffectiveHibernateModel

/** Combines id-column and scalar-array migration statements. */
internal object PostgresqlMigrationRenderer {
    fun render(model: EffectiveHibernateModel): String =
        buildString {
            model.computedRelationships.forEach { relationship ->
                relationship.edgeFields.forEach { field ->
                    appendLine(EdgeFieldMigrationRenderer.render(relationship, field))
                }
            }
            model.relationships
                .distinctBy { listOf(it.schemaName, it.tableName, it.columnName) }
                .forEach { appendLine(ForeignKeyMigrationRenderer.render(it)) }
            model.computedRelationships.forEach { relationship ->
                appendLine(
                    ForeignKeyMigrationRenderer.render(
                        ForeignKeySpec(
                            relationship.joinSchemaName,
                            relationship.joinTableName,
                            relationship.joinOwnerColumnName,
                            relationship.ownerSchemaName,
                            relationship.ownerTableName,
                            relationship.ownerIdColumnName,
                        ),
                    ),
                )
                appendLine(
                    ForeignKeyMigrationRenderer.render(
                        ForeignKeySpec(
                            relationship.joinSchemaName,
                            relationship.joinTableName,
                            relationship.joinTargetColumnName,
                            relationship.targetSchemaName,
                            relationship.targetTableName,
                            relationship.targetIdColumnName,
                        ),
                    ),
                )
                relationship.edgeFields.filter { it.targetTableName != null }.forEach { field ->
                    appendLine(
                        ForeignKeyMigrationRenderer.render(
                            ForeignKeySpec(
                                relationship.joinSchemaName,
                                relationship.joinTableName,
                                field.columnName,
                                requireNotNull(field.targetSchemaName),
                                requireNotNull(field.targetTableName),
                                requireNotNull(field.targetIdColumnName),
                            ),
                        ),
                    )
                }
            }
            model.entities.filter { it.generatedGlobalId }.forEach {
                appendLine(GlobalIdMigrationRenderer.render(it))
            }
            model.arrays.filterNot { it.elementNullable }.forEach {
                appendLine(ArrayConstraintMigrationRenderer.render(it))
            }
            model.computedRelationships.forEach { relationship ->
                relationship.edgeFields.forEach { field ->
                    EdgeFieldMigrationRenderer.render(relationship, field).takeIf(String::isNotEmpty)?.let {
                        appendLine(it)
                    }
                }
            }
        }
}
