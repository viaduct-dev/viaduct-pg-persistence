package dev.viaduct.persistence.runtime.db

import dev.viaduct.persistence.runtime.graphql.GraphqlQuery
import dev.viaduct.persistence.runtime.graphql.PgGraphqlTransport
import io.ktor.client.HttpClient
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

/** Executes application-owned operations against a pg_graphql endpoint. */
@Suppress("LongParameterList")
class PgGraphqlClient(
    httpClient: HttpClient,
    endpoint: String,
) {
    private val transport = PgGraphqlTransport(httpClient, endpoint, DbRequestHeaders { emptyMap() })

    /** Executes an arbitrary application-owned GraphQL operation and returns its root field. */
    suspend fun execute(
        document: String,
        variables: JsonObject = buildJsonObject {},
        responseKey: String,
        headers: Map<String, String> = emptyMap(),
    ): JsonElement = executeResult(document, variables, responseKey, headers).strict(responseKey)

    /** Executes an operation while preserving partial data and structured upstream errors. */
    suspend fun executeResult(
        document: String,
        variables: JsonObject = buildJsonObject {},
        responseKey: String,
        headers: Map<String, String> = emptyMap(),
    ): DbResult<JsonElement> = transport.executeElementResult(headers, GraphqlQuery(document, variables, responseKey))

    /** Queries a pg_graphql Relay collection and returns its node records. */
    suspend fun select(
        entity: PgGraphqlEntity,
        selection: String,
        filter: JsonObject = buildJsonObject {},
        first: Int = 10_000,
        orderBy: JsonArray = JsonArray(emptyList()),
        headers: Map<String, String> = emptyMap(),
    ): JsonArray {
        require(first > 0) { "first must be greater than zero" }
        val orderArgument = if (orderBy.isEmpty()) "" else ", orderBy: ${'$'}orderBy"
        val orderVariable = if (orderBy.isEmpty()) "" else ", ${'$'}orderBy: [${entity.typeName}OrderBy!]"
        val root =
            execute(
                document =
                    "query Select(${'$'}filter: ${entity.typeName}Filter, ${'$'}first: Int$orderVariable) { " +
                        "${entity.collectionField}(filter: ${'$'}filter, first: ${'$'}first$orderArgument) { " +
                        "edges { node { $selection } } } }",
                variables =
                    buildJsonObject {
                        put("filter", filter)
                        put("first", first)
                        if (orderBy.isNotEmpty()) put("orderBy", orderBy)
                    },
                responseKey = entity.collectionField,
                headers = headers,
            )
        return JsonArray(
            root.jsonObject
                .getValue("edges")
                .jsonArray
                .map { edge -> edge.jsonObject.getValue("node") },
        )
    }
}
