package dev.viaduct.persistence.hibernate

import org.stringtemplate.v4.AttributeRenderer
import org.stringtemplate.v4.AutoIndentWriter
import org.stringtemplate.v4.Interpreter
import org.stringtemplate.v4.ST
import org.stringtemplate.v4.STGroupString
import org.stringtemplate.v4.STWriter
import org.stringtemplate.v4.misc.ErrorBuffer
import org.stringtemplate.v4.misc.ObjectModelAdaptor
import org.w3c.dom.Document
import java.io.StringWriter
import java.util.Locale

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
                registerRenderer(String::class.java, XmlAttributeRenderer)
                registerModelAdaptor(HbmBasicMapping::class.java, HbmAttributeModelAdaptor)
                registerModelAdaptor(HbmToOneMapping::class.java, HbmAttributeModelAdaptor)
                registerModelAdaptor(HbmToManyMapping::class.java, HbmAttributeModelAdaptor)
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

    private object HbmAttributeModelAdaptor : ObjectModelAdaptor<HbmAttributeMapping>() {
        override fun getProperty(
            interpreter: Interpreter,
            template: ST,
            model: HbmAttributeMapping,
            property: Any,
            propertyName: String,
        ): Any? =
            when (propertyName) {
                "basic" -> model as? HbmBasicMapping
                "toOne" -> model as? HbmToOneMapping
                "toMany" -> model as? HbmToManyMapping
                else -> super.getProperty(interpreter, template, model, property, propertyName)
            }
    }

    private object XmlAttributeRenderer : AttributeRenderer<String> {
        override fun toString(
            value: String,
            formatString: String?,
            locale: Locale?,
        ): String {
            if (formatString == null) return value
            require(formatString == "xml-attribute") { "Unsupported string format: $formatString" }
            return value
                .replace("&", "&amp;")
                .replace("\"", "&quot;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
        }
    }
}
