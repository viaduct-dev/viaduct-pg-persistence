package dev.viaduct.persistence.gradle

import dev.viaduct.persistence.hibernate.HibernateSchemaModelWriter
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

abstract class GenerateHibernateSchemaModelTask : DefaultTask() {
    @get:InputDirectory
    abstract val centralSchemaDirectory: DirectoryProperty

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @get:Input
    abstract val packageName: Property<String>

    @get:InputFile
    @get:Optional
    abstract val replacementOrmXml: RegularFileProperty

    @get:Input
    abstract val associationSchemaName: Property<String>

    @get:InputFiles
    abstract val persistenceConfigFile: ConfigurableFileCollection

    init {
        associationSchemaName.convention(
            HibernateSchemaModelWriter.DEFAULT_ASSOCIATION_SCHEMA,
        )
    }

    @TaskAction
    fun generate() {
        val model =
            PersistenceSchemaModelLoader.build(
                centralSchemaDirectory = centralSchemaDirectory.get().asFile,
                persistenceConfigFile = persistenceConfigFile.files.singleOrNull(),
            )
        HibernateSchemaModelWriter().write(
            model = model,
            outputDirectory = outputDirectory.get().asFile,
            packageName = packageName.get(),
            replacementOrmXml = replacementOrmXml.orNull?.asFile,
            associationSchemaName = associationSchemaName.get(),
        )
        logger.lifecycle(
            "Generated Hibernate schema model for ${model.entities.joinToString { it.graphqlName }}",
        )
    }
}
