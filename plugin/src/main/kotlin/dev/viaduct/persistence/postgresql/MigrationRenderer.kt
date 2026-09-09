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
