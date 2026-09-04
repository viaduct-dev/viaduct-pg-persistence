package dev.viaduct.persistence.hibernate

import dev.viaduct.persistence.model.PersistenceModel
import java.io.File

/**
 * The in-memory inputs used to build the Hibernate metadata for a persistence task.
 *
 * [mappingFile], [classpath], and [managedClassNames] are mechanical inputs derived from a build
 * or a generated mapping file. All three default to the plugin's own conventions — the mapping
 * file the Gradle tasks generate, the current JVM's classpath, and the managed classes declared
 * in that mapping file — so a caller with an already-generated mapping at the conventional
 * location can build a configuration with no arguments at all. The remaining parameters are
 * policy knobs with sensible defaults; override only the ones you need to change.
 */
@Suppress("LongParameterList")
class HibernateMetadataConfiguration(
    val mappingFile: File = defaultMappingFile(),
    classpath: List<File> = defaultClasspath(),
    managedClassNames: List<String> = managedClassNamesIn(mappingFile),
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

        /** The mapping file location the plugin's own Gradle tasks generate. */
        private const val DEFAULT_MAPPING_FILE_PATH = "build/generated/viaduct-persistence/resources/META-INF/orm.xml"

        fun defaultSettings(): Map<String, String> =
            mapOf(
                "hibernate.boot.allow_jdbc_metadata_access" to "false",
                "hibernate.temp.use_jdbc_metadata_defaults" to "false",
            )

        /** The plugin's conventional mapping file location, relative to the working directory. */
        fun defaultMappingFile(): File = File(DEFAULT_MAPPING_FILE_PATH)

        /** The current JVM's classpath, filtered to entries that exist. */
        fun defaultClasspath(): List<File> =
            System
                .getProperty("java.class.path")
                .split(File.pathSeparator)
                .map(::File)
                .filter(File::exists)

        /**
         * Reads the managed class names out of an already-generated Hibernate `orm.xml` mapping
         * file, for callers building a configuration around a mapping file rather than a live
         * [PersistenceModel].
         */
        fun managedClassNamesIn(mappingFile: File): List<String> {
            require(mappingFile.isFile) {
                "Hibernate mapping file does not exist: ${mappingFile.absolutePath}"
            }
            return Regex("""class="([^"]+)"""")
                .findAll(mappingFile.readText())
                .map { it.groupValues[1] }
                .distinct()
                .toList()
        }
    }
}
