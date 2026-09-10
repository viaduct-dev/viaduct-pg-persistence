package dev.viaduct.persistence.runtime.db

import graphql.schema.GraphQLInputObjectType
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import viaduct.api.types.Input

internal fun Input.mutationValues(
    entityName: String,
    identifierField: String? = null,
): JsonObject {
    val values = toPgGraphqlInput()
    val identifierName = identifierName(entityName, identifierField)
    require(values.containsKey(identifierName)) {
        "Mutation input does not contain identifier field '$identifierName'"
    }
    return JsonObject(values.filterKeys { it != identifierName })
}

internal fun Input.mutationIdentifiers(
    entityName: String,
    identifierField: String? = null,
): JsonObject {
    val values = toPgGraphqlInput()
    val identifierName = identifierName(entityName, identifierField)
    val identifier =
        requireNotNull(values[identifierName]) {
            "Mutation input does not contain identifier field '$identifierName'"
        }
    return buildJsonObject { put("uuidId", buildJsonObject { put("eq", identifier) }) }
}

private fun Input.identifierName(
    entityName: String,
    explicitField: String?,
): String {
    val candidates =
        buildSet {
            graphQLInputType().fieldDefinitions.forEach { field ->
                if (field.getAppliedDirective("idOf")?.getArgument("type")?.getValue<String>() == entityName) {
                    add(field.name)
                }
            }
        }
    if (explicitField != null) {
        require(explicitField in candidates) {
            "Identifier field '$explicitField' must have @idOf(type: \"$entityName\")"
        }
        return explicitField
    }
    require(candidates.isNotEmpty()) { "Mutation input has no @idOf field for $entityName" }
    require(candidates.size == 1) {
        "Mutation input has multiple @idOf fields for $entityName: ${candidates.joinToString()}. " +
            "Pass identifierField explicitly."
    }
    return candidates.single()
}

private fun Input.graphQLInputType(): GraphQLInputObjectType =
    javaClass.getMethod("getGraphQLInputObjectType").invoke(this) as GraphQLInputObjectType
