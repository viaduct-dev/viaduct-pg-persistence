package dev.viaduct.persistence.hibernate

import dev.viaduct.persistence.model.PersistenceAssociation
import dev.viaduct.persistence.model.PersistenceEntity
import org.hibernate.boot.Metadata
import org.hibernate.mapping.Collection
import org.hibernate.mapping.PersistentClass

/** Provides dynamic entity-name and collection lookups shared by model projections. */
internal class HibernateModelContext(
    val metadata: Metadata,
    val semanticModel: dev.viaduct.persistence.model.PersistenceModel,
) {
    private val bindingsByEntityName =
        metadata.entityBindings
            .associateBy { binding -> binding.entityName }

    fun className(graphqlTypeName: String): String = graphqlTypeName

    fun bindingFor(entity: PersistenceEntity): PersistentClass = bindingFor(entity.graphqlName)

    fun bindingFor(graphqlTypeName: String): PersistentClass =
        requireNotNull(bindingsByEntityName[graphqlTypeName]) {
            "Hibernate metadata does not contain dynamic entity $graphqlTypeName"
        }

    fun collectionFor(
        ownerTypeName: String,
        fieldName: String,
    ): Collection {
        val role = "$ownerTypeName.$fieldName"
        return requireNotNull(metadata.getCollectionBinding(role)) {
            "Hibernate metadata does not contain collection $role"
        }
    }

    fun associationFor(
        ownerTypeName: String,
        fieldName: String,
    ): PersistenceAssociation =
        requireNotNull(
            semanticModel.associations.singleOrNull {
                it.ownerTypeName == ownerTypeName && it.fieldName == fieldName
            },
        ) {
            "Semantic model does not contain association $ownerTypeName.$fieldName"
        }

    fun associationBindingFor(
        ownerTypeName: String,
        fieldName: String,
    ): PersistentClass {
        val association = associationFor(ownerTypeName, fieldName)
        return requireNotNull(bindingsByEntityName[association.typeName]) {
            "Hibernate metadata does not contain dynamic association ${association.typeName}"
        }
    }
}
