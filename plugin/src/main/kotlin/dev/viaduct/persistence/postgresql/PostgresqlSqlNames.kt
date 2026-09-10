package dev.viaduct.persistence.postgresql

private const val POSTGRES_IDENTIFIER_MAX_LENGTH = 63

internal fun qualifiedTableName(
    schemaName: String,
    tableName: String,
): String = "${quoteIdentifier(schemaName)}.${quoteIdentifier(tableName)}"

internal fun quoteIdentifier(value: String): String = "\"${value.replace("\"", "\"\"")}\""

internal fun quoteLiteral(value: String): String = "'${value.replace("'", "''")}'"

internal fun arrayCheckConstraintName(array: dev.viaduct.persistence.hibernate.EffectiveHibernateArray): String =
    "viaduct_${array.tableName}_${array.columnName}_no_null_elements"
        .replace(Regex("[^A-Za-z0-9_]"), "_")
        .take(POSTGRES_IDENTIFIER_MAX_LENGTH)

internal fun edgeFieldForeignKeyName(
    tableName: String,
    columnName: String,
): String =
    "viaduct_${tableName}_${columnName}_fkey"
        .replace(Regex("[^A-Za-z0-9_]"), "_")
        .take(POSTGRES_IDENTIFIER_MAX_LENGTH)

internal fun dev.viaduct.persistence.hibernate.EffectiveHibernateEntity.qualifiedTableName(): String =
    qualifiedTableName(schemaName, tableName)
