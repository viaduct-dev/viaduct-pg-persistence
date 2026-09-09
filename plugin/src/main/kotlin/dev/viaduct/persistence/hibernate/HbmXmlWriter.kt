package dev.viaduct.persistence.hibernate

import dev.viaduct.persistence.model.PersistenceModel
import org.stringtemplate.v4.AutoIndentWriter
import org.stringtemplate.v4.STGroupString
import org.stringtemplate.v4.STWriter
import org.stringtemplate.v4.misc.ErrorBuffer
import org.w3c.dom.Document
import java.io.StringWriter

/** Renders a normalized [HbmMappingDocument] using the native Hibernate XML template. */
internal class HbmXmlWriter {
    fun document(
        model: PersistenceModel,
        associationSchemaName: String,
    ): Document = document(PersistenceModelToHbmMapper.map(model, associationSchemaName))

    internal fun document(mapping: HbmMappingDocument): Document =
        HbmXmlTemplateModel
            .from(mapping)
            .let(::render)
            .let(HibernateXmlDocuments::parse)

    private fun render(model: HbmXmlTemplateModel): String {
        val errors = ErrorBuffer()
        val group = STGroupString(templateSource).apply { listener = errors }
        val template = requireNotNull(group.getInstanceOf("main")) { "HBM template has no main entry" }
        template.add("mdl", model)
        val output = StringWriter()
        template.write(AutoIndentWriter(output).apply { setLineWidth(STWriter.NO_WRAP) }, errors)
        check(errors.errors.isEmpty()) { "Could not render HBM template: $errors" }
        return output.toString()
    }

    private companion object {
        private const val TEMPLATE_RESOURCE =
            "/dev/viaduct/persistence/hibernate/viaduct-persistence.hbm.stg"

        val templateSource: String =
            requireNotNull(HbmXmlWriter::class.java.getResourceAsStream(TEMPLATE_RESOURCE)) {
                "Missing HBM template $TEMPLATE_RESOURCE"
            }.bufferedReader().use { it.readText() }
    }
}
