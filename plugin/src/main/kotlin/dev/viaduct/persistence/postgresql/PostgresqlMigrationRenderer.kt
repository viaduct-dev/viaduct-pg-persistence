package dev.viaduct.persistence.postgresql

import dev.viaduct.persistence.hibernate.EffectiveHibernateModel

/** Combines id-column and scalar-array migration statements. */
internal object PostgresqlMigrationRenderer {
    fun render(model: EffectiveHibernateModel): String =
        buildString {
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
