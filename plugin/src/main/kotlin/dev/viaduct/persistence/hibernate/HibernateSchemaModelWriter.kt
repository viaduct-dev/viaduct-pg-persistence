package dev.viaduct.persistence.hibernate

import dev.viaduct.persistence.io.ensureDirectory
import dev.viaduct.persistence.model.PersistenceEntity
import dev.viaduct.persistence.model.PersistenceModel
import java.io.File

/** Inputs used when generating the Hibernate source and mapping artifacts. */
data class HibernateSchemaModelWriteRequest(
    val model: PersistenceModel,
    val outputDirectory: File,
    val packageName: String,
    val options: HibernateSchemaModelWriteOptions = HibernateSchemaModelWriteOptions(),
)

/** Optional names and replacement mapping used by [HibernateSchemaModelWriteRequest]. */
data class HibernateSchemaModelWriteOptions(
    val persistenceUnitName: String = HibernateSchemaModelWriter.DEFAULT_PERSISTENCE_UNIT,
    val replacementOrmXml: File? = null,
    val associationSchemaName: String = HibernateSchemaModelWriter.DEFAULT_ASSOCIATION_SCHEMA,
)

class HibernateSchemaModelWriter {
    private val sourceWriter = GeneratedEntitySourceWriter()
    private val persistenceWriter = PersistenceXmlWriter()
    private val ormWriter = OrmXmlWriter()

    fun write(request: HibernateSchemaModelWriteRequest) {
        val model = request.model
        val outputDirectory = request.outputDirectory
        val packageName = request.packageName
        val options = request.options
        outputDirectory.deleteRecursively()
        val kotlinDirectory = outputDirectory.resolve("kotlin/${packageName.replace('.', '/')}")
        val resourcesDirectory = outputDirectory.resolve("resources/META-INF")
        kotlinDirectory.ensureDirectory()
        resourcesDirectory.ensureDirectory()
        sourceWriter.write(model, outputDirectory.resolve("kotlin"), packageName)
        HibernateXmlDocuments.write(
            persistenceWriter.document(model, packageName, options.persistenceUnitName),
            resourcesDirectory.resolve("persistence.xml"),
        )
        resourcesDirectory.resolve("viaduct-persistence-semantic-not-null.txt").writeText(
            model.semanticNotNullCoordinates.sorted().joinToString(separator = "\n", postfix = "\n"),
        )
        val mappingDestination = resourcesDirectory.resolve("orm.xml")
        if (options.replacementOrmXml == null) {
            HibernateXmlDocuments.write(
                ormWriter.document(model, packageName, options.associationSchemaName),
                mappingDestination,
            )
        } else {
            options.replacementOrmXml.copyTo(mappingDestination)
        }
    }

    /** Compatibility overload for callers that pass generation options individually. */
    @Suppress("LongParameterList")
    fun write(
        model: PersistenceModel,
        outputDirectory: File,
        packageName: String,
        persistenceUnitName: String = DEFAULT_PERSISTENCE_UNIT,
        replacementOrmXml: File? = null,
        associationSchemaName: String = DEFAULT_ASSOCIATION_SCHEMA,
    ) = write(
        HibernateSchemaModelWriteRequest(
            model = model,
            outputDirectory = outputDirectory,
            packageName = packageName,
            options =
                HibernateSchemaModelWriteOptions(
                    persistenceUnitName = persistenceUnitName,
                    replacementOrmXml = replacementOrmXml,
                    associationSchemaName = associationSchemaName,
                ),
        ),
    )

    fun renderEntity(
        entity: PersistenceEntity,
        packageName: String,
    ): String = sourceWriter.renderEntity(entity, packageName)

    companion object {
        const val DEFAULT_PERSISTENCE_UNIT = "gateloom-schema"
        const val DEFAULT_ASSOCIATION_SCHEMA = "viaduct_internal"
    }
}
