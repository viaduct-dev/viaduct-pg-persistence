package dev.viaduct.persistence.hibernate

import org.w3c.dom.Document
import org.w3c.dom.Element
import org.xml.sax.InputSource
import java.io.File
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

internal object HibernateXmlDocuments {
    const val PERSISTENCE_NS = "https://jakarta.ee/xml/ns/persistence"
    const val ORM_NS = "https://jakarta.ee/xml/ns/persistence/orm"
    const val HBM_NS = "http://www.hibernate.org/xsd/orm/hbm"

    fun newDocument(): Document = documentBuilderFactory().newDocumentBuilder().newDocument()

    fun parse(xml: String): Document =
        documentBuilderFactory()
            .newDocumentBuilder()
            .parse(InputSource(StringReader(xml)))

    private fun documentBuilderFactory(): DocumentBuilderFactory =
        DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            setAttribute(JaxpXmlConstants.accessExternalDtd(), "")
            setAttribute(JaxpXmlConstants.accessExternalSchema(), "")
        }

    fun write(
        document: Document,
        destination: File,
    ) {
        TransformerFactory
            .newInstance()
            .apply {
                setAttribute(JaxpXmlConstants.accessExternalDtd(), "")
                setAttribute(JaxpXmlConstants.accessExternalStylesheet(), "")
            }.newTransformer()
            .apply {
                setOutputProperty(OutputKeys.INDENT, "yes")
                setOutputProperty(OutputKeys.ENCODING, "UTF-8")
                setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2")
            }.transform(DOMSource(document), StreamResult(destination))
    }
}

internal fun Element.child(name: String): Element =
    ownerDocument
        .createElementNS(namespaceURI, name)
        .also(::appendChild)
