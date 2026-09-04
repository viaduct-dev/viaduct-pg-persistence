package dev.viaduct.persistence.hibernate

import dev.viaduct.persistence.model.PersistenceModel
import java.io.File

/**
 * The in-memory inputs used to build the Hibernate metadata for a persistence task.
 *
 * [mappingFile], [classpath], and [managedClassNames] are mechanical inputs derived from a build
 * or a generated mapping file. The remaining parameters are policy knobs with sensible defaults;
 * override only the ones you need to change.
 */
@Suppress("LongParameterList")
class HibernateMetadataConfiguration(
    val mappingFile: File,
    classpath: List<File>,
    managedClassNames: List<String>,
    val implicitNamingStrategyClassName: String = ViaductImplicitNamingStrategy::class.java.name,
    val physicalNamingStrategyClassName: String = ViaductPhysicalNamingStrategy::class.java.name,
    metadataCustomizerClassNames: List<String> = emptyList(),
    val dialectClassName: String = DEFAULT_DIALECT,
    hibernateSettings: Map<String, String> = defaultSettings(),
    /** The semantic model this configuration was derived from, if known. */
    val semanticModel: PersistenceModel? = null,
    /** The generated-entity package name this configuration was derived from, if known. */
    val packageName: String? = null,
) {
    val classpath: List<File> = java.util.List.copyOf(classpath)
    val managedClassNames: List<String> = java.util.List.copyOf(managedClassNames)
    val metadataCustomizerClassNames: List<String> =
        java.util.List.copyOf(metadataCustomizerClassNames)
    val hibernateSettings: Map<String, String> =
        java.util.Collections.unmodifiableMap(java.util.LinkedHashMap(hibernateSettings))

    fun validate() {
        require(mappingFile.isFile) {
            "Hibernate mapping file does not exist: ${mappingFile.absolutePath}"
        }
        require(managedClassNames.isNotEmpty()) {
            "Hibernate metadata configuration must contain at least one managed class"
        }
    }

    companion object {
        const val DEFAULT_DIALECT = "org.hibernate.dialect.PostgreSQLDialect"

        fun defaultSettings(): Map<String, String> =
            mapOf(
                "hibernate.boot.allow_jdbc_metadata_access" to "false",
                "hibernate.temp.use_jdbc_metadata_defaults" to "false",
            )

        /**
         * Reads the managed class names out of an already-generated Hibernate `orm.xml` mapping
         * file, for callers building a configuration around a mapping file rather than a live
         * [PersistenceModel].
         */
        fun managedClassNamesIn(mappingFile: File): List<String> =
            Regex("""class="([^"]+)"""")
                .findAll(mappingFile.readText())
                .map { it.groupValues[1] }
                .distinct()
                .toList()
    }
}
