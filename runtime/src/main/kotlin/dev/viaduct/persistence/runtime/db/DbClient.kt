@file:OptIn(viaduct.apiannotations.ExperimentalApi::class)

package dev.viaduct.persistence.runtime.db
import dev.viaduct.persistence.runtime.connection.ConnectionFetcher
import dev.viaduct.persistence.runtime.connection.ConnectionPageRequest
import dev.viaduct.persistence.runtime.connection.NestedConnectionPageRequest
import dev.viaduct.persistence.runtime.connection.UuidConnectionPage
import dev.viaduct.persistence.runtime.graphql.PgGraphqlTransport
import dev.viaduct.persistence.runtime.node.NodeReferenceHydrator
import dev.viaduct.persistence.runtime.node.NodeReferencePlanner
import dev.viaduct.persistence.runtime.reflection.GeneratedTypeReflection
import io.ktor.client.HttpClient
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import viaduct.api.context.ExecutionContext
import viaduct.api.context.ResolverExecutionContext
import viaduct.api.select.SelectionSet
import viaduct.api.types.CompositeOutput
import viaduct.api.types.Input
import viaduct.api.types.NodeObject
import viaduct.api.types.Query

/**
 * Supplies provider-specific headers for each db request.
 *
 * The callback is evaluated for every request so applications can derive authorization from the
 * current execution context instead of storing request credentials in the runtime client.
 */
fun interface DbRequestHeaders {
    suspend fun forContext(context: ExecutionContext): Map<String, String>
}

/**
 * Executes Viaduct-owned selections against a pg_graphql db.
 *
 * The client derives translation conventions from the generated Viaduct types supplied by each
 * selection set. Applications own endpoint selection, HTTP-client lifecycle, and provider-specific
 * request headers.
 */
@Suppress("TooManyFunctions")
class DbClient(
    private val httpClient: HttpClient,
    private val endpoint: String,
    private val requestHeaders: DbRequestHeaders =
        DbRequestHeaders { emptyMap() },
) {
    private val typeReflection = GeneratedTypeReflection()
    private val transport =
        PgGraphqlTransport(
            httpClient = httpClient,
            endpoint = endpoint,
            requestHeaders = requestHeaders,
        )
    private val queryPlanner = DbQueryPlanner(typeReflection)
    private val nodeReferencePlanner = NodeReferencePlanner(typeReflection)
    private val nodeReferenceHydrator = NodeReferenceHydrator(typeReflection)
    private val dbFetcher =
        DbFetcher(
            transport = transport,
            queryPlanner = queryPlanner,
            typeReflection = typeReflection,
            nodeReferencePlanner = nodeReferencePlanner,
            nodeReferenceHydrator = nodeReferenceHydrator,
        )
    private val dbBatchFetcher =
        DbBatchFetcher(
            transport = transport,
            queryPlanner = queryPlanner,
            typeReflection = typeReflection,
            nodeReferencePlanner = nodeReferencePlanner,
            nodeReferenceHydrator = nodeReferenceHydrator,
        )
    private val connectionFetcher = ConnectionFetcher(transport)
    private val mutationClient = PgGraphqlMutationClient(httpClient, endpoint)

    /** Inserts the fields of [input] into the pg_graphql entity represented by [T]. */
    suspend inline fun <reified T : NodeObject> insert(
        ctx: ExecutionContext,
        input: Input,
    ): JsonObject = insert(ctx, input, requireNotNull(T::class.simpleName))

    /** Updates the pg_graphql entity represented by [T], using its typed `@idOf` input as the filter. */
    suspend inline fun <reified T : NodeObject> update(
        ctx: ExecutionContext,
        input: Input,
    ): JsonObject = update(ctx, input, requireNotNull(T::class.simpleName))

    /** Deletes the pg_graphql entity represented by [T], using its typed `@idOf` input as the filter. */
    suspend inline fun <reified T : NodeObject> delete(
        ctx: ExecutionContext,
        input: Input,
    ): JsonObject = delete(ctx, input, requireNotNull(T::class.simpleName))

    @PublishedApi
    internal suspend fun insert(
        ctx: ExecutionContext,
        input: Input,
        entityName: String,
    ): JsonObject =
        mutationClient.insert(
            PgGraphqlEntity(entityName),
            input,
            selection = "affectedCount records { uuidId }",
            headers = requestHeaders.forContext(ctx),
        )

    @PublishedApi
    internal suspend fun update(
        ctx: ExecutionContext,
        input: Input,
        entityName: String,
    ): JsonObject {
        val identifiers = input.mutationIdentifiers(entityName)
        require(identifiers.isNotEmpty()) { "Update input has no @idOf field for $entityName" }
        return mutationClient.update(
            PgGraphqlEntity(entityName),
            input.mutationValues(entityName),
            identifiers.equalityFilter(),
            atMost = 1,
            selection = "affectedCount records { uuidId }",
            headers = requestHeaders.forContext(ctx),
        )
    }

    @PublishedApi
    internal suspend fun delete(
        ctx: ExecutionContext,
        input: Input,
        entityName: String,
    ): JsonObject {
        val identifiers = input.mutationIdentifiers(entityName)
        require(identifiers.isNotEmpty()) { "Delete input has no @idOf field for $entityName" }
        return mutationClient.delete(
            PgGraphqlEntity(entityName),
            identifiers.equalityFilter(),
            atMost = 1,
            headers = requestHeaders.forContext(ctx),
        )
    }

    /** Fetches [selections] and converts the result to a GRT. Shortcut for [fetchJson] + [toGRT]. */
    suspend fun <T : CompositeOutput> fetch(
        ctx: ExecutionContext,
        dbRead: DbRead,
        selections: SelectionSet<T>,
    ): T = dbFetcher.fetch(ctx, dbRead, selections)

    /** Fetches partial typed data while preserving upstream GraphQL errors. */
    suspend fun <T : CompositeOutput> fetchResult(
        ctx: ExecutionContext,
        dbRead: DbRead,
        selections: SelectionSet<T>,
    ): DbResult<T> = dbFetcher.fetchResult(ctx, dbRead, selections)

    /**
     * Fetches the raw pg_graphql JSON for [selections], without converting it to a GRT. Use
     * [toGRT] to convert the result, or [fetch] for the common case of doing both in one call.
     */
    suspend fun <T : CompositeOutput> fetchJson(
        ctx: ExecutionContext,
        dbRead: DbRead,
        selections: SelectionSet<T>,
    ): JsonObject = dbFetcher.fetchJson(ctx, dbRead, selections)

    /** Fetches partial JSON data while preserving upstream GraphQL errors. */
    suspend fun <T : CompositeOutput> fetchJsonResult(
        ctx: ExecutionContext,
        dbRead: DbRead,
        selections: SelectionSet<T>,
    ): DbResult<JsonObject> = dbFetcher.fetchJsonResult(ctx, dbRead, selections)

    suspend fun <T> fetchNode(
        ctx: ResolverExecutionContext<out Query>,
        dbRead: DbRead,
        ownedSelections: SelectionSet<T>,
        requestedSelections: SelectionSet<T>,
    ): T where T : CompositeOutput, T : NodeObject =
        dbFetcher.fetchNode(
            ctx,
            dbRead,
            ownedSelections,
            requestedSelections,
        )

    suspend fun <T> fetchByInternalId(
        ctx: ResolverExecutionContext<out Query>,
        collectionField: String,
        id: String,
        ownedSelections: SelectionSet<T>,
        requestedSelections: SelectionSet<T>,
    ): T where T : CompositeOutput, T : NodeObject =
        fetchNode(
            ctx,
            DbRead(
                root =
                    DbRoot(
                        field = collectionField,
                        arguments = "(filter: {uuidId: {eq: \$id}})",
                        variableDefinitions = "\$id: UUID!",
                        variables = buildJsonObject { put("id", id) },
                        singleViaFilteredCollection = true,
                    ),
            ),
            ownedSelections,
            requestedSelections,
        )

    /**
     * Fetches and hydrates several nodes with one pg_graphql request. The returned map uses the
     * provider UUID, so callers can put the objects back into the connection's original order.
     */
    suspend fun <T> fetchByInternalIds(
        ctx: ResolverExecutionContext<out Query>,
        collectionField: String,
        ids: List<String>,
        ownedSelections: SelectionSet<T>,
        requestedSelections: SelectionSet<T> = ownedSelections,
    ): Map<String, T> where T : CompositeOutput, T : NodeObject =
        dbBatchFetcher.fetchByInternalIds(
            ctx,
            collectionField,
            ids,
            ownedSelections,
            requestedSelections,
        )

    suspend fun fetchUuidIds(
        ctx: ExecutionContext,
        collectionField: String,
        arguments: String = "",
        variableDefinitions: String = "",
        variables: JsonObject = buildJsonObject {},
    ): List<String> =
        connectionFetcher.fetchUuidIds(
            ctx,
            collectionField,
            arguments,
            variableDefinitions,
            variables,
        )

    /**
     * Fetches a pg_graphql connection while preserving the provider's cursors and page info.
     *
     * Callers that expose a Viaduct connection should pass these cursors back to this method on
     * the next request. This keeps pagination database-managed instead of converting a cursor
     * into an offset or loading the entire collection into the application.
     */
    suspend fun fetchUuidConnection(
        ctx: ExecutionContext,
        request: ConnectionPageRequest,
    ): UuidConnectionPage = connectionFetcher.fetchUuidConnection(ctx, request)

    /** Compatibility overload for callers that pass pagination arguments individually. */
    @Suppress("LongParameterList")
    suspend fun fetchUuidConnection(
        ctx: ExecutionContext,
        collectionField: String,
        first: Int? = null,
        after: String? = null,
        last: Int? = null,
        before: String? = null,
        additionalArguments: String = "",
        additionalVariableDefinitions: String = "",
        additionalVariables: JsonObject = buildJsonObject {},
    ): UuidConnectionPage =
        fetchUuidConnection(
            ctx = ctx,
            request =
                ConnectionPageRequest(
                    collectionField,
                    first,
                    after,
                    last,
                    before,
                    additionalArguments,
                    additionalVariableDefinitions,
                    additionalVariables,
                ),
        )

    /**
     * Loads one paginated child connection for every requested parent in a single pg_graphql
     * query. pg_graphql evaluates the nested connection per parent, so `first`/`after` retain
     * their per-parent meaning without issuing one request per parent.
     */
    suspend fun fetchNestedUuidConnections(
        ctx: ExecutionContext,
        request: NestedConnectionPageRequest,
    ): Map<String, UuidConnectionPage> = connectionFetcher.fetchNestedUuidConnections(ctx, request)

    /** Compatibility overload for callers that pass nested pagination arguments individually. */
    @Suppress("LongParameterList")
    suspend fun fetchNestedUuidConnections(
        ctx: ExecutionContext,
        parentCollectionField: String,
        parentIds: List<String>,
        childCollectionField: String,
        first: Int? = null,
        after: String? = null,
        last: Int? = null,
        before: String? = null,
    ): Map<String, UuidConnectionPage> =
        fetchNestedUuidConnections(
            ctx = ctx,
            request =
                NestedConnectionPageRequest(
                    parentCollectionField = parentCollectionField,
                    parentIds = parentIds,
                    child =
                        ConnectionPageRequest(
                            collectionField = childCollectionField,
                            first = first,
                            after = after,
                            last = last,
                            before = before,
                        ),
                ),
        )
}
