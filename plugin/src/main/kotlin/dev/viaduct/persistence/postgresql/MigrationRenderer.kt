package dev.viaduct.persistence.postgresql

import kotlin.reflect.KClass

/** Renders one concrete PostgreSQL migration operation type. */
internal interface MigrationRenderer<T : PostgresqlMigrationOperation> {
    val operationType: KClass<T>

    fun render(operation: T): String

    fun renderMatching(operation: PostgresqlMigrationOperation): String {
        require(operationType.isInstance(operation)) {
            "${this::class.simpleName} cannot render ${operation::class.simpleName}"
        }
        @Suppress("UNCHECKED_CAST")
        return render(operation as T)
    }
}

internal class MigrationRendererRegistry private constructor(
    private val renderersByOperationType: Map<KClass<out PostgresqlMigrationOperation>, MigrationRenderer<*>>,
) {
    fun render(operation: PostgresqlMigrationOperation): String {
        val renderer =
            requireNotNull(renderersByOperationType[operation::class]) {
                "No migration renderer registered for ${operation::class.simpleName}"
            }
        return renderer.renderMatching(operation)
    }

    companion object {
        fun create(
            renderers: List<MigrationRenderer<*>>,
            operationTypes: Set<KClass<out PostgresqlMigrationOperation>> = PostgresqlMigrationOperation.types,
        ): MigrationRendererRegistry {
            val byOperationType = renderers.groupBy(MigrationRenderer<*>::operationType)
            val duplicateTypes = byOperationType.filterValues { it.size > 1 }.keys
            require(duplicateTypes.isEmpty()) {
                "Multiple migration renderers registered for ${duplicateTypes.joinToString { it.simpleName.orEmpty() }}"
            }
            val missingTypes = operationTypes - byOperationType.keys
            require(missingTypes.isEmpty()) {
                "No migration renderers registered for ${missingTypes.joinToString { it.simpleName.orEmpty() }}"
            }
            val unexpectedTypes = byOperationType.keys - operationTypes
            require(unexpectedTypes.isEmpty()) {
                val names = unexpectedTypes.joinToString { it.simpleName.orEmpty() }
                "Unexpected migration renderers registered for $names"
            }
            return MigrationRendererRegistry(
                java.util.Map.copyOf(byOperationType.mapValues { it.value.single() }),
            )
        }
    }
}
