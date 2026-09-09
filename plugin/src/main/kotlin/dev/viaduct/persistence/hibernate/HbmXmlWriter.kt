package dev.viaduct.persistence.hibernate

import dev.viaduct.persistence.model.PersistenceAssociation
import dev.viaduct.persistence.model.PersistenceAttribute
import dev.viaduct.persistence.model.PersistenceBasicAttribute
import dev.viaduct.persistence.model.PersistenceEntity
import dev.viaduct.persistence.model.PersistenceModel
import dev.viaduct.persistence.model.PersistenceToManyAttribute
import dev.viaduct.persistence.model.PersistenceToManyStorage
import dev.viaduct.persistence.model.PersistenceToOneAttribute
import dev.viaduct.persistence.model.associationJoinColumnName
import org.w3c.dom.Document
import org.w3c.dom.Element

/**
 * Creates native Hibernate mappings whose entities use the dynamic-map representation.
 *
 * The GraphQL/GRT type and field names are also the Hibernate entity and property names. No
 * parallel Java or Kotlin entity classes are required.
 */
internal class HbmXmlWriter {
    fun document(
        model: PersistenceModel,
        associationSchemaName: String,
    ): Document {
        val document = HibernateXmlDocuments.newDocument()
        val mappings =
            document
                .createElementNS(HibernateXmlDocuments.HBM_NS, "hibernate-mapping")
                .apply { setAttribute("schema", "public") }
        document.appendChild(mappings)
        model.entities.forEach { writeEntity(mappings, it, associationSchemaName) }
        model.associations.forEach { writeAssociation(mappings, it, associationSchemaName) }
        return document
    }

    private fun writeEntity(
        mappings: Element,
        entity: PersistenceEntity,
        associationSchemaName: String,
    ) {
        val mapped =
            mappings.child("class").apply {
                setAttribute("entity-name", entity.graphqlName)
                setAttribute("table", entity.graphqlName)
                setAttribute("lazy", "false")
            }
        entity.attributes.forEach { writeAttribute(mapped, entity, it, associationSchemaName) }
    }

    private fun writeAssociation(
        mappings: Element,
        association: PersistenceAssociation,
        associationSchemaName: String,
    ) {
        val mapped =
            mappings.child("class").apply {
                setAttribute("entity-name", association.typeName)
                setAttribute("table", association.tableName)
                setAttribute("schema", associationSchemaName)
                setAttribute("lazy", "false")
            }
        mapped.child("id").apply {
            setAttribute("name", "internalId")
            setAttribute("type", "uuid")
            child("column").apply {
                setAttribute("name", "_viaduct_id")
                setAttribute("not-null", "true")
                setAttribute("sql-type", "uuid default gen_random_uuid()")
            }
            child("generator").setAttribute("class", "assigned")
        }
        writeToOne(
            mapped,
            association.typeName,
            PersistenceToOneAttribute("owner", false, association.ownerTypeName),
            association.ownerColumnName,
        )
        writeToOne(
            mapped,
            association.typeName,
            PersistenceToOneAttribute("node", false, association.targetTypeName),
            association.targetColumnName,
        )
        val semanticEntity = PersistenceEntity(association.typeName, true, emptyList())
        association.edgeMapping.attributes.forEach {
            writeAttribute(mapped, semanticEntity, it, associationSchemaName)
        }
    }

    private fun writeAttribute(
        mapped: Element,
        entity: PersistenceEntity,
        attribute: PersistenceAttribute,
        associationSchemaName: String,
    ) {
        when (attribute) {
            is PersistenceBasicAttribute -> writeBasic(mapped, entity, attribute)
            is PersistenceToOneAttribute -> writeToOne(mapped, entity.graphqlName, attribute)
            is PersistenceToManyAttribute -> writeToMany(mapped, entity, attribute, associationSchemaName)
        }
    }

    private fun writeBasic(
        mapped: Element,
        entity: PersistenceEntity,
        attribute: PersistenceBasicAttribute,
    ) {
        val primaryKey =
            attribute.name == "internalId" || (!entity.generatedGlobalId && attribute.name == "id")
        val property =
            mapped.child(if (primaryKey) "id" else "property").apply {
                setAttribute("name", attribute.name)
                setAttribute("type", hibernateType(attribute))
                if (!primaryKey) setAttribute("not-null", (!attribute.nullable).toString())
                if (entity.generatedGlobalId && attribute.name == "id") {
                    setAttribute("insert", "false")
                    setAttribute("update", "false")
                }
            }
        property.child("column").apply {
            setAttribute("name", attribute.name)
            setAttribute("not-null", (!attribute.nullable).toString())
            columnDefinition(entity, attribute, primaryKey)?.let { setAttribute("sql-type", it) }
        }
        if (primaryKey) property.child("generator").setAttribute("class", "assigned")
    }

    private fun writeToOne(
        mapped: Element,
        ownerTypeName: String,
        attribute: PersistenceToOneAttribute,
        columnName: String = if (attribute.idOfDirected) attribute.name else "${attribute.name}Id",
    ) {
        mapped.child("many-to-one").apply {
            setAttribute("name", attribute.name)
            setAttribute("entity-name", attribute.targetTypeName)
            setAttribute("not-null", (!attribute.nullable).toString())
            setAttribute("lazy", "proxy")
            setAttribute("foreign-key", "FK_${ownerTypeName}_${attribute.name}")
            child("column").apply {
                setAttribute("name", columnName)
                setAttribute("not-null", (!attribute.nullable).toString())
                setAttribute("sql-type", "uuid")
            }
        }
    }

    private fun writeToMany(
        mapped: Element,
        entity: PersistenceEntity,
        attribute: PersistenceToManyAttribute,
        associationSchemaName: String,
    ) {
        val collection =
            mapped.child("bag").apply {
                setAttribute("name", attribute.name)
                setAttribute("lazy", "true")
                if (
                    attribute.inverseFieldName != null ||
                    attribute.storage == PersistenceToManyStorage.JOIN_TABLE_INVERSE
                ) {
                    setAttribute("inverse", "true")
                }
                if (attribute.storage != PersistenceToManyStorage.TARGET_FOREIGN_KEY) {
                    setAttribute("table", requireNotNull(attribute.joinTableName))
                    setAttribute("schema", associationSchemaName)
                }
            }
        when (attribute.storage) {
            PersistenceToManyStorage.TARGET_FOREIGN_KEY -> {
                collection.child("key").apply {
                    setAttribute("column", "${entity.graphqlName.replaceFirstChar(Char::lowercaseChar)}Id")
                    setAttribute("not-null", "true")
                }
                collection.child("one-to-many").setAttribute("entity-name", attribute.targetTypeName)
            }
            PersistenceToManyStorage.JOIN_TABLE_OWNER,
            PersistenceToManyStorage.JOIN_TABLE_INVERSE,
            -> {
                val selfReferential = entity.graphqlName == attribute.targetTypeName
                collection.child("key").apply {
                    setAttribute(
                        "column",
                        associationJoinColumnName(entity.graphqlName, "owner", selfReferential),
                    )
                    setAttribute("not-null", "true")
                }
                collection.child("many-to-many").apply {
                    setAttribute("entity-name", attribute.targetTypeName)
                    setAttribute("foreign-key", "FK_${entity.graphqlName}_${attribute.name}_target")
                    setAttribute(
                        "column",
                        associationJoinColumnName(attribute.targetTypeName, "target", selfReferential),
                    )
                }
            }
        }
    }

    private fun hibernateType(attribute: PersistenceBasicAttribute): String =
        when {
            attribute.enumTypeName != null -> "string"
            // With no POJO property Hibernate cannot infer an array JavaType. Schema management
            // only needs the explicit PostgreSQL column definition; semantic array information is
            // retained in PersistenceModel and projected into the effective model separately.
            attribute.collection -> "string"
            attribute.kotlinType == "String" -> "string"
            attribute.kotlinType == "Boolean" -> "boolean"
            attribute.kotlinType == "Byte" -> "byte"
            attribute.kotlinType == "Short" -> "short"
            attribute.kotlinType == "Int" -> "integer"
            attribute.kotlinType == "Long" -> "long"
            attribute.kotlinType == "Double" -> "double"
            attribute.kotlinType == "java.util.UUID" -> "uuid"
            else -> attribute.kotlinType
        }

    private fun columnDefinition(
        entity: PersistenceEntity,
        attribute: PersistenceBasicAttribute,
        primaryKey: Boolean,
    ): String? =
        when {
            attribute.name == "internalId" || primaryKey && attribute.kotlinType == "java.util.UUID" ->
                "uuid default gen_random_uuid()"
            entity.generatedGlobalId && attribute.name == "id" -> "text"
            attribute.columnDefinition != null -> attribute.columnDefinition
            else -> null
        }
}
