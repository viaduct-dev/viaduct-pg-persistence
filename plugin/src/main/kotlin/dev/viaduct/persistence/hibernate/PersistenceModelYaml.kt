package dev.viaduct.persistence.hibernate

import dev.viaduct.persistence.model.PersistenceAttribute
import dev.viaduct.persistence.model.PersistenceBasicAttribute
import dev.viaduct.persistence.model.PersistenceEdgeMapping
import dev.viaduct.persistence.model.PersistenceEntity
import dev.viaduct.persistence.model.PersistenceEnum
import dev.viaduct.persistence.model.PersistenceModel
import dev.viaduct.persistence.model.PersistenceToManyAttribute
import dev.viaduct.persistence.model.PersistenceToManyStorage
import dev.viaduct.persistence.model.PersistenceToOneAttribute

/** YAML encoding for [PersistenceModel], used by [HibernateMetadataConfigurationDescriptor]. */
internal object PersistenceModelYaml {
    fun toYaml(model: PersistenceModel): Map<String, Any?> =
        mapOf(
            "entities" to model.entities.map(::entityToYaml),
            "enums" to model.enums.map(::enumToYaml),
        )

    fun fromYaml(yaml: Map<String, Any?>): PersistenceModel =
        PersistenceModel(
            entities = yaml.yamlMapList("entities").map(::entityFromYaml),
            enums = yaml.yamlMapList("enums").map(::enumFromYaml),
        )

    private fun entityToYaml(entity: PersistenceEntity): Map<String, Any?> =
        mapOf(
            "graphqlName" to entity.graphqlName,
            "generatedGlobalId" to entity.generatedGlobalId,
            "attributes" to entity.attributes.map(::attributeToYaml),
        )

    private fun entityFromYaml(yaml: Map<String, Any?>): PersistenceEntity =
        PersistenceEntity(
            graphqlName = yaml.yamlString("graphqlName"),
            generatedGlobalId = yaml.yamlBoolean("generatedGlobalId"),
            attributes = yaml.yamlMapList("attributes").map(::attributeFromYaml),
        )

    @Suppress("MaxLineLength")
    private fun enumToYaml(enum: PersistenceEnum): Map<String, Any?> = mapOf("graphqlName" to enum.graphqlName, "values" to enum.values)

    private fun enumFromYaml(yaml: Map<String, Any?>): PersistenceEnum =
        PersistenceEnum(graphqlName = yaml.yamlString("graphqlName"), values = yaml.yamlStringList("values"))

    private fun attributeToYaml(attribute: PersistenceAttribute): Map<String, Any?> =
        when (attribute) {
            is PersistenceBasicAttribute ->
                mapOf(
                    "kind" to "basic",
                    "name" to attribute.name,
                    "nullable" to attribute.nullable,
                    "kotlinType" to attribute.kotlinType,
                    "enumTypeName" to attribute.enumTypeName,
                    "collection" to attribute.collection,
                    "elementNullable" to attribute.elementNullable,
                    "columnDefinition" to attribute.columnDefinition,
                )
            is PersistenceToOneAttribute ->
                mapOf(
                    "kind" to "toOne",
                    "name" to attribute.name,
                    "nullable" to attribute.nullable,
                    "targetTypeName" to attribute.targetTypeName,
                    "idOfDirected" to attribute.idOfDirected,
                )
            is PersistenceToManyAttribute ->
                mapOf(
                    "kind" to "toMany",
                    "name" to attribute.name,
                    "nullable" to attribute.nullable,
                    "targetTypeName" to attribute.targetTypeName,
                    "inverseFieldName" to attribute.inverseFieldName,
                    "storage" to attribute.storage.name,
                    "joinTableName" to attribute.joinTableName,
                    "edgeMapping" to attribute.edgeMapping?.let(::edgeMappingToYaml),
                )
        }

    private fun attributeFromYaml(yaml: Map<String, Any?>): PersistenceAttribute =
        when (val kind = yaml.yamlString("kind")) {
            "basic" ->
                PersistenceBasicAttribute(
                    name = yaml.yamlString("name"),
                    nullable = yaml.yamlBoolean("nullable"),
                    kotlinType = yaml.yamlString("kotlinType"),
                    enumTypeName = yaml["enumTypeName"] as String?,
                    collection = yaml.yamlBoolean("collection"),
                    elementNullable = yaml.yamlBoolean("elementNullable"),
                    columnDefinition = yaml["columnDefinition"] as String?,
                )
            "toOne" ->
                PersistenceToOneAttribute(
                    name = yaml.yamlString("name"),
                    nullable = yaml.yamlBoolean("nullable"),
                    targetTypeName = yaml.yamlString("targetTypeName"),
                    idOfDirected = yaml.yamlBoolean("idOfDirected"),
                )
            "toMany" ->
                PersistenceToManyAttribute(
                    name = yaml.yamlString("name"),
                    nullable = yaml.yamlBoolean("nullable"),
                    targetTypeName = yaml.yamlString("targetTypeName"),
                    inverseFieldName = yaml["inverseFieldName"] as String?,
                    storage = PersistenceToManyStorage.valueOf(yaml.yamlString("storage")),
                    joinTableName = yaml["joinTableName"] as String?,
                    edgeMapping = yaml.yamlMapOrNull("edgeMapping")?.let(::edgeMappingFromYaml),
                )
            else -> error("Unknown persistence attribute kind: $kind")
        }

    private fun edgeMappingToYaml(edgeMapping: PersistenceEdgeMapping): Map<String, Any?> =
        mapOf(
            "typeName" to edgeMapping.typeName,
            "attributes" to edgeMapping.attributes.map(::attributeToYaml),
        )

    private fun edgeMappingFromYaml(yaml: Map<String, Any?>): PersistenceEdgeMapping =
        PersistenceEdgeMapping(
            typeName = yaml.yamlString("typeName"),
            attributes = yaml.yamlMapList("attributes").map(::attributeFromYaml),
        )
}
