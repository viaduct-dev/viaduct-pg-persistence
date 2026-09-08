# Runtime

The runtime executes Viaduct-owned db selections against a pg_graphql endpoint. It derives
translation information from the generated Viaduct reflection types at request time; it does not
load a generated translation descriptor.

```kotlin
dependencies {
    implementation("dev.viaduct.persistence:runtime:0.1.0-SNAPSHOT")
}
```

```kotlin
val client = DbClient(
    httpClient = httpClient,
    endpoint = "$postgresGraphqlEndpoint/graphql",
    requestHeaders = DbRequestHeaders { context ->
        mapOf("Authorization" to "Bearer ${accessTokenFor(context)}")
    },
)
```

`DbClient` provides:

- `fetch` for an explicit db root and owned selection set. Shortcut for `fetchJson` + `toGRT`.
- `fetchResult` for partial typed data together with structured upstream GraphQL errors.
- `fetchJson` for the same read, returning the raw pg_graphql JSON without converting it to a GRT.
- `fetchJsonResult` for partial JSON together with upstream error messages, paths, locations, and
  extensions.
- `toGRT` (an extension on the `JsonObject` result) to convert JSON — from `fetchJson`, a cache, or
  any other source — into the generated Viaduct value for a typed selection set.
- `fetchNode` for results that also need requested node references.
- `fetchByInternalId`/`fetchByInternalIds` for the common filtered-collection node lookup.
- `fetchUuidIds` for collection resolvers that return Viaduct node references.
- `fetchUuidConnection` for caller-managed `first`/`after` or `last`/`before` pagination.
- `fetchNestedUuidConnections` for one paginated child connection per parent in one request.

The result-returning operation is the canonical fetch path. The strict `fetch` and `fetchJson`
operations are adapters that throw `UpstreamGraphqlException` when that result contains errors.
Reach for the JSON operation directly when you need to inspect a response before conversion, or to
convert JSON obtained some other way:

```kotlin
val json = client.fetchJson(ctx, dbRead, selections)
val value = json.toGRT(ctx, selections)
```

The application owns the HTTP client lifecycle, endpoint, and header policy. The runtime owns
query translation, transport execution, GraphQL error propagation, response restoration, GRT
mapping, and node-reference hydration. A generated `ConnectionBuilder` with a compatibility
`nodes` field or an `edges { node }` shape is recognized structurally, so nested connections and
ordinary domain fields named `nodes` remain schema-safe without translation metadata.

## Writes

`PgGraphqlMutationClient` executes pg_graphql's generated Relay CRUD mutations without requiring
a Viaduct execution context. This lets an application's mutation resolvers retain their domain
validation and `userErrors` behavior while replacing direct PostgREST `POST`, `PATCH`, and `DELETE`
calls. Credentials are passed per call and values are always sent as GraphQL variables:

```kotlin
val writes = PgGraphqlMutationClient(httpClient, "$postgresGraphqlEndpoint/graphql/v1")
val person = PgGraphqlEntity("Person")

val result = writes.updateResult(
    entity = person,
    set = buildJsonObject { put("name", input.name) },
    filter = buildJsonObject {
        put("uuidId", buildJsonObject { put("eq", input.id) })
    },
    atMost = 1,
    selection = "affectedCount records { uuidId name }",
    headers = mapOf("Authorization" to "Bearer $accessToken", "apikey" to anonKey),
)
```

Use the `*Result` methods to translate structured database errors into application payload errors.
The strict `insert`, `update`, and `delete` methods throw `UpstreamGraphqlException` when pg_graphql
returns errors. The explicit `atMost` parameter prevents an accidentally broad update or delete.

For database functions and application-specific projections, `PgGraphqlClient` executes an
application-owned GraphQL document with the same per-call headers and structured error behavior.
It is the escape hatch for migrating database operations that are not ordinary table CRUD without
falling back to PostgREST.

Result operations preserve partial data and structured errors. Error paths are restored through
the same response-shape transformation as data, including association rows and filtered single-row
lookups. They are not automatically installed into Viaduct's field-error channel: a resolver that
returns partial data must translate the returned errors at its execution boundary. The strict
operations intentionally throw instead and therefore do not retain partial data.

When a connection uses a join table, the pg_graphql path resolver uses the real
`<fieldName>Associations` relationship (for example, `membersAssociations`), even when the edge
contains only `node` and `cursor`. Pagination, filters, and ordering are applied to association
rows. Each response row is then unwrapped from `association.node` into the Viaduct edge node while
the remaining association columns become edge fields; a single unidirectional connection uses the
target relationship directly. No edge view or SQL function is required.

This translation assumes pg_graphql is called by a trusted Viaduct backend. It preserves selection
and response shapes but does not make authorization decisions; consuming applications apply
checker executors before returning persisted fields and keep the database endpoint behind that
boundary.
