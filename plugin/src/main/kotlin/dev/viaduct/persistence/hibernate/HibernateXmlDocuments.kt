package dev.viaduct.persistence.hibernate

import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

internal object HibernateXmlDocuments {
    const val PERSISTENCE_NS = "https://jakarta.ee/xml/ns/persistence"
    const val ORM_NS = "https://jakarta.ee/xml/ns/persistence/orm"
    const val HBM_NS = "http://www.hibernate.org/xsd/orm/hbm"

    fun newDocument(): Document {
        val factory =
            DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = true
                setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
                setAttribute("http://javax.xml.XMLConstants/property/accessExternalDTD", "")
                setAttribute("http://javax.xml.XMLConstants/property/accessExternalSchema", "")
            }
        return factory.newDocumentBuilder().newDocument()
    }

    fun write(
        document: Document,
        destination: File,
    ) {
        TransformerFactory
            .newInstance()
            .apply {
                setAttribute("http://javax.xml.XMLConstants/property/accessExternalDTD", "")
                setAttribute("http://javax.xml.XMLConstants/property/accessExternalStylesheet", "")
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
