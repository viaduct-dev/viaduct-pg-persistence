package dev.viaduct.persistence.liquibase

import dev.viaduct.persistence.hibernate.EffectiveHibernateModel
import dev.viaduct.persistence.hibernate.EffectiveHibernateModelBuilder
import dev.viaduct.persistence.hibernate.HibernateMetadataBootstrap
import dev.viaduct.persistence.hibernate.HibernateMetadataConfiguration
import dev.viaduct.persistence.hibernate.HibernateMetadataHandle
import dev.viaduct.persistence.pggraphql.overlay.PgGraphqlConstraintRenderer
import liquibase.database.DatabaseConnection
import liquibase.exception.DatabaseException
import liquibase.ext.hibernate.database.HibernateDatabase
import org.hibernate.boot.Metadata
import org.hibernate.boot.MetadataSources
import java.io.File

private typealias LiquibaseConnection = DatabaseConnection

class ViaductHibernateDatabase : HibernateDatabase() {
    private var metadataHandle: HibernateMetadataHandle? = null
    private var effectiveModel: EffectiveHibernateModel? = null

    /** The desired pg_graphql `@graphql({...})` comment for a foreign-key column, if known. */
    fun pgGraphqlConstraintComment(
        schemaName: String,
        tableName: String,
        columnName: String,
    ): String? =
        effectiveModel?.let {
            PgGraphqlConstraintRenderer.commentValue(it, schemaName, tableName, columnName)
        }

    override fun isCorrectDatabaseImplementation(connection: LiquibaseConnection): Boolean {
        val url = connection.url
        return url.startsWith(URL_PREFIX)
    }

    override fun getShortName(): String = "hibernateViaduct"

    override fun getDefaultDatabaseProductName(): String = "Hibernate Viaduct"

    override fun buildMetadataFromPath(): Metadata {
        val path = referencePath()
        val configuration =
            runCatching { HibernateMetadataReferences.resolve(path) }
                .getOrElse { failure ->
                    throw DatabaseException(
                        "Unable to resolve Viaduct Hibernate metadata reference $path",
                        failure,
                    )
                }

        return runCatching {
            metadataHandle?.close()
            metadataHandle = null
            effectiveModel = null
            HibernateMetadataBootstrap
                .build(configuration)
                .also { handle ->
                    metadataHandle = handle
                    dialect = handle.metadata.database.jdbcEnvironment.dialect
                    effectiveModel = buildEffectiveModel(configuration, handle)
                }.metadata
        }.getOrElse { failure ->
            throw DatabaseException(
                "Unable to build Hibernate metadata for reference $path",
                failure,
            )
        }
    }

    private fun buildEffectiveModel(
        configuration: HibernateMetadataConfiguration,
        handle: HibernateMetadataHandle,
    ): EffectiveHibernateModel? {
        val semanticModel = configuration.semanticModel
        val packageName = configuration.packageName
        if (semanticModel == null || packageName == null) return null
        return EffectiveHibernateModelBuilder.build(handle.metadata, semanticModel, packageName)
    }

    override fun configureSources(sources: MetadataSources) = Unit

    override fun close() {
        val path = referencePathOrNull()
        try {
            super.close()
        } finally {
            try {
                metadataHandle?.close()
            } finally {
                metadataHandle = null
                effectiveModel = null
                path?.let { File(it).delete() }
            }
        }
    }

    private fun referencePath(): String =
        referencePathOrNull()
            ?: throw DatabaseException("Invalid Viaduct Hibernate metadata reference URL")

    private fun referencePathOrNull(): String? =
        runCatching {
            hibernateConnection.url
                .takeIf { it.startsWith(URL_PREFIX) }
                ?.removePrefix(URL_PREFIX)
                ?.takeIf(String::isNotBlank)
        }.getOrNull()

    companion object {
        const val URL_PREFIX = HIBERNATE_VIADUCT_URL_PREFIX

        fun reference(configuration: HibernateMetadataConfiguration): HibernateMetadataReference =
            HibernateMetadataReferences.create(configuration)
    }
}
