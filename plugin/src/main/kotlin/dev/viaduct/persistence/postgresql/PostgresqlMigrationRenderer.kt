package dev.viaduct.persistence.postgresql

import dev.viaduct.persistence.hibernate.EffectiveHibernateModel

/** Combines id-column and scalar-array migration statements. */
internal object PostgresqlMigrationRenderer {
    fun render(model: EffectiveHibernateModel): String = render(EffectiveModelToMigrationPlanMapper.map(model))

    internal fun render(plan: PostgresqlMigrationPlan): String =
        buildString {
            plan.operations.forEach { operation ->
                appendLine(
                    when (operation) {
                        is PostgresqlMigrationOperation.AddEdgeField ->
                            EdgeFieldMigrationRenderer.render(operation.field)
                        is PostgresqlMigrationOperation.AddForeignKey ->
                            ForeignKeyMigrationRenderer.render(operation.foreignKey)
                        is PostgresqlMigrationOperation.AddGlobalId ->
                            GlobalIdMigrationRenderer.render(operation.globalId)
                        is PostgresqlMigrationOperation.AddArrayCheck ->
                            ArrayConstraintMigrationRenderer.render(operation.check)
                    },
                )
            }
        }
}
