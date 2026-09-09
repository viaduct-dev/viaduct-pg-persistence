package dev.viaduct.persistence.hibernate

import dev.viaduct.persistence.model.PersistenceModel
import org.w3c.dom.Document
import org.w3c.dom.Element

/** Formats a normalized [HbmMappingDocument] as native Hibernate XML. */
internal class HbmXmlWriter {
    fun document(
        model: PersistenceModel,
        associationSchemaName: String,
    ): Document = document(PersistenceModelToHbmMapper.map(model, associationSchemaName))

    internal fun document(mapping: HbmMappingDocument): Document {
        val document = HibernateXmlDocuments.newDocument()
        val mappings =
            document
                .createElementNS(HibernateXmlDocuments.HBM_NS, "hibernate-mapping")
                .apply { setAttribute("schema", "public") }
        document.appendChild(mappings)
        mapping.entities.forEach { writeEntity(mappings, it) }
        return document
    }

    private fun writeEntity(
        mappings: Element,
        entity: HbmEntityMapping,
    ) {
        val mapped =
            mappings.child("class").apply {
                setAttribute("entity-name", entity.entityName)
                setAttribute("table", entity.tableName)
                entity.schemaName?.let { setAttribute("schema", it) }
                setAttribute("lazy", "false")
            }
        entity.attributes.forEach { writeAttribute(mapped, it) }
    }

    private fun writeAttribute(
        entity: Element,
        attribute: HbmAttributeMapping,
    ) {
        when (attribute) {
            is HbmBasicMapping -> writeBasic(entity, attribute)
            is HbmToOneMapping -> writeToOne(entity, attribute)
            is HbmToManyMapping -> writeToMany(entity, attribute)
        }
    }

    private fun writeBasic(
        entity: Element,
        attribute: HbmBasicMapping,
    ) {
        val property =
            entity.child(if (attribute.primaryKey) "id" else "property").apply {
                setAttribute("name", attribute.name)
                setAttribute("type", attribute.hibernateType)
                if (!attribute.primaryKey) setAttribute("not-null", (!attribute.nullable).toString())
                if (!attribute.insertable) setAttribute("insert", "false")
                if (!attribute.updatable) setAttribute("update", "false")
            }
        property.child("column").apply {
            setAttribute("name", attribute.columnName)
            setAttribute("not-null", (!attribute.nullable).toString())
            attribute.columnDefinition?.let { setAttribute("sql-type", it) }
        }
        if (attribute.primaryKey) property.child("generator").setAttribute("class", "assigned")
    }

    private fun writeToOne(
        entity: Element,
        attribute: HbmToOneMapping,
    ) {
        entity.child("many-to-one").apply {
            setAttribute("name", attribute.name)
            setAttribute("entity-name", attribute.targetEntityName)
            setAttribute("not-null", (!attribute.nullable).toString())
            setAttribute("lazy", "proxy")
            setAttribute("foreign-key", attribute.foreignKeyName)
            child("column").apply {
                setAttribute("name", attribute.columnName)
                setAttribute("not-null", (!attribute.nullable).toString())
                setAttribute("sql-type", "uuid")
            }
        }
    }

    private fun writeToMany(
        entity: Element,
        attribute: HbmToManyMapping,
    ) {
        val collection =
            entity.child("bag").apply {
                setAttribute("name", attribute.name)
                setAttribute("lazy", "true")
                if (attribute.inverse) setAttribute("inverse", "true")
                attribute.joinTableName?.let { setAttribute("table", it) }
                attribute.joinSchemaName?.let { setAttribute("schema", it) }
            }
        collection.child("key").apply {
            setAttribute("column", attribute.keyColumnName)
            setAttribute("not-null", "true")
        }
        if (attribute.targetColumnName == null) {
            collection.child("one-to-many").setAttribute("entity-name", attribute.targetEntityName)
        } else {
            collection.child("many-to-many").apply {
                setAttribute("entity-name", attribute.targetEntityName)
                setAttribute("foreign-key", requireNotNull(attribute.targetForeignKeyName))
                setAttribute("column", attribute.targetColumnName)
            }
        }
    }
}
