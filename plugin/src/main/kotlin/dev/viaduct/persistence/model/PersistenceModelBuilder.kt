package dev.viaduct.persistence.model

import viaduct.graphql.schema.ViaductSchema

data class PersistenceModelPolicy(
    val deniedTypeNames: Set<String> = emptySet(),
    val semanticNotNullTypeNames: Set<String> = emptySet(),
    val semanticNotNullFieldCoordinates: Set<String> = emptySet(),
    val unidirectionalTargetForeignKeyFields: Set<String> = emptySet(),
    val inverseFieldOverrides: Map<String, String> = emptyMap(),
)

class PersistenceModelBuilder {
    private val modelValidator = PersistenceModelValidator()
    private val entityAttributeFactory = PersistenceEntityAttributeFactory(modelValidator)

    fun build(
        schema: ViaductSchema,
        selectedTypeNames: Set<String>,
        policy: PersistenceModelPolicy = PersistenceModelPolicy(),
    ): PersistenceModel {
        val includedObjects = resolveIncludedObjects(schema, selectedTypeNames)
        val modelContext =
            PersistenceModelContext(
                includedObjects = includedObjects,
                schemaObjects =
                    schema.types.values
                        .filterIsInstance<ViaductSchema.Object>()
                        .associateBy(ViaductSchema.Object::name),
                policy = policy,
            )
        validateDeniedRelationships(schema, includedObjects, policy.deniedTypeNames)
        modelContext.validateSemanticNotNullCoordinates()
        modelValidator.validateTargetForeignKeyFields(modelContext)
        val entities =
            includedObjects.values
                .sortedBy { it.name }
                .map { type ->
                    entityAttributeFactory.build(
                        type = type,
                        generatedGlobalId = generatesGlobalId(type),
                        modelContext = modelContext,
                    )
                }

        return PersistenceModel(
            entities = entities,
            enums = modelContext.generatedEnums.values.sortedBy { it.graphqlName },
            semanticNotNullCoordinates = modelContext.semanticNotNullCoordinates(),
        )
    }

    private fun resolveIncludedObjects(
        schema: ViaductSchema,
        selectedTypeNames: Set<String>,
    ): Map<String, ViaductSchema.Object> =
        selectedTypeNames.associateWith { typeName ->
            schema.types[typeName] as? ViaductSchema.Object
                ?: error("Persistence type '$typeName' is not a GraphQL object")
        }

    private fun generatesGlobalId(type: ViaductSchema.Object): Boolean = type.supers.any { it.name == "Node" }

    private fun validateDeniedRelationships(
        schema: ViaductSchema,
        includedObjects: Map<String, ViaductSchema.Object>,
        deniedTypeNames: Set<String>,
    ) {
        val allObjects =
            schema.types.values
                .filterIsInstance<ViaductSchema.Object>()
                .associateBy { it.name }
        val resolver = RelationshipTargetResolverChain()
        includedObjects.values.forEach { type ->
            type.fields.forEach { field ->
                if (field.hasAppliedDirective("resolver")) return@forEach
                val targetName = resolver.resolve(field, allObjects)?.targetName
                require(targetName !in deniedTypeNames) {
                    "Persisted field '${type.name}.${field.name}' targets denied type '$targetName'"
                }
            }
        }
    }
}
