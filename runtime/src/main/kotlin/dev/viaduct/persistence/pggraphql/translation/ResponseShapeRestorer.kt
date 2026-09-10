package dev.viaduct.persistence.pggraphql.translation

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

internal class ResponseShapeRestorer {
    private val fieldRestorers: List<ResponseFieldRestorer> =
        listOf(
            AssociationConnectionFieldRestorer(),
            AssociationEdgesFieldRestorer(),
            AssociationNodesFieldRestorer(),
            ViaductNodesFieldRestorer(),
            NestedResponseFieldRestorer(),
        )

    fun restore(response: JsonElement): JsonElement =
        when (response) {
            is JsonObject -> restoreObject(response)
            is JsonArray -> JsonArray(response.map(::restore))
            else -> response
        }

    /** Restores a pg_graphql error path using the aliases that define response restoration. */
    fun restorePath(path: List<JsonElement>): List<JsonElement> = ResponsePathRestorer.restore(path)

    private fun restoreObject(response: JsonObject): JsonObject =
        JsonObject(
            response.entries.associate { (key, value) ->
                val restorer = fieldRestorers.first { it.supports(key, value) }
                val restored = restorer.restore(key, value, ::restore)
                restored.key to restored.value
            },
        )
}

@Suppress("MagicNumber")
private object ResponsePathRestorer {
    fun restore(path: List<JsonElement>): List<JsonElement> {
        val restored = mutableListOf<JsonElement>()
        var index = 0
        while (index < path.size) {
            val segment = path[index]
            val key = (segment as? JsonPrimitive)?.content
            val consumed = restoreSegment(path, index, key, restored)
            index += consumed
        }
        return restored
    }

    private fun restoreSegment(
        path: List<JsonElement>,
        index: Int,
        key: String?,
        restored: MutableList<JsonElement>,
    ): Int =
        when {
            key == VIADUCT_NODES_RESPONSE_ALIAS -> restoreNodes(path, index, restored)
            key?.startsWith(VIADUCT_ASSOCIATION_NODES_ALIAS_PREFIX) == true ->
                restoreAssociationNodes(path, index, key, restored)
            key?.startsWith(VIADUCT_ASSOCIATION_EDGES_ALIAS_PREFIX) == true ->
                restoreAssociationEdges(path, index, key, restored)
            key?.startsWith(VIADUCT_ASSOCIATION_CONNECTION_ALIAS_PREFIX) == true -> {
                restored +=
                    JsonPrimitive(
                        responseKeyFromInternalAlias(VIADUCT_ASSOCIATION_CONNECTION_ALIAS_PREFIX, key),
                    )
                1
            }
            key?.startsWith(VIADUCT_ASSOCIATION_NODE_ALIAS_PREFIX) == true -> {
                restored += JsonPrimitive(responseKeyFromInternalAlias(VIADUCT_ASSOCIATION_NODE_ALIAS_PREFIX, key))
                1
            }
            else -> {
                restored += path[index]
                1
            }
        }

    private fun restoreNodes(
        path: List<JsonElement>,
        index: Int,
        restored: MutableList<JsonElement>,
    ): Int {
        restored += JsonPrimitive("nodes")
        path.getOrNull(index + 1)?.let(restored::add)
        return if (path.textAt(index + 2) == "node") 3 else 2
    }

    private fun restoreAssociationNodes(
        path: List<JsonElement>,
        index: Int,
        key: String,
        restored: MutableList<JsonElement>,
    ): Int {
        restored += JsonPrimitive(responseKeyFromInternalAlias(VIADUCT_ASSOCIATION_NODES_ALIAS_PREFIX, key))
        path.getOrNull(index + 1)?.let(restored::add)
        return if (
            path.textAt(index + 2) == "node" &&
            path.textAt(index + 3)?.startsWith(VIADUCT_ASSOCIATION_NODE_ALIAS_PREFIX) == true
        ) {
            4
        } else {
            2
        }
    }

    private fun restoreAssociationEdges(
        path: List<JsonElement>,
        index: Int,
        key: String,
        restored: MutableList<JsonElement>,
    ): Int {
        restored += JsonPrimitive(responseKeyFromInternalAlias(VIADUCT_ASSOCIATION_EDGES_ALIAS_PREFIX, key))
        path.getOrNull(index + 1)?.let(restored::add)
        if (path.textAt(index + 2) != "node") return 2
        val nodeAlias = path.textAt(index + 3)
        return if (nodeAlias?.startsWith(VIADUCT_ASSOCIATION_NODE_ALIAS_PREFIX) == true) {
            restored += JsonPrimitive(responseKeyFromInternalAlias(VIADUCT_ASSOCIATION_NODE_ALIAS_PREFIX, nodeAlias))
            4
        } else {
            3
        }
    }

    private fun List<JsonElement>.textAt(index: Int): String? = (getOrNull(index) as? JsonPrimitive)?.content
}

private class AssociationConnectionFieldRestorer : ResponseFieldRestorer {
    override fun supports(
        key: String,
        value: JsonElement,
    ): Boolean = key.startsWith(VIADUCT_ASSOCIATION_CONNECTION_ALIAS_PREFIX) && value is JsonObject

    override fun restore(
        key: String,
        value: JsonElement,
        restore: (JsonElement) -> JsonElement,
    ): RestoredResponseField =
        RestoredResponseField(
            key = responseKeyFromInternalAlias(VIADUCT_ASSOCIATION_CONNECTION_ALIAS_PREFIX, key),
            value = restore(value),
        )
}

private class AssociationEdgesFieldRestorer : ResponseFieldRestorer {
    override fun supports(
        key: String,
        value: JsonElement,
    ): Boolean = key.startsWith(VIADUCT_ASSOCIATION_EDGES_ALIAS_PREFIX) && value is JsonArray

    override fun restore(
        key: String,
        value: JsonElement,
        restore: (JsonElement) -> JsonElement,
    ): RestoredResponseField {
        val responseKey = responseKeyFromInternalAlias(VIADUCT_ASSOCIATION_EDGES_ALIAS_PREFIX, key)
        return RestoredResponseField(
            key = responseKey,
            value = JsonArray(value.jsonArray.map { flattenEdge(restore(it).jsonObject) }),
        )
    }

    private fun flattenEdge(edge: JsonObject): JsonObject {
        val row = edge["node"] as? JsonObject ?: return edge
        val nodeAlias = row.keys.firstOrNull { it.startsWith(VIADUCT_ASSOCIATION_NODE_ALIAS_PREFIX) }
        val flattened = linkedMapOf<String, JsonElement>()
        edge.forEach { (key, value) ->
            if (key != "node") flattened[key] = value
        }
        row.forEach { (key, value) ->
            if (key != nodeAlias) flattened[key] = value
        }
        nodeAlias?.let { alias ->
            flattened[responseKeyFromInternalAlias(VIADUCT_ASSOCIATION_NODE_ALIAS_PREFIX, alias)] =
                requireNotNull(row[alias])
        }
        return JsonObject(flattened)
    }
}

private class AssociationNodesFieldRestorer : ResponseFieldRestorer {
    override fun supports(
        key: String,
        value: JsonElement,
    ): Boolean = key.startsWith(VIADUCT_ASSOCIATION_NODES_ALIAS_PREFIX) && value is JsonArray

    override fun restore(
        key: String,
        value: JsonElement,
        restore: (JsonElement) -> JsonElement,
    ): RestoredResponseField {
        val responseKey = responseKeyFromInternalAlias(VIADUCT_ASSOCIATION_NODES_ALIAS_PREFIX, key)
        return RestoredResponseField(
            key = responseKey,
            value =
                JsonArray(
                    value.jsonArray.map { edge ->
                        val restoredEdge = restore(edge).jsonObject
                        val row = requireNotNull(restoredEdge["node"] as? JsonObject)
                        val nodeAlias = row.keys.first { it.startsWith(VIADUCT_ASSOCIATION_NODE_ALIAS_PREFIX) }
                        requireNotNull(row[nodeAlias])
                    },
                ),
        )
    }
}

private data class RestoredResponseField(
    val key: String,
    val value: JsonElement,
)

private interface ResponseFieldRestorer {
    fun supports(
        key: String,
        value: JsonElement,
    ): Boolean

    fun restore(
        key: String,
        value: JsonElement,
        restore: (JsonElement) -> JsonElement,
    ): RestoredResponseField
}

private class ViaductNodesFieldRestorer : ResponseFieldRestorer {
    override fun supports(
        key: String,
        value: JsonElement,
    ): Boolean = key == VIADUCT_NODES_RESPONSE_ALIAS && value is JsonArray

    override fun restore(
        key: String,
        value: JsonElement,
        restore: (JsonElement) -> JsonElement,
    ): RestoredResponseField =
        RestoredResponseField(
            key = "nodes",
            value =
                JsonArray(
                    value.jsonArray.map { edge ->
                        restore(edge.jsonObject["node"] ?: edge)
                    },
                ),
        )
}

private class NestedResponseFieldRestorer : ResponseFieldRestorer {
    override fun supports(
        key: String,
        value: JsonElement,
    ): Boolean = true

    override fun restore(
        key: String,
        value: JsonElement,
        restore: (JsonElement) -> JsonElement,
    ): RestoredResponseField =
        RestoredResponseField(
            key = key,
            value = restore(value),
        )
}
