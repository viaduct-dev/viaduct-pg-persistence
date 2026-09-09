package dev.viaduct.persistence.runtime.db

import dev.viaduct.persistence.runtime.graphql.GraphqlQuery
import dev.viaduct.persistence.runtime.graphql.PgGraphqlTransport
import io.ktor.client.HttpClient
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import viaduct.api.globalid.GlobalID
import viaduct.api.types.Input

/**
 * Executes pg_graphql's generated Relay-style insert, update, and delete operations.
 *
 * This client is independent of a Viaduct [viaduct.api.context.ExecutionContext]. Applications
 * pass authentication headers for each call, which makes it suitable for mutation resolvers that
 * already own authentication and domain-error handling.
 */
@Suppress("LongParameterList")
class PgGraphqlMutationClient(
    httpClient: HttpClient,
    endpoint: String,
) {
    private val transport = PgGraphqlTransport(httpClient, endpoint, DbRequestHeaders { emptyMap() })

    /** Inserts one Viaduct input GRT without requiring callers to construct GraphQL variable JSON. */
    suspend fun insert(
        entity: PgGraphqlEntity,
        input: Input,
        selection: String = "affectedCount",
        headers: Map<String, String> = emptyMap(),
    ): JsonObject = insert(entity, buildJsonArray { add(input.toPgGraphqlInput()) }, selection, headers)

    /** Inserts [objects] and returns the selected pg_graphql mutation payload. */
    suspend fun insert(
        entity: PgGraphqlEntity,
        objects: JsonArray,
        selection: String = "affectedCount",
        headers: Map<String, String> = emptyMap(),
    ): JsonObject = insertResult(entity, objects, selection, headers).strict(entity.insertField)

    /** Inserts [objects], preserving partial payload data and structured upstream errors. */
    suspend fun insertResult(
        entity: PgGraphqlEntity,
        objects: JsonArray,
        selection: String = "affectedCount",
        headers: Map<String, String> = emptyMap(),
    ): DbResult<JsonObject> =
        execute(
            entity.insertField,
            "mutation Insert(${'$'}objects: [${entity.typeName}InsertInput!]!) { " +
                "${entity.insertField}(objects: ${'$'}objects) { $selection } }",
            buildJsonObject { put("objects", objects) },
            headers,
        )

    /** Updates matching rows and returns the selected pg_graphql mutation payload. */
    suspend fun update(
        entity: PgGraphqlEntity,
        set: JsonObject,
        filter: JsonObject,
        atMost: Int,
        selection: String = "affectedCount",
        headers: Map<String, String> = emptyMap(),
    ): JsonObject = updateResult(entity, set, filter, atMost, selection, headers).strict(entity.updateField)

    /** Updates from a Viaduct input GRT without requiring callers to construct the set object. */
    suspend fun update(
        entity: PgGraphqlEntity,
        input: Input,
        filter: JsonObject,
        atMost: Int,
        selection: String = "affectedCount",
        headers: Map<String, String> = emptyMap(),
    ): JsonObject = update(entity, input.toPgGraphqlInput(), filter, atMost, selection, headers)

    /** Updates matching rows, preserving partial payload data and structured upstream errors. */
    suspend fun updateResult(
        entity: PgGraphqlEntity,
        set: JsonObject,
        filter: JsonObject,
        atMost: Int,
        selection: String = "affectedCount",
        headers: Map<String, String> = emptyMap(),
    ): DbResult<JsonObject> {
        require(atMost > 0) { "atMost must be greater than zero" }
        return execute(
            entity.updateField,
            "mutation Update(${'$'}set: ${entity.typeName}UpdateInput!, " +
                "${'$'}filter: ${entity.typeName}Filter!, ${'$'}atMost: Int!) { " +
                "${entity.updateField}(set: ${'$'}set, filter: ${'$'}filter, atMost: ${'$'}atMost) " +
                "{ $selection } }",
            buildJsonObject {
                put("set", set)
                put("filter", filter)
                put("atMost", atMost)
            },
            headers,
        )
    }

    /** Deletes matching rows and returns the selected pg_graphql mutation payload. */
    suspend fun delete(
        entity: PgGraphqlEntity,
        filter: JsonObject,
        atMost: Int,
        selection: String = "affectedCount",
        headers: Map<String, String> = emptyMap(),
    ): JsonObject = deleteResult(entity, filter, atMost, selection, headers).strict(entity.deleteField)

    /** Deletes rows matching every field in a Viaduct input GRT. */
    suspend fun delete(
        entity: PgGraphqlEntity,
        input: Input,
        atMost: Int,
        selection: String = "affectedCount",
        headers: Map<String, String> = emptyMap(),
    ): JsonObject = delete(entity, input.toPgGraphqlInput().toEqualityFilter(), atMost, selection, headers)

    /** Deletes matching rows, preserving partial payload data and structured upstream errors. */
    suspend fun deleteResult(
        entity: PgGraphqlEntity,
        filter: JsonObject,
        atMost: Int,
        selection: String = "affectedCount",
        headers: Map<String, String> = emptyMap(),
    ): DbResult<JsonObject> {
        require(atMost > 0) { "atMost must be greater than zero" }
        return execute(
            entity.deleteField,
            "mutation Delete(${'$'}filter: ${entity.typeName}Filter!, ${'$'}atMost: Int!) { " +
                "${entity.deleteField}(filter: ${'$'}filter, atMost: ${'$'}atMost) { $selection } }",
            buildJsonObject {
                put("filter", filter)
                put("atMost", atMost)
            },
            headers,
        )
    }

    private suspend fun execute(
        responseKey: String,
        document: String,
        variables: JsonObject,
        headers: Map<String, String>,
    ): DbResult<JsonObject> = transport.executeResult(headers, GraphqlQuery(document, variables, responseKey))
}

private fun Input.toPgGraphqlInput(): JsonObject {
    @Suppress("UNCHECKED_CAST")
    val inputData =
        runCatching {
            javaClass.getMethod("getInputData").invoke(this) as Map<String, Any?>
        }.getOrElse {
            error("Viaduct input ${this::class.qualifiedName} does not expose its input data")
        }
    return inputData.toJsonElement().let { it as JsonObject }
}

private fun JsonObject.toEqualityFilter(): JsonObject =
    buildJsonObject {
        this@toEqualityFilter.forEach { (field, value) ->
            put(field, buildJsonObject { put("eq", value) })
        }
    }

@Suppress("CyclomaticComplexMethod")
private fun Any?.toJsonElement(): JsonElement =
    when (this) {
        null -> JsonNull
        is JsonElement -> this
        is GlobalID<*> -> JsonPrimitive(internalID)
        is Input -> toPgGraphqlInput()
        is Map<*, *> ->
            buildJsonObject {
                this@toJsonElement.forEach { (key, value) -> put(key.toString(), value.toJsonElement()) }
            }
        is Iterable<*> -> buildJsonArray { this@toJsonElement.forEach { add(it.toJsonElement()) } }
        is Array<*> -> buildJsonArray { this@toJsonElement.forEach { add(it.toJsonElement()) } }
        is Boolean -> JsonPrimitive(this)
        is Number -> JsonPrimitive(this)
        is Enum<*> -> JsonPrimitive(name)
        else -> JsonPrimitive(toString())
    }

/** Names generated by pg_graphql for a table exposed as [typeName]. */
data class PgGraphqlEntity(
    val typeName: String,
) {
    init {
        require(Regex("[_A-Za-z][_0-9A-Za-z]*").matches(typeName)) {
            "typeName must be a GraphQL name"
        }
    }

    internal val insertField = "insertInto${typeName}Collection"
    internal val updateField = "update${typeName}Collection"
    internal val deleteField = "deleteFrom${typeName}Collection"
}
