package dev.viaduct.persistence.runtime.db

import graphql.schema.GraphQLInputObjectType
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import viaduct.api.types.Input

internal fun Input.mutationValues(entityName: String): JsonObject {
    val values = toPgGraphqlInput()
    val identifierNames = identifierNames(entityName)
    return JsonObject(values.filterKeys { it !in identifierNames })
}

internal fun Input.mutationIdentifiers(entityName: String): JsonObject {
    val values = toPgGraphqlInput()
    val identifierNames = identifierNames(entityName)
    return JsonObject(values.filterKeys { it in identifierNames })
}

private fun Input.identifierNames(entityName: String): Set<String> =
    buildSet {
        graphQLInputType().fieldDefinitions.forEach { field ->
            if (field.getAppliedDirective("idOf")?.getArgument("type")?.getValue<String>() == entityName) {
                add(field.name)
            }
        }
    }

private fun Input.graphQLInputType(): GraphQLInputObjectType =
    javaClass.getMethod("getGraphQLInputObjectType").invoke(this) as GraphQLInputObjectType

internal fun JsonObject.equalityFilter(): JsonObject =
    buildJsonObject {
        this@equalityFilter.forEach { (field, value) ->
            val databaseField = if (field.endsWith("Id")) "uuidId" else field
            put(databaseField, buildJsonObject { put("eq", value) })
        }
    }
