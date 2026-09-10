package dev.viaduct.persistence.hibernate

import dev.viaduct.persistence.model.PersistenceModel
import dev.viaduct.persistence.model.associationEntityClassName
import dev.viaduct.persistence.model.entityClassName
import java.io.File

/**
 * Builds Hibernate metadata inputs from the semantic persistence model.
 *
 * Only [mappingFile], [classpath], [semanticModel], and [packageName] are mandatory; the
 * remaining fields are policy knobs that default to Viaduct's standard configuration.
 */
@Suppress("LongParameterList")
class HibernateMetadataConfigurationInput(
    val mappingFile: File,
    classpath: List<File>,
    val semanticModel: PersistenceModel,
    val packageName: String,
    val implicitNamingStrategyClassName: String = ViaductImplicitNamingStrategy::class.java.name,
    val physicalNamingStrategyClassName: String = ViaductPhysicalNamingStrategy::class.java.name,
    metadataCustomizerClassNames: List<String> = emptyList(),
    val dialectClassName: String = HibernateMetadataConfiguration.DEFAULT_DIALECT,
    hibernateSettings: Map<String, String> = HibernateMetadataConfiguration.defaultSettings(),
) {
    val classpath: List<File> = java.util.List.copyOf(classpath)
    val metadataCustomizerClassNames: List<String> =
        java.util.List.copyOf(metadataCustomizerClassNames)
    val hibernateSettings: Map<String, String> =
        java.util.Collections.unmodifiableMap(java.util.LinkedHashMap(hibernateSettings))
}

/** Builds a [HibernateMetadataConfiguration] with Viaduct's standard policy defaults. */
object HibernateMetadataConfigurationFactory {
    fun create(input: HibernateMetadataConfigurationInput): HibernateMetadataConfiguration =
        HibernateMetadataConfiguration(
            mappingFile = input.mappingFile,
            classpath = input.classpath,
            managedClassNames = managedClassNames(input),
            implicitNamingStrategyClassName = input.implicitNamingStrategyClassName,
            physicalNamingStrategyClassName = input.physicalNamingStrategyClassName,
            metadataCustomizerClassNames = input.metadataCustomizerClassNames,
            dialectClassName = input.dialectClassName,
            hibernateSettings = input.hibernateSettings,
            semanticModel = input.semanticModel,
            packageName = input.packageName,
        )

    private fun managedClassNames(input: HibernateMetadataConfigurationInput): List<String> =
        buildList {
            input.semanticModel.entities.forEach { entity ->
                add("${input.packageName}.${entityClassName(entity.graphqlName)}")
            }
            input.semanticModel.associations.forEach { association ->
                add(
                    "${input.packageName}.${associationEntityClassName(
                        association.ownerTypeName,
                        association.fieldName,
                    )}",
                )
            }
        }
}
