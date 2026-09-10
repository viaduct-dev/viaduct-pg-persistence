@file:OptIn(viaduct.apiannotations.InternalApi::class)

package dev.viaduct.persistence.runtime.db

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import viaduct.api.context.ExecutionContext
import viaduct.api.mapping.GRTDomain
import viaduct.api.mapping.JsonDomain
import viaduct.api.select.SelectionSet
import viaduct.api.types.CompositeOutput

/**
 * Converts a pg_graphql JSON response into the generated Viaduct value for a typed selection set.
 *
 * This is the conversion used by [DbClient.fetchResult]. It remains public so a caller with JSON
 * obtained some other way — a cache, a different transport, or a test fixture — can convert it
 * without going through [DbClient.fetchJson].
 */
@Suppress("UNCHECKED_CAST")
fun <T : CompositeOutput> JsonObject.toGRT(
    ctx: ExecutionContext,
    selections: SelectionSet<T>,
): T {
    val jsonString = Json.encodeToString(JsonObject.serializer(), this)
    return JsonDomain
        .forSelectionSet(ctx, selections)
        .mapperTo(GRTDomain.forSelectionSet(ctx, selections))(jsonString) as T
}
