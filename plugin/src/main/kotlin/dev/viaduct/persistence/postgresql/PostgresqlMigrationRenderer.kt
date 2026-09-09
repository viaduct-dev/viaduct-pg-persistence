package dev.viaduct.persistence.postgresql

import dev.viaduct.persistence.hibernate.EffectiveHibernateModel

/** Renders an ordered migration plan by dispatching each operation to its registered renderer. */
internal object PostgresqlMigrationRenderer {
    private val renderersByOperationType =
        listOf(
            EdgeFieldMigrationRenderer,
            ForeignKeyMigrationRenderer,
            GlobalIdMigrationRenderer,
            ArrayConstraintMigrationRenderer,
        ).associateBy(MigrationRenderer<*>::operationType)

    fun render(model: EffectiveHibernateModel): String = render(EffectiveModelToMigrationPlanMapper.map(model))

    internal fun render(plan: PostgresqlMigrationPlan): String =
        buildString {
            plan.operations.forEach { operation ->
                val renderer =
                    requireNotNull(renderersByOperationType[operation::class]) {
                        "No migration renderer registered for ${operation::class.simpleName}"
                    }
                appendLine(renderer.renderMatching(operation))
            }
        }
}
