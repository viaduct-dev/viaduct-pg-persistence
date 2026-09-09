package dev.viaduct.persistence.hibernate

internal class HbmMappingDocument(
    entities: List<HbmEntityMapping>,
) {
    val entities: List<HbmEntityMapping> = java.util.List.copyOf(entities)
}

internal class HbmEntityMapping(
    val entityName: String,
    val tableName: String,
    val schemaName: String? = null,
    attributes: List<HbmAttributeMapping>,
) {
    val attributes: List<HbmAttributeMapping> = java.util.List.copyOf(attributes)
}

internal sealed interface HbmAttributeMapping {
    val name: String
    val basic: HbmBasicMapping?
        get() = this as? HbmBasicMapping
    val toOne: HbmToOneMapping?
        get() = this as? HbmToOneMapping
    val toMany: HbmToManyMapping?
        get() = this as? HbmToManyMapping
}

internal data class HbmBasicMapping(
    override val name: String,
    val hibernateType: String,
    val columnName: String,
    val nullable: Boolean,
    val primaryKey: Boolean,
    val insertable: Boolean = true,
    val updatable: Boolean = true,
    val columnDefinition: String? = null,
) : HbmAttributeMapping {
    val elementName: String = if (primaryKey) "id" else "property"
    val notNull: Boolean = !nullable
    val noInsert: Boolean = !insertable
    val noUpdate: Boolean = !updatable
}

internal data class HbmToOneMapping(
    override val name: String,
    val targetEntityName: String,
    val columnName: String,
    val nullable: Boolean,
    val foreignKeyName: String,
) : HbmAttributeMapping {
    val notNull: Boolean = !nullable
}

internal data class HbmToManyMapping(
    override val name: String,
    val targetEntityName: String,
    val keyColumnName: String,
    val inverse: Boolean,
    val joinTableName: String? = null,
    val joinSchemaName: String? = null,
    val targetColumnName: String? = null,
    val targetForeignKeyName: String? = null,
) : HbmAttributeMapping {
    val oneToMany: Boolean = targetColumnName == null
}
