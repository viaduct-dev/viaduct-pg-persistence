package dev.viaduct.persistence.runtime.db

import dev.viaduct.persistence.runtime.graphql.GraphqlQuery
import dev.viaduct.persistence.runtime.graphql.PgGraphqlTransport
import io.ktor.client.HttpClient
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject

/**
 * Executes an application-owned pg_graphql operation without requiring a Viaduct execution
 * context. This is intended for database functions and projections that do not map to a generated
 * Viaduct selection.
 */
class PgGraphqlClient(
    httpClient: HttpClient,
    endpoint: String,
) {
    private val transport = PgGraphqlTransport(httpClient, endpoint, DbRequestHeaders { emptyMap() })

    /** Executes [document] and throws when pg_graphql returns errors. */
    suspend fun execute(
        document: String,
        responseKey: String,
        variables: JsonObject = buildJsonObject {},
        headers: Map<String, String> = emptyMap(),
    ): JsonObject = executeResult(document, responseKey, variables, headers).strict(responseKey)

    /** Executes [document], preserving partial data and structured pg_graphql errors. */
    suspend fun executeResult(
        document: String,
        responseKey: String,
        variables: JsonObject = buildJsonObject {},
        headers: Map<String, String> = emptyMap(),
    ): DbResult<JsonObject> =
        transport.executeResult(
            headers,
            GraphqlQuery(document, variables, responseKey),
        )
}
