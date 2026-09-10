package dev.viaduct.persistence.hibernate

class EffectiveHibernateModel(
    entities: List<EffectiveHibernateEntity>,
    relationships: List<EffectiveHibernateRelationship>,
    computedRelationships: List<EffectiveHibernateComputedRelationship>,
    arrays: List<EffectiveHibernateArray>,
) {
    val entities: List<EffectiveHibernateEntity> = java.util.List.copyOf(entities)
    val relationships: List<EffectiveHibernateRelationship> = java.util.List.copyOf(relationships)
    val computedRelationships: List<EffectiveHibernateComputedRelationship> =
        java.util.List.copyOf(computedRelationships)
    val arrays: List<EffectiveHibernateArray> = java.util.List.copyOf(arrays)

    override fun equals(other: Any?): Boolean =
        other is EffectiveHibernateModel &&
            entities == other.entities &&
            relationships == other.relationships &&
            computedRelationships == other.computedRelationships &&
            arrays == other.arrays

    override fun hashCode(): Int {
        var result = entities.hashCode()
        result = 31 * result + relationships.hashCode()
        result = 31 * result + computedRelationships.hashCode()
        result = 31 * result + arrays.hashCode()
        return result
    }

    override fun toString(): String =
        "EffectiveHibernateModel(" +
            "entities=$entities, " +
            "relationships=$relationships, " +
            "computedRelationships=$computedRelationships, " +
            "arrays=$arrays)"
}

data class EffectiveHibernateEntity(
    val graphqlName: String,
    val schemaName: String,
    val tableName: String,
    val generatedGlobalId: Boolean,
    val internalIdColumnName: String?,
    val globalIdColumnName: String?,
)

data class EffectiveHibernateRelationship(
    val ownerTypeName: String,
    val fieldName: String,
    val schemaName: String,
    val tableName: String,
    val columnName: String,
    val graphqlNameKind: GraphqlNameKind,
    /** Populated for [GraphqlNameKind.FOREIGN]: the table the foreign key points at. */
    val targetSchemaName: String? = null,
    val targetTableName: String? = null,
    val targetIdColumnName: String? = null,
)

data class EffectiveHibernateArray(
    val ownerTypeName: String,
    val fieldName: String,
    val schemaName: String,
    val tableName: String,
    val columnName: String,
    val elementNullable: Boolean,
)

class EffectiveHibernateComputedRelationship(
    val ownerTypeName: String,
    val fieldName: String,
    val owner: EffectiveHibernateTable,
    val target: EffectiveHibernateTable,
    val join: EffectiveHibernateJoinTable,
    edgeFields: List<EffectiveHibernateEdgeField> = emptyList(),
) {
    val edgeFields: List<EffectiveHibernateEdgeField> = java.util.List.copyOf(edgeFields)

    val ownerSchemaName: String get() = owner.schemaName
    val ownerTableName: String get() = owner.tableName
    val ownerIdColumnName: String get() = owner.idColumnName
    val targetSchemaName: String get() = target.schemaName
    val targetTableName: String get() = target.tableName
    val targetIdColumnName: String get() = target.idColumnName
    val joinSchemaName: String get() = join.schemaName
    val joinTableName: String get() = join.tableName
    val joinOwnerColumnName: String get() = join.ownerColumnName
    val joinTargetColumnName: String get() = join.targetColumnName

    override fun equals(other: Any?): Boolean =
        other is EffectiveHibernateComputedRelationship &&
            ownerTypeName == other.ownerTypeName &&
            fieldName == other.fieldName &&
            owner == other.owner &&
            target == other.target &&
            join == other.join &&
            edgeFields == other.edgeFields

    override fun hashCode(): Int =
        listOf(
            ownerTypeName,
            fieldName,
            owner,
            target,
            join,
            edgeFields,
        ).hashCode()
}

data class EffectiveHibernateTable(
    val schemaName: String,
    val tableName: String,
    val idColumnName: String,
)

data class EffectiveHibernateJoinTable(
    val schemaName: String,
    val tableName: String,
    val ownerColumnName: String,
    val targetColumnName: String,
)

data class EffectiveHibernateEdgeField(
    val name: String,
    val columnName: String,
    val sqlType: String,
    val nullable: Boolean,
    val targetSchemaName: String? = null,
    val targetTableName: String? = null,
    val targetIdColumnName: String? = null,
    val collection: EffectiveHibernateEdgeCollection? = null,
)

data class EffectiveHibernateEdgeCollection(
    val target: EffectiveHibernateTable,
    val ownerColumnName: String,
    val join: EffectiveHibernateJoinTable? = null,
)

enum class GraphqlNameKind {
    FOREIGN,
    LOCAL,
    NONE,
}
