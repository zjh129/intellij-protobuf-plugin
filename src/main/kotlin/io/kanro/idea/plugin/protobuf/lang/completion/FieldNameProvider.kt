package io.kanro.idea.plugin.protobuf.lang.completion

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.InsertHandler
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiElement
import com.intellij.psi.util.parentOfType
import com.intellij.util.ProcessingContext
import io.kanro.idea.plugin.protobuf.lang.psi.forEachPrev
import io.kanro.idea.plugin.protobuf.lang.psi.proto.ProtobufExtensionStatement
import io.kanro.idea.plugin.protobuf.lang.psi.proto.ProtobufFieldDefinition
import io.kanro.idea.plugin.protobuf.lang.psi.proto.ProtobufReservedRange
import io.kanro.idea.plugin.protobuf.lang.psi.proto.range
import io.kanro.idea.plugin.protobuf.lang.psi.proto.structure.ProtobufNumberScope
import io.kanro.idea.plugin.protobuf.lang.support.BuiltInType
import io.kanro.idea.plugin.protobuf.string.case.CommonWordSplitter
import io.kanro.idea.plugin.protobuf.string.case.SnakeCaseFormatter
import io.kanro.idea.plugin.protobuf.string.plural
import java.util.LinkedList

object FieldNameProvider : CompletionProvider<CompletionParameters>() {
    override fun addCompletions(
        parameters: CompletionParameters,
        context: ProcessingContext,
        result: CompletionResultSet,
    ) {
        val element = parameters.position
        val field = element.parentOfType<ProtobufFieldDefinition>() ?: return
        val type = field.typeName.leaf()?.text ?: return
        val searchName = element.text.substringBeforeLast("_IntellijIdeaRulezzz", "")

        // Compute the smallest available field number avoiding used numbers and reserved ranges
        val nextNumber = nextAvailableFieldNumber(element)
        val inserter = fieldNumberInserter(nextNumber)

        wellKnownTypeSuggestion(type).forEach {
            result.addAllElements(fieldNameSuggestion(searchName, it, field.fieldLabel?.text == "repeated", inserter))
        }
        result.restartCompletionOnPrefixChange(PlatformPatterns.string().endsWith("_"))
    }

    private fun fieldNameSuggestion(
        name: String,
        type: String,
        plural: Boolean,
        inserter: InsertHandler<LookupElement>,
    ): List<LookupElement> {
        if (type.isEmpty()) return listOf()

        val result = mutableListOf<LookupElement>()
        val nameParts = CommonWordSplitter.split(name)
        val typeParts = LinkedList(CommonWordSplitter.split(type))
        if (plural) {
            typeParts[typeParts.lastIndex] = typeParts[typeParts.lastIndex].plural()
        }

        repeat(typeParts.size) {
            result +=
                LookupElementBuilder.create(SnakeCaseFormatter.format(nameParts + typeParts))
                    .withTypeText("field")
                    .withInsertHandler(inserter)
            typeParts.removeFirst()
        }

        return result
    }

    /**
     * Computes the smallest available field number, skipping numbers used by sibling fields
     * and numbers covered by reserved ranges.
     */
    private fun nextAvailableFieldNumber(context: PsiElement): Long {
        val usedNumbers = mutableSetOf<Long>()
        val reservedRanges = mutableListOf<LongRange>()

        context.parentOfType<ProtobufFieldDefinition>()?.forEachPrev {
            when (it) {
                is ProtobufFieldDefinition -> {
                    it.number()?.let { n -> usedNumbers.add(n) }
                }

                is ProtobufReservedRange -> {
                    it.range()?.let { range -> reservedRanges.add(range) }
                }

                is ProtobufExtensionStatement -> {
                    it.extensionRangeList.mapNotNull { ext -> ext.range() }.forEach { range ->
                        reservedRanges.add(range)
                    }
                }
            }
        }

        // Also collect reserved ranges from the scope (forward scan for later declarations)
        val scope = context.parentOfType<ProtobufFieldDefinition>()?.parentOfType<ProtobufNumberScope>()
        scope?.let { s ->
            for (reserved in s.reservedRange()) {
                reserved.range()?.let { range -> reservedRanges.add(range) }
            }
            for (extRange in s.extensionRange()) {
                extRange.range()?.let { range -> reservedRanges.add(range) }
            }
        }

        // Find the smallest positive number not in usedNumbers and not covered by any reserved range
        var candidate = 1L
        while (true) {
            if (candidate !in usedNumbers && reservedRanges.none { it.contains(candidate) }) {
                return candidate
            }
            candidate++
            if (candidate > 536_870_911) return 1L // max valid field number
        }
    }

    private fun fieldNumberInserter(number: Long): InsertHandler<LookupElement> {
        return SmartInsertHandler(" = $number;", -1)
    }

    private fun wellKnownTypeSuggestion(type: String): List<String> {
        return when (type) {
            "FieldMask" -> listOf("mask")
            "Timestamp" -> listOf("time")
            "Duration" -> listOf("duration", "offset")
            "Date" -> listOf("time")
            "Status" -> listOf("status", "error")
            else ->
                if (BuiltInType.isBuiltInType(type)) {
                    listOf()
                } else {
                    listOf(type)
                }
        }
    }
}
