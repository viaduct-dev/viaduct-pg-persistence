package dev.viaduct.persistence.model

import viaduct.graphql.schema.ViaductSchema

internal data class PersistenceRelationshipTarget(
    val targetName: String,
    val collection: Boolean,
    val edgeTypeName: String? = null,
    /** True when this target was resolved from `@idOf` on a scalar `ID` field, not an object field. */
    val idOfDirected: Boolean = false,
)

internal interface RelationshipTargetResolver {
    fun resolve(
        field: ViaductSchema.Field,
        includedObjects: Map<String, ViaductSchema.Object>,
    ): PersistenceRelationshipTarget?
}

internal class RelationshipTargetResolverChain(
    private val resolvers: List<RelationshipTargetResolver> =
        listOf(
            DirectRelationshipTargetResolver(),
            ConnectionRelationshipTargetResolver(),
            NodesCollectionRelationshipTargetResolver(),
            IdOfRelationshipTargetResolver(),
        ),
) : RelationshipTargetResolver {
    override fun resolve(
        field: ViaductSchema.Field,
        includedObjects: Map<String, ViaductSchema.Object>,
    ): PersistenceRelationshipTarget? = resolvers.firstNotNullOfOrNull { it.resolve(field, includedObjects) }
}

private class DirectRelationshipTargetResolver : RelationshipTargetResolver {
    override fun resolve(
        field: ViaductSchema.Field,
        includedObjects: Map<String, ViaductSchema.Object>,
    ): PersistenceRelationshipTarget? =
        (field.type.baseTypeDef as? ViaductSchema.Object)
            ?.takeIf { it.name in includedObjects }
            ?.let { PersistenceRelationshipTarget(it.name, field.type.isList) }
}

private class ConnectionRelationshipTargetResolver : RelationshipTargetResolver {
    override fun resolve(
        field: ViaductSchema.Field,
        includedObjects: Map<String, ViaductSchema.Object>,
    ): PersistenceRelationshipTarget? =
        (field.type.baseTypeDef as? ViaductSchema.Object)
            ?.let(::connectionTypes)
            ?.takeIf { it.node.name in includedObjects }
            ?.let {
                PersistenceRelationshipTarget(
                    targetName = it.node.name,
                    collection = true,
                    edgeTypeName = it.edge.name,
                )
            }

    private fun connectionTypes(connectionType: ViaductSchema.Object): ConnectionTypes? {
        val edge =
            connectionType.fields
                .singleOrNull { it.name == "edges" }
                ?.type
                ?.baseTypeDef
                ?.let { it as? ViaductSchema.Object }
        val node =
            edge
                ?.fields
                ?.singleOrNull { it.name == "node" }
                ?.type
                ?.baseTypeDef as? ViaductSchema.Object
        return edge?.let { edgeType -> node?.let { nodeType -> ConnectionTypes(edgeType, nodeType) } }
    }

    private data class ConnectionTypes(
        val edge: ViaductSchema.Object,
        val node: ViaductSchema.Object,
    )
}

private class NodesCollectionRelationshipTargetResolver : RelationshipTargetResolver {
    override fun resolve(
        field: ViaductSchema.Field,
        includedObjects: Map<String, ViaductSchema.Object>,
    ): PersistenceRelationshipTarget? =
        (field.type.baseTypeDef as? ViaductSchema.Object)
            ?.takeUnless(::isStructuralConnection)
            ?.fields
            ?.singleOrNull { it.name == "nodes" }
            ?.type
            ?.baseTypeDef
            ?.let { it as? ViaductSchema.Object }
            ?.takeIf { it.name in includedObjects }
            ?.let { PersistenceRelationshipTarget(it.name, collection = true) }
}

/**
 * Resolves a scalar `ID` field carrying `@idOf(type: "Target")` as a to-one relationship to
 * `Target`, letting `@idOf` direct a foreign key without an object-typed reference field.
 */
private class IdOfRelationshipTargetResolver : RelationshipTargetResolver {
    override fun resolve(
        field: ViaductSchema.Field,
        includedObjects: Map<String, ViaductSchema.Object>,
    ): PersistenceRelationshipTarget? {
        val baseType = field.type.baseTypeDef
        val isScalarId = !field.type.isList && baseType is ViaductSchema.Scalar && baseType.name == "ID"
        val targetName = if (isScalarId) field.idOfTypeName() else null
        return targetName?.takeIf { it in includedObjects }?.let {
            PersistenceRelationshipTarget(targetName = it, collection = false, idOfDirected = true)
        }
    }
}

private fun ViaductSchema.Field.idOfTypeName(): String? =
    appliedDirectives
        .firstOrNull { it.name == "idOf" }
        ?.arguments
        ?.get("type")
        ?.let { it as? ViaductSchema.StringLiteral }
        ?.value

private fun isStructuralConnection(type: ViaductSchema.Object): Boolean =
    (
        type.fields
            .singleOrNull { it.name == "edges" }
            ?.type
            ?.baseTypeDef
            ?.let { it as? ViaductSchema.Object }
            ?.fields
            ?.any { it.name == "node" }
            == true
    )

internal data class PersistenceCollectionMapping(
    val inverseFieldName: String?,
    val storage: PersistenceToManyStorage,
    val joinTableName: String? = null,
)

internal data class CollectionMappingContext(
    val source: ViaductSchema.Object,
    val sourceField: ViaductSchema.Field,
    val target: ViaductSchema.Object,
    val sourceCollections: List<ViaductSchema.Field>,
    val inverseToOneFields: List<ViaductSchema.Field>,
    val inverseCollections: List<ViaductSchema.Field>,
    val unidirectionalTargetForeignKeyFields: Set<String>,
    val hasPersistedEdgeFields: Boolean = false,
    val sourceEdgeMappings: Map<String, PersistenceEdgeMapping?> = emptyMap(),
    val inverseEdgeMappings: Map<String, PersistenceEdgeMapping?> = emptyMap(),
)

internal interface CollectionMappingStrategy {
    fun resolve(context: CollectionMappingContext): PersistenceCollectionMapping?
}

internal class CollectionMappingResolver(
    private val strategies: List<CollectionMappingStrategy> =
        listOf(
            EdgeCollectionMappingStrategy(),
            InverseToOneCollectionMappingStrategy(),
            MutualCollectionMappingStrategy(),
            AmbiguousInverseCollectionStrategy(),
            ConfiguredTargetForeignKeyStrategy(),
            SingleUnidirectionalCollectionStrategy(),
            MultipleUnidirectionalCollectionStrategy(),
            FallbackJoinTableStrategy(),
        ),
) {
    fun resolve(context: CollectionMappingContext): PersistenceCollectionMapping =
        checkNotNull(strategies.firstNotNullOfOrNull { it.resolve(context) }) {
            "No collection mapping strategy matched ${context.source.name}.${context.sourceField.name}"
        }
}

private class InverseToOneCollectionMappingStrategy : CollectionMappingStrategy {
    override fun resolve(context: CollectionMappingContext): PersistenceCollectionMapping? {
        if (context.hasPersistedEdgeFields) return null
        require(context.inverseToOneFields.size <= 1) {
            val coordinate = "${context.source.name}.${context.sourceField.name}"
            val candidates = context.inverseToOneFields.joinToString { it.name }
            "Relationship $coordinate -> ${context.target.name} is ambiguous because " +
                "${context.target.name} has multiple references back to ${context.source.name}: " +
                "$candidates. Specify which one $coordinate is the inverse of by adding " +
                "an inverseFieldOverrides entry to persistence.yaml " +
                "(viaductPgPersistence.persistenceConfigFile), e.g.:\n" +
                "relationships:\n  inverseFieldOverrides:\n    $coordinate: <fieldName>\n" +
                "using one of: $candidates."
        }
        return context.inverseToOneFields.singleOrNull()?.let { inverse ->
            require(context.sourceCollections.size == 1) {
                "Relationship ${context.source.name} -> ${context.target.name} is ambiguous because " +
                    "multiple collections would share ${context.target.name}.${inverse.name}: " +
                    context.sourceCollections.joinToString { it.name }
            }
            PersistenceCollectionMapping(
                inverseFieldName = inverse.name,
                storage = PersistenceToManyStorage.TARGET_FOREIGN_KEY,
            )
        }
    }
}

private class EdgeCollectionMappingStrategy : CollectionMappingStrategy {
    override fun resolve(context: CollectionMappingContext): PersistenceCollectionMapping? {
        val sourceHasEdgeFields =
            context.hasPersistedEdgeFields || context.sourceEdgeMappings[context.sourceField.name] != null
        val inverseEdgeFields =
            context.inverseCollections.filter { context.inverseEdgeMappings[it.name] != null }
        return when {
            !sourceHasEdgeFields && inverseEdgeFields.isEmpty() -> null
            sourceHasEdgeFields ->
                PersistenceCollectionMapping(
                    inverseFieldName = null,
                    storage = PersistenceToManyStorage.JOIN_TABLE_OWNER,
                    joinTableName = associationJoinTableName(context.source.name, context.sourceField.name),
                )
            else -> {
                require(inverseEdgeFields.size == 1) {
                    "Relationship ${context.source.name}.${context.sourceField.name} is ambiguous because " +
                        "${context.target.name} has multiple association-backed collections back: " +
                        inverseEdgeFields.joinToString { it.name }
                }
                val inverse = inverseEdgeFields.single()
                PersistenceCollectionMapping(
                    inverseFieldName = inverse.name,
                    storage = PersistenceToManyStorage.JOIN_TABLE_INVERSE,
                    joinTableName = associationJoinTableName(context.target.name, inverse.name),
                )
            }
        }
    }
}

private class MutualCollectionMappingStrategy : CollectionMappingStrategy {
    override fun resolve(context: CollectionMappingContext): PersistenceCollectionMapping? {
        val hasEdgeFields =
            context.sourceEdgeMappings.values.any { it != null } ||
                context.inverseEdgeMappings.values.any { it != null }
        return when {
            hasEdgeFields ||
                context.sourceCollections.size != 1 ||
                context.inverseCollections.size != 1 -> null
            else -> {
                val inverseField = context.inverseCollections.single()
                val sourceKey = "${context.source.name}.${context.sourceField.name}"
                val targetKey = "${context.target.name}.${inverseField.name}"
                val sourceOwns = sourceKey <= targetKey
                val ownerType = if (sourceOwns) context.source else context.target
                val ownerField = if (sourceOwns) context.sourceField else inverseField
                PersistenceCollectionMapping(
                    inverseFieldName = if (sourceOwns) null else inverseField.name,
                    storage =
                        if (sourceOwns) {
                            PersistenceToManyStorage.JOIN_TABLE_OWNER
                        } else {
                            PersistenceToManyStorage.JOIN_TABLE_INVERSE
                        },
                    joinTableName = associationJoinTableName(ownerType.name, ownerField.name),
                )
            }
        }
    }
}

private class AmbiguousInverseCollectionStrategy : CollectionMappingStrategy {
    override fun resolve(context: CollectionMappingContext): PersistenceCollectionMapping? {
        require(context.inverseCollections.isEmpty()) {
            "Relationship ${context.source.name}.${context.sourceField.name} -> " +
                "${context.target.name} is ambiguous because ${context.target.name} has " +
                "multiple collection references back: " +
                context.inverseCollections.joinToString { it.name }
        }
        return null
    }
}

private class ConfiguredTargetForeignKeyStrategy : CollectionMappingStrategy {
    override fun resolve(context: CollectionMappingContext): PersistenceCollectionMapping? {
        val sourceKey = "${context.source.name}.${context.sourceField.name}"
        if (sourceKey !in context.unidirectionalTargetForeignKeyFields) return null
        require(!context.hasPersistedEdgeFields) {
            "Configured target-foreign-key relationship $sourceKey cannot persist custom edge fields; " +
                "remove the configuration so an association table can be used"
        }
        require(context.sourceCollections.size == 1) {
            "Configured target-foreign-key relationship $sourceKey must be the only collection " +
                "from ${context.source.name} to ${context.target.name}"
        }
        return PersistenceCollectionMapping(
            inverseFieldName = null,
            storage = PersistenceToManyStorage.TARGET_FOREIGN_KEY,
        )
    }
}

private class SingleUnidirectionalCollectionStrategy : CollectionMappingStrategy {
    override fun resolve(context: CollectionMappingContext): PersistenceCollectionMapping? =
        if (context.sourceCollections.size == 1 && !context.hasPersistedEdgeFields) {
            PersistenceCollectionMapping(
                inverseFieldName = null,
                storage = PersistenceToManyStorage.TARGET_FOREIGN_KEY,
            )
        } else if (context.sourceCollections.size == 1) {
            PersistenceCollectionMapping(
                inverseFieldName = null,
                storage = PersistenceToManyStorage.JOIN_TABLE_OWNER,
                joinTableName = associationJoinTableName(context.source.name, context.sourceField.name),
            )
        } else {
            null
        }
}

private class MultipleUnidirectionalCollectionStrategy : CollectionMappingStrategy {
    override fun resolve(context: CollectionMappingContext): PersistenceCollectionMapping? =
        if (context.sourceCollections.size > 1) {
            PersistenceCollectionMapping(
                inverseFieldName = null,
                storage = PersistenceToManyStorage.JOIN_TABLE_OWNER,
                joinTableName =
                    associationJoinTableName(
                        context.source.name,
                        context.sourceField.name,
                    ),
            )
        } else {
            null
        }
}

private class FallbackJoinTableStrategy : CollectionMappingStrategy {
    override fun resolve(context: CollectionMappingContext): PersistenceCollectionMapping =
        PersistenceCollectionMapping(
            inverseFieldName = null,
            storage = PersistenceToManyStorage.JOIN_TABLE_OWNER,
            joinTableName =
                associationJoinTableName(
                    context.source.name,
                    context.sourceField.name,
                ),
        )
}
