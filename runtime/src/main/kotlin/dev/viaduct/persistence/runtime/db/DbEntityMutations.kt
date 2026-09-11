@file:OptIn(viaduct.apiannotations.ExperimentalApi::class, viaduct.apiannotations.InternalApi::class)

package dev.viaduct.persistence.runtime.db

import dev.viaduct.persistence.runtime.reflection.GeneratedBuilder
import dev.viaduct.persistence.runtime.reflection.GeneratedFieldReflection
import dev.viaduct.persistence.runtime.reflection.GeneratedTypeReflection
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import viaduct.api.context.MutationFieldExecutionContext
import viaduct.api.reflect.CompositeField
import viaduct.api.reflect.Type
import viaduct.api.types.CompositeOutput
import viaduct.api.types.NodeObject

/** CRUD operations for one persisted Viaduct node type. */
class DbEntityMutations<T : NodeObject>
    @PublishedApi
    internal constructor(
        private val client: DbClient,
        private val entityType: Type<T>,
    ) {
        /** Inserts one row and returns the current mutation resolver's payload. */
        suspend fun <P : CompositeOutput> insert(
            ctx: MutationFieldExecutionContext<*, *, *, P>,
            input: PgGraphqlObject,
        ): P = payload(ctx, client.insertRaw(ctx, input, entityType.name), batch = false)

        /** Inserts several rows in one pg_graphql mutation and returns the current resolver's payload. */
        suspend fun <P : CompositeOutput> insertBatch(
            ctx: MutationFieldExecutionContext<*, *, *, P>,
            inputs: Iterable<PgGraphqlObject>,
        ): P = payload(ctx, client.insertRaw(ctx, inputs, entityType.name), batch = true)

        /** Updates one row and returns the current mutation resolver's payload. */
        suspend fun <P : CompositeOutput> update(
            ctx: MutationFieldExecutionContext<*, *, *, P>,
            mutation: PgGraphqlUpdate,
        ): P = payload(ctx, client.updateRaw(ctx, mutation, entityType.name), batch = false)

        /** Updates several independently identified rows and returns a list-valued resolver payload. */
        suspend fun <P : CompositeOutput> updateBatch(
            ctx: MutationFieldExecutionContext<*, *, *, P>,
            mutations: Iterable<PgGraphqlUpdate>,
        ): P = payload(ctx, client.updateRaw(ctx, mutations, entityType.name), batch = true)

        /** Deletes one row and returns the current mutation resolver's payload. */
        suspend fun <P : CompositeOutput> delete(
            ctx: MutationFieldExecutionContext<*, *, *, P>,
            mutation: PgGraphqlDelete,
        ): P =
            payload(
                ctx,
                client.deleteRaw(ctx, mutation, entityType.name),
                batch = false,
                allowNoEntityField = true,
            )

        /** Deletes several independently identified rows and returns the current resolver's payload. */
        suspend fun <P : CompositeOutput> deleteBatch(
            ctx: MutationFieldExecutionContext<*, *, *, P>,
            mutations: Iterable<PgGraphqlDelete>,
        ): P =
            payload(
                ctx,
                client.deleteRaw(ctx, mutations, entityType.name),
                batch = true,
                allowNoEntityField = true,
            )

        /**
         * Builds the mutation resolver's declared payload from pg_graphql's returned records.
         *
         * The generated mutation context identifies [P]. Its generated builder is populated with
         * node references made from the returned internal IDs. Singular operations require one
         * record; batch operations provide all returned references. Insert and update payloads
         * require exactly one field of [entityType], while delete payloads may omit that field.
         * When present, `userErrors` is initialized to an empty list.
         */
        @Suppress("UNCHECKED_CAST")
        private fun <P : CompositeOutput> payload(
            ctx: MutationFieldExecutionContext<*, *, *, P>,
            mutation: JsonObject,
            batch: Boolean,
            allowNoEntityField: Boolean = false,
        ): P {
            val payloadType = mutationPayloadType<P>(ctx.javaClass)
            val builder =
                GeneratedBuilder.fromExecutionContext(
                    GeneratedTypeReflection().builderClass(payloadType),
                    ctx,
                )
            val entityFields =
                GeneratedFieldReflection()
                    .allFields(payloadType)
                    .filterIsInstance<CompositeField<*, *>>()
                    .filter { it.type.name == entityType.name }

            if (entityFields.isEmpty() && !allowNoEntityField) {
                error("Mutation payload ${payloadType.name} has no ${entityType.name} field")
            }
            if (entityFields.size > 1) {
                error("Mutation payload ${payloadType.name} has multiple ${entityType.name} fields")
            }

            entityFields.singleOrNull()?.let { field ->
                val references =
                    mutation.records().map { record ->
                        ctx.nodeRef(
                            ctx.globalIDFor(
                                entityType,
                                record.jsonObject
                                    .getValue("uuidId")
                                    .jsonPrimitive.content,
                            ),
                        )
                    }
                val value = if (batch) references else references.single()
                builder.set(field, value)
            }
            GeneratedFieldReflection().anyField(payloadType, "userErrors")?.let { builder.set(it, emptyList<Any>()) }
            return builder.build() as P
        }

        private fun JsonObject.records(): JsonArray = getValue("records").jsonArray
    }

@Suppress("UNCHECKED_CAST")
@PublishedApi
internal fun <T : NodeObject> reflectedType(nodeClass: Class<T>): Type<T> {
    val reflectionClass = Class.forName("${nodeClass.name}\$Reflection")
    return reflectionClass.getField("INSTANCE").get(null) as Type<T>
}

@Suppress("UNCHECKED_CAST")
private fun <P : CompositeOutput> mutationPayloadType(contextClass: Class<*>): Type<P> {
    val mutationContext =
        contextClass.genericInterfaces
            .filterIsInstance<java.lang.reflect.ParameterizedType>()
            .singleOrNull {
                (it.rawType as? Class<*>) == MutationFieldExecutionContext::class.java
            } ?: error("${contextClass.name} does not declare a typed MutationFieldExecutionContext")
    val payloadClass =
        mutationContext.actualTypeArguments[MUTATION_PAYLOAD_TYPE_ARGUMENT] as? Class<*>
            ?: error("${contextClass.name} does not declare a concrete mutation payload type")
    val reflectionClass = Class.forName("${payloadClass.name}\$Reflection")
    return reflectionClass.getField("INSTANCE").get(null) as Type<P>
}

private const val MUTATION_PAYLOAD_TYPE_ARGUMENT = 3
