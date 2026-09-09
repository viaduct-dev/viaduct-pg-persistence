package dev.viaduct.persistence.hibernate

import dev.viaduct.persistence.model.PersistenceModel
import org.hibernate.boot.Metadata

/** Builds the normalized model consumed by the PostgreSQL and pg_graphql overlays. */
object EffectiveHibernateModelBuilder {
    fun build(
        metadata: Metadata,
        semanticModel: PersistenceModel,
    ): EffectiveHibernateModel =
        EffectiveHibernateModelAssembler(
            HibernateModelContext(metadata, semanticModel),
        ).build()
}

private class EffectiveHibernateModelAssembler(
    private val context: HibernateModelContext,
) {
    private val entityProjector = EffectiveHibernateEntityProjector(context)
    private val relationshipProjector = EffectiveHibernateRelationshipProjector(context)
    private val arrayProjector = EffectiveHibernateArrayProjector(context)

    fun build(): EffectiveHibernateModel {
        HibernateSemanticModelValidator(context).validate()
        val relationshipProjection = relationshipProjector.project()
        return EffectiveHibernateModel(
            entities =
                context.semanticModel.entities
                    .map(entityProjector::project)
                    .sortedBy(EffectiveHibernateEntity::graphqlName),
            relationships =
                relationshipProjection.relationships.sortedWith(
                    compareBy(
                        EffectiveHibernateRelationship::ownerTypeName,
                        EffectiveHibernateRelationship::fieldName,
                    ),
                ),
            computedRelationships =
                relationshipProjection.computedRelationships.sortedWith(
                    compareBy(
                        EffectiveHibernateComputedRelationship::ownerTypeName,
                        EffectiveHibernateComputedRelationship::fieldName,
                    ),
                ),
            arrays =
                arrayProjector.project().sortedWith(
                    compareBy(
                        EffectiveHibernateArray::ownerTypeName,
                        EffectiveHibernateArray::fieldName,
                    ),
                ),
        )
    }
}
