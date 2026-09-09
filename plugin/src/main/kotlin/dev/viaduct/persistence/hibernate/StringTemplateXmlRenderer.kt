package dev.viaduct.persistence.hibernate

import org.stringtemplate.v4.AutoIndentWriter
import org.stringtemplate.v4.STGroupString
import org.stringtemplate.v4.STWriter
import org.stringtemplate.v4.StringRenderer
import org.stringtemplate.v4.misc.ErrorBuffer
import org.w3c.dom.Document
import java.io.StringWriter

/** Applies an object model to an XML StringTemplate resource and parses the result securely. */
internal object StringTemplateXmlRenderer {
    fun document(
        templateResource: String,
        model: Any,
    ): Document = HibernateXmlDocuments.parse(render(templateResource, model))

    private fun render(
        templateResource: String,
        model: Any,
    ): String {
        val errors = ErrorBuffer()
        val group =
            STGroupString(load(templateResource)).apply {
                listener = errors
                registerRenderer(String::class.java, StringRenderer())
            }
        val template = requireNotNull(group.getInstanceOf("main")) { "$templateResource has no main entry" }
        template.add("mdl", model)
        val output = StringWriter()
        template.write(AutoIndentWriter(output).apply { setLineWidth(STWriter.NO_WRAP) }, errors)
        check(errors.errors.isEmpty()) { "Could not render $templateResource: $errors" }
        return output.toString()
    }

    private fun load(templateResource: String): String =
        requireNotNull(StringTemplateXmlRenderer::class.java.getResourceAsStream(templateResource)) {
            "Missing XML template $templateResource"
        }.bufferedReader().use { it.readText() }
}
