package dev.viaduct.persistence.hibernate

import dev.viaduct.persistence.model.PersistenceModel
import java.io.File

/**
 * The in-memory inputs used to build the Hibernate metadata for a persistence task.
 *
 * [mappingFile], [classpath], and [managedClassNames] have no default here on purpose: a process
 * that builds configurations for more than one mapping file (tests generating several
 * scenario-specific models are the common case) must not have a missing override silently fall
 * back to an unrelated file that happens to exist. Use [default] for the common single-mapping-
 * file case instead. The remaining parameters are policy knobs with sensible defaults; override
 * only the ones you need to change.
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

        /**
         * Builds a configuration from the plugin's own conventions: [defaultMappingFile],
         * [defaultClasspath], and the managed classes declared in that mapping file.
         *
         * Only use this when a single mapping file exists at the conventional location for the
         * lifetime of this process. A caller building configurations for more than one mapping
         * file — for example, a test that generates several scenario-specific models — should use
         * the primary constructor with an explicit [mappingFile] instead, so a missing override is
         * a compile error rather than a silent fallback to the wrong file.
         */
        @Suppress("LongParameterList")
        fun default(
            implicitNamingStrategyClassName: String = ViaductImplicitNamingStrategy::class.java.name,
            physicalNamingStrategyClassName: String = ViaductPhysicalNamingStrategy::class.java.name,
            metadataCustomizerClassNames: List<String> = emptyList(),
            dialectClassName: String = DEFAULT_DIALECT,
            hibernateSettings: Map<String, String> = defaultSettings(),
            semanticModel: PersistenceModel? = null,
            packageName: String? = null,
        ): HibernateMetadataConfiguration {
            val mappingFile = defaultMappingFile()
            return HibernateMetadataConfiguration(
                mappingFile = mappingFile,
                classpath = defaultClasspath(),
                managedClassNames = managedClassNamesIn(mappingFile),
                implicitNamingStrategyClassName = implicitNamingStrategyClassName,
                physicalNamingStrategyClassName = physicalNamingStrategyClassName,
                metadataCustomizerClassNames = metadataCustomizerClassNames,
                dialectClassName = dialectClassName,
                hibernateSettings = hibernateSettings,
                semanticModel = semanticModel,
                packageName = packageName,
            )
        }
    }
}
