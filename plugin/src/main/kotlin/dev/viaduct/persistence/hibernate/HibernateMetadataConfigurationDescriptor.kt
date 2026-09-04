package dev.viaduct.persistence.hibernate

import org.yaml.snakeyaml.Yaml
import java.io.File

/**
 * Reads and writes a [HibernateMetadataConfiguration] as a YAML file, so it can be referenced by
 * a real file path instead of held in an in-process registry.
 */
object HibernateMetadataConfigurationDescriptor {
    fun write(
        configuration: HibernateMetadataConfiguration,
        file: File,
    ) {
        file.writeText(Yaml().dump(toYaml(configuration)))
    }

    fun read(file: File): HibernateMetadataConfiguration {
        require(file.isFile) { "Hibernate metadata descriptor does not exist: ${file.absolutePath}" }
        val yaml = Yaml().load<Map<String, Any?>>(file.readText())
        return fromYaml(yaml)
    }

    private fun toYaml(configuration: HibernateMetadataConfiguration): Map<String, Any?> =
        mapOf(
            "mappingFile" to configuration.mappingFile.absolutePath,
            "classpath" to configuration.classpath.map(File::getAbsolutePath),
            "managedClassNames" to configuration.managedClassNames,
            "implicitNamingStrategyClassName" to configuration.implicitNamingStrategyClassName,
            "physicalNamingStrategyClassName" to configuration.physicalNamingStrategyClassName,
            "metadataCustomizerClassNames" to configuration.metadataCustomizerClassNames,
            "dialectClassName" to configuration.dialectClassName,
            "hibernateSettings" to configuration.hibernateSettings,
            "semanticModel" to configuration.semanticModel?.let(PersistenceModelYaml::toYaml),
            "packageName" to configuration.packageName,
        )

    private fun fromYaml(yaml: Map<String, Any?>): HibernateMetadataConfiguration =
        HibernateMetadataConfiguration(
            mappingFile = File(yaml.yamlString("mappingFile")),
            classpath = yaml.yamlStringList("classpath").map(::File),
            managedClassNames = yaml.yamlStringList("managedClassNames"),
            implicitNamingStrategyClassName = yaml.yamlString("implicitNamingStrategyClassName"),
            physicalNamingStrategyClassName = yaml.yamlString("physicalNamingStrategyClassName"),
            metadataCustomizerClassNames = yaml.yamlStringList("metadataCustomizerClassNames"),
            dialectClassName = yaml.yamlString("dialectClassName"),
            hibernateSettings = yaml.yamlStringMap("hibernateSettings"),
            semanticModel = yaml.yamlMapOrNull("semanticModel")?.let(PersistenceModelYaml::fromYaml),
            packageName = yaml["packageName"] as String?,
        )
}
