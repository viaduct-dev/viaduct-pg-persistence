package dev.viaduct.persistence.liquibase

import dev.viaduct.persistence.hibernate.HibernateMetadataConfiguration
import dev.viaduct.persistence.hibernate.HibernateMetadataConfigurationDescriptor
import java.io.File

internal const val HIBERNATE_VIADUCT_URL_PREFIX = "hibernate:viaduct:"

/** A Liquibase reference to a Hibernate metadata configuration, backed by a descriptor file. */
class HibernateMetadataReference internal constructor(
    private val descriptorFile: File,
) : AutoCloseable {
    val url: String = HIBERNATE_VIADUCT_URL_PREFIX + descriptorFile.absolutePath

    override fun close() {
        descriptorFile.delete()
    }

    companion object {
        @Suppress("MaxLineLength")
        fun forDescriptorFile(descriptorFile: File): HibernateMetadataReference = HibernateMetadataReference(descriptorFile)
    }
}

internal object HibernateMetadataReferences {
    fun create(configuration: HibernateMetadataConfiguration): HibernateMetadataReference {
        configuration.validate()
        val descriptorFile = File.createTempFile("hibernate-viaduct-metadata", ".yaml")
        HibernateMetadataConfigurationDescriptor.write(configuration, descriptorFile)
        return HibernateMetadataReference.forDescriptorFile(descriptorFile)
    }

    @Suppress("MaxLineLength")
    fun resolve(path: String): HibernateMetadataConfiguration = HibernateMetadataConfigurationDescriptor.read(File(path))
}
