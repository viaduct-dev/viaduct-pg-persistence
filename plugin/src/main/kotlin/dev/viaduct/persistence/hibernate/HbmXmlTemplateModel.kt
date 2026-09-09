package dev.viaduct.persistence.hibernate

/** Presentation-only model for the StringTemplate-backed HBM document. */
internal class HbmXmlTemplateModel(
    entities: List<HbmEntityTemplate>,
) {
    val entities: List<HbmEntityTemplate> = java.util.List.copyOf(entities)

    companion object {
        fun from(mapping: HbmMappingDocument) = HbmXmlTemplateModel(mapping.entities.map(HbmEntityTemplate::from))
    }
}

internal class HbmEntityTemplate(
    val entityName: String,
    val tableName: String,
    val schemaName: String?,
    attributes: List<HbmAttributeTemplate>,
) {
    val attributes: List<HbmAttributeTemplate> = java.util.List.copyOf(attributes)

    companion object {
        fun from(entity: HbmEntityMapping) =
            HbmEntityTemplate(
                entityName = entity.entityName.xmlAttribute(),
                tableName = entity.tableName.xmlAttribute(),
                schemaName = entity.schemaName?.xmlAttribute(),
                attributes = entity.attributes.map(HbmAttributeTemplate::from),
            )
    }
}

internal data class HbmAttributeTemplate(
    val basic: HbmBasicTemplate? = null,
    val toOne: HbmToOneTemplate? = null,
    val toMany: HbmToManyTemplate? = null,
) {
    companion object {
        fun from(attribute: HbmAttributeMapping) =
            when (attribute) {
                is HbmBasicMapping -> HbmAttributeTemplate(basic = HbmBasicTemplate.from(attribute))
                is HbmToOneMapping -> HbmAttributeTemplate(toOne = HbmToOneTemplate.from(attribute))
                is HbmToManyMapping -> HbmAttributeTemplate(toMany = HbmToManyTemplate.from(attribute))
            }
    }
}

internal data class HbmBasicTemplate(
    val elementName: String,
    val name: String,
    val hibernateType: String,
    val columnName: String,
    val notNull: Boolean,
    val primaryKey: Boolean,
    val noInsert: Boolean,
    val noUpdate: Boolean,
    val columnDefinition: String?,
) {
    companion object {
        fun from(attribute: HbmBasicMapping) =
            HbmBasicTemplate(
                elementName = if (attribute.primaryKey) "id" else "property",
                name = attribute.name.xmlAttribute(),
                hibernateType = attribute.hibernateType.xmlAttribute(),
                columnName = attribute.columnName.xmlAttribute(),
                notNull = !attribute.nullable,
                primaryKey = attribute.primaryKey,
                noInsert = !attribute.insertable,
                noUpdate = !attribute.updatable,
                columnDefinition = attribute.columnDefinition?.xmlAttribute(),
            )
    }
}

internal data class HbmToOneTemplate(
    val name: String,
    val targetEntityName: String,
    val columnName: String,
    val notNull: Boolean,
    val foreignKeyName: String,
) {
    companion object {
        fun from(attribute: HbmToOneMapping) =
            HbmToOneTemplate(
                name = attribute.name.xmlAttribute(),
                targetEntityName = attribute.targetEntityName.xmlAttribute(),
                columnName = attribute.columnName.xmlAttribute(),
                notNull = !attribute.nullable,
                foreignKeyName = attribute.foreignKeyName.xmlAttribute(),
            )
    }
}

internal data class HbmToManyTemplate(
    val name: String,
    val targetEntityName: String,
    val keyColumnName: String,
    val inverse: Boolean,
    val joinTableName: String?,
    val joinSchemaName: String?,
    val oneToMany: Boolean,
    val targetColumnName: String?,
    val targetForeignKeyName: String?,
) {
    companion object {
        fun from(attribute: HbmToManyMapping) =
            HbmToManyTemplate(
                name = attribute.name.xmlAttribute(),
                targetEntityName = attribute.targetEntityName.xmlAttribute(),
                keyColumnName = attribute.keyColumnName.xmlAttribute(),
                inverse = attribute.inverse,
                joinTableName = attribute.joinTableName?.xmlAttribute(),
                joinSchemaName = attribute.joinSchemaName?.xmlAttribute(),
                oneToMany = attribute.targetColumnName == null,
                targetColumnName = attribute.targetColumnName?.xmlAttribute(),
                targetForeignKeyName = attribute.targetForeignKeyName?.xmlAttribute(),
            )
    }
}

private fun String.xmlAttribute(): String =
    replace("&", "&amp;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
