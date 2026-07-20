package io.kanro.idea.plugin.protobuf.lang.util

import com.intellij.codeInsight.documentation.DocumentationManagerProtocol
import io.kanro.idea.plugin.protobuf.lang.psi.proto.ProtobufElement
import org.commonmark.ext.autolink.AutolinkExtension
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.node.Document
import org.commonmark.node.Link
import org.commonmark.node.LinkReferenceDefinition
import org.commonmark.node.Node
import org.commonmark.parser.Parser
import org.commonmark.renderer.html.HtmlRenderer

private val extensions = listOf(AutolinkExtension.create(), TablesExtension.create())
private val parser = Parser.builder().extensions(extensions).build()
private val renderer = HtmlRenderer.builder().extensions(extensions).build()

fun renderDoc(
    context: ProtobufElement,
    doc: String,
): String {
    val document = parser.parse(doc)
    resolveLinkReferences(context, document)
    return renderer.render(document)
}

private fun resolveLinkReferences(context: ProtobufElement, node: Node) {
    when (node) {
        is Link -> {
            val dest = node.destination
            if (dest.startsWith(DocumentationManagerProtocol.PSI_ELEMENT_PROTOCOL)) {
                val label = dest.removePrefix(DocumentationManagerProtocol.PSI_ELEMENT_PROTOCOL)
                    .removeSuffix(DocumentationManagerProtocol.PSI_ELEMENT_PROTOCOL)
                if (label.isNotEmpty()) {
                    val path =
                        DocumentationManagerProtocol.PSI_ELEMENT_PROTOCOL +
                            label +
                            DocumentationManagerProtocol.PSI_ELEMENT_PROTOCOL_REF_SEPARATOR +
                            label
                    node.destination = path
                }
            }
        }
        is LinkReferenceDefinition -> {
            val path =
                DocumentationManagerProtocol.PSI_ELEMENT_PROTOCOL +
                    node.label +
                    DocumentationManagerProtocol.PSI_ELEMENT_PROTOCOL_REF_SEPARATOR +
                    node.label
            node.destination = path
        }
    }
    var child = node.firstChild
    while (child != null) {
        val next = child.next
        resolveLinkReferences(context, child)
        child = next
    }
}
