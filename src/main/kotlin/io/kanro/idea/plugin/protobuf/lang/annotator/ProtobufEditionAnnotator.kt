package io.kanro.idea.plugin.protobuf.lang.annotator

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.psi.PsiElement
import com.intellij.psi.util.QualifiedName
import com.intellij.psi.util.parentOfType
import io.kanro.idea.plugin.protobuf.lang.psi.proto.ProtobufAnyName
import io.kanro.idea.plugin.protobuf.lang.psi.proto.ProtobufElement
import io.kanro.idea.plugin.protobuf.lang.psi.proto.ProtobufEnumDefinition
import io.kanro.idea.plugin.protobuf.lang.psi.proto.ProtobufEnumValueDefinition
import io.kanro.idea.plugin.protobuf.lang.psi.proto.ProtobufExtendDefinition
import io.kanro.idea.plugin.protobuf.lang.psi.proto.ProtobufExtensionStatement
import io.kanro.idea.plugin.protobuf.lang.psi.proto.ProtobufFieldDefinition
import io.kanro.idea.plugin.protobuf.lang.psi.proto.ProtobufFile
import io.kanro.idea.plugin.protobuf.lang.psi.proto.ProtobufGroupDefinition
import io.kanro.idea.plugin.protobuf.lang.psi.proto.ProtobufImportStatement
import io.kanro.idea.plugin.protobuf.lang.psi.proto.ProtobufMessageDefinition
import io.kanro.idea.plugin.protobuf.lang.psi.proto.ProtobufOneofBody
import io.kanro.idea.plugin.protobuf.lang.psi.proto.ProtobufOptionName
import io.kanro.idea.plugin.protobuf.lang.psi.proto.ProtobufTypeName
import io.kanro.idea.plugin.protobuf.lang.psi.proto.ProtobufVisitor
import io.kanro.idea.plugin.protobuf.lang.psi.proto.isFieldDefaultOption
import io.kanro.idea.plugin.protobuf.lang.psi.proto.weak
import io.kanro.idea.plugin.protobuf.lang.support.Options
import io.kanro.idea.plugin.protobuf.lang.support.WellknownTypes

/**
 * Annotator for Protobuf Editions (edition = "2023").
 *
 * This annotator replaces syntax-based rules with feature-based rules.
 * For edition files, the file's edition is resolved to a [ProtobufFeature] instance,
 * and validation rules are driven by the individual feature flags rather than
 * the presence of a syntax declaration.
 */
class ProtobufEditionAnnotator : Annotator {
    override fun annotate(
        element: PsiElement,
        holder: AnnotationHolder,
    ) {
        if (element.containingFile.originalFile !is ProtobufFile) return
        val file = (element as? ProtobufElement)?.file() ?: return
        // Only activate for edition files (syntax == null, edition != null)
        if (file.edition() == null) return

        val edition = file.edition() ?: return
        val feature = resolveFeature(edition) ?: return

        element.accept(
            object : ProtobufVisitor() {
                override fun visitFieldDefinition(o: ProtobufFieldDefinition) {
                    // Check field presence feature
                    when (feature.fieldPresence) {
                        ProtobufFieldPresence.EXPLICIT -> {
                            // Fields must have explicit labels (proto2-like)
                            if (o.parent is ProtobufOneofBody) {
                                if (o.fieldLabel?.textMatches("optional") == false) {
                                    holder.newAnnotation(
                                        HighlightSeverity.ERROR,
                                        "OneOf field only support 'optional' or none label with explicit presence.",
                                    )
                                        .range(o.textRange)
                                        .create()
                                }
                            }
                            // For editions with explicit presence, no label is required
                            // but 'required' is still not allowed
                            if (o.fieldLabel?.textMatches("required") == true) {
                                holder.newAnnotation(
                                    HighlightSeverity.ERROR,
                                    "'required' field is not supported with explicit field presence.",
                                )
                                    .range(o.fieldLabel?.textRange ?: o.textRange)
                                    .create()
                            }
                        }

                        ProtobufFieldPresence.IMPLICIT -> {
                            // Proto3-like: no labels, no required
                            if (o.parent is ProtobufOneofBody) {
                                holder.newAnnotation(
                                    HighlightSeverity.ERROR,
                                    "OneOf field does not support labels in implicit presence mode.",
                                )
                                    .range(o.textRange)
                                    .create()
                            }
                            if (o.fieldLabel?.textMatches("required") == true) {
                                holder.newAnnotation(
                                    HighlightSeverity.ERROR,
                                    "'required' field is not supported with implicit field presence.",
                                )
                                    .range(o.fieldLabel?.textRange ?: o.textRange)
                                    .create()
                            }
                        }

                        ProtobufFieldPresence.LEGACY_REQUIRED -> {
                            // Proto2-like: labels required
                            if (o.parent is ProtobufOneofBody) {
                                if (o.fieldLabel?.textMatches("optional") == false) {
                                    holder.newAnnotation(
                                        HighlightSeverity.ERROR,
                                        "OneOf field only support 'optional' or none label.",
                                    )
                                        .range(o.textRange)
                                        .create()
                                }
                            } else {
                                if (o.fieldLabel == null) {
                                    holder.newAnnotation(
                                        HighlightSeverity.ERROR,
                                        "Field must have a label with legacy required presence.",
                                    )
                                        .range(o.textRange)
                                        .create()
                                }
                            }
                        }
                    }
                }

                override fun visitExtendDefinition(o: ProtobufExtendDefinition) {
                    val typename = o.typeName ?: return
                    val qname = (typename.resolve() as? ProtobufMessageDefinition)?.qualifiedName()
                    val isOptionMessage = qname != null && Options.all.any { it.toString() == qname.toString() }
                    if (isOptionMessage) {
                        return
                    }
                    // Proto3-like editions disallow non-Option extensions (feature: IMPLICIT)
                    if (feature.fieldPresence == ProtobufFieldPresence.IMPLICIT) {
                        holder.newAnnotation(
                            HighlightSeverity.ERROR,
                            "Only Options can be extended with implicit field presence.",
                        )
                            .range(typename.textRange)
                            .create()
                    }
                }

                override fun visitGroupDefinition(o: ProtobufGroupDefinition) {
                    // Groups are not allowed in IMPLICIT (proto3-like) editions
                    if (feature.fieldPresence == ProtobufFieldPresence.IMPLICIT) {
                        holder.newAnnotation(
                            HighlightSeverity.ERROR,
                            "'group' is not supported with implicit field presence.",
                        )
                            .range(o.textRange)
                            .create()
                    }
                    // Group names must start with capital letter
                    val name = o.qualifiedName()?.lastComponent ?: return
                    if (name.isNotEmpty() && !name[0].isUpperCase()) {
                        holder.newAnnotation(
                            HighlightSeverity.ERROR,
                            "'group' field name must start with a capital letter.",
                        )
                            .range(o.textRange)
                            .create()
                    }
                }

                override fun visitExtensionStatement(o: ProtobufExtensionStatement) {
                    // Extensions are not allowed in IMPLICIT (proto3-like) editions
                    if (feature.fieldPresence == ProtobufFieldPresence.IMPLICIT) {
                        holder.newAnnotation(
                            HighlightSeverity.ERROR,
                            "'extension' is not supported with implicit field presence.",
                        )
                            .range(o.textRange)
                            .create()
                    }
                }

                override fun visitImportStatement(o: ProtobufImportStatement) {
                    // Weak imports are not allowed in IMPLICIT editions
                    if (o.weak() && feature.fieldPresence == ProtobufFieldPresence.IMPLICIT) {
                        holder.newAnnotation(
                            HighlightSeverity.ERROR,
                            "'weak' import is not supported with implicit field presence.",
                        )
                            .range(o.importLabel?.textRange ?: o.textRange)
                            .create()
                    }
                }

                override fun visitOptionName(o: ProtobufOptionName) {
                    // Default option is not allowed in IMPLICIT editions
                    if (o.isFieldDefaultOption() && feature.fieldPresence == ProtobufFieldPresence.IMPLICIT) {
                        holder.newAnnotation(
                            HighlightSeverity.ERROR,
                            "'default' option is not supported with implicit field presence.",
                        )
                            .range(o.textRange)
                            .create()
                    }
                }

                override fun visitEnumDefinition(o: ProtobufEnumDefinition) {
                    // OPEN enums (proto3-like) must have a zero value
                    if (feature.enumType == ProtobufEnumType.OPEN) {
                        val items = o.items()
                        val zeroDefinition =
                            items.firstOrNull { it is ProtobufEnumValueDefinition && it.number() == 0L }
                                as? ProtobufEnumValueDefinition

                        if (zeroDefinition == null) {
                            holder.newAnnotation(
                                HighlightSeverity.ERROR,
                                "'zero' enum value is required with open enum type.",
                            )
                                .range(o.textRange)
                                .create()
                        }
                    }
                    // CLOSED enums (proto2-like) should have zero value but it's not required
                }

                override fun visitTypeName(o: ProtobufTypeName) {
                    val type = o.reference?.resolve()
                    if (type is ProtobufEnumDefinition) {
                        val typeFile = type.file()
                        // If current edition uses OPEN enums, closed enums from proto2 are not allowed
                        if (feature.enumType == ProtobufEnumType.OPEN &&
                            typeFile.syntax() == "proto2" &&
                            o.parent !is ProtobufTypeName
                        ) {
                            holder.newAnnotation(
                                HighlightSeverity.ERROR,
                                "Proto2 Enums are not supported with open enum type.",
                            )
                                .range(o.textRange)
                                .create()
                        }
                    }
                }

                override fun visitAnyName(o: ProtobufAnyName) {
                    val parentField =
                        o.parentOfType<io.kanro.idea.plugin.protobuf.lang.psi.proto.ProtobufField>()
                            ?.parentOfType<io.kanro.idea.plugin.protobuf.lang.psi.proto.ProtobufField>()
                            ?.field()

                    if (parentField !is ProtobufFieldDefinition ||
                        (parentField.typeName.resolve() as? ProtobufMessageDefinition)?.qualifiedName()
                            ?.toString() != WellknownTypes.ANY
                    ) {
                        holder.newAnnotation(
                            HighlightSeverity.ERROR,
                            "Field '${parentField?.name()}' is not a field with Any type",
                        )
                            .range(o.textRange)
                            .highlightType(
                                com.intellij.codeInspection.ProblemHighlightType.ERROR,
                            )
                            .create()
                    }
                }
            },
        )
    }

    private fun resolveFeature(edition: String): ProtobufFeature? {
        return when (edition) {
            "2023" -> ProtobufFeature.EDITION_2023
            else -> null
        }
    }
}
