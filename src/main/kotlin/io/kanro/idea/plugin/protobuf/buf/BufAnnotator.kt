package io.kanro.idea.plugin.protobuf.buf

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.ExternalAnnotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.psi.PsiFile
import io.kanro.idea.plugin.protobuf.lang.ProtobufFileType

class BufAnnotator : ExternalAnnotator<PsiFile, BufAnnotator.BufLintResult?>() {

    override fun collectInformation(file: PsiFile): PsiFile? {
        if (file.fileType !is ProtobufFileType) return null
        if (!BufSettings.getInstance().state.enabled) return null
        return file
    }

    override fun doAnnotate(protoFile: PsiFile): BufLintResult? {
        val settings = BufSettings.getInstance()
        if (!settings.state.enabled) return null

        val bufPath = (settings.state.bufPath ?: "buf").ifBlank { "buf" }
        val protoPath = protoFile.virtualFile?.path ?: return null
        val workDir = protoFile.virtualFile?.parent?.path

        val configArg = findBufConfig(protoFile)

        val args = buildList {
            add("lint")
            add(protoPath)
            if (configArg != null) {
                add("--config")
                add(configArg)
            }
            add("--error-format=json")
        }

        return runLint(bufPath, args, workDir)
    }

    override fun apply(
        file: PsiFile,
        result: BufLintResult?,
        holder: AnnotationHolder,
    ) {
        if (result == null) return
        for (issue in result.issues) {
            val range = issue.textRange(file) ?: continue
            holder.newAnnotation(
                HighlightSeverity.WARNING,
                "[buf] ${issue.message}",
            )
                .range(range)
                .create()
        }
    }

    private fun runLint(
        executable: String,
        args: List<String>,
        workDir: String?,
    ): BufLintResult? {
        return try {
            val processBuilder = ProcessBuilder(listOf(executable) + args)
            workDir?.let { processBuilder.directory(java.io.File(it)) }
            processBuilder.redirectErrorStream(true)
            val process = processBuilder.start()
            val output = process.inputStream.bufferedReader().readText()
            val exitCode = process.waitFor()
            if (exitCode != 0 && output.isBlank()) return null
            parseOutput(output)
        } catch (e: Exception) {
            null
        }
    }

    private fun findBufConfig(file: PsiFile): String? {
        val project = file.project
        val virtualFile = file.virtualFile ?: return null
        for (module in com.intellij.openapi.module.ModuleManager.getInstance(project).modules) {
            for (root in com.intellij.openapi.roots.ModuleRootManager.getInstance(module).contentRoots) {
                val bufYaml = root.findChild("buf.yaml")
                if (bufYaml != null &&
                    com.intellij.openapi.roots.ModuleRootManager.getInstance(module).fileIndex.isInContent(virtualFile)
                ) {
                    return bufYaml.path
                }
            }
        }
        return null
    }

    private fun parseOutput(output: String): BufLintResult {
        val issues = mutableListOf<BufLintIssue>()
        for (line in output.lines()) {
            if (line.isNotBlank()) {
                parseIssue(line)?.let { issues.add(it) }
            }
        }
        return BufLintResult(issues)
    }

    private fun parseIssue(line: String): BufLintIssue? {
        if (!line.startsWith("{")) return null
        val path = extractString(line, "path") ?: return null
        val lineNum = extractInt(line, "line") ?: 1
        val col = extractInt(line, "column") ?: 1
        val message = extractString(line, "message") ?: extractString(line, "msg") ?: return null
        val ruleId = extractString(line, "rule_id") ?: ""
        return BufLintIssue(path = path, line = lineNum, column = col, message = message, ruleId = ruleId)
    }

    private fun extractString(json: String, key: String): String? {
        val pattern = Regex(""""$key"\s*:\s*"([^"\\]*(?:\\.[^"\\]*)*)"""")
        return pattern.find(json)?.groupValues?.getOrNull(1)
    }

    private fun extractInt(json: String, key: String): Int? {
        val pattern = Regex(""""$key"\s*:\s*(\d+)""")
        return pattern.find(json)?.groupValues?.getOrNull(1)?.toIntOrNull()
    }

    data class BufLintResult(val issues: List<BufLintIssue>)

    data class BufLintIssue(
        val path: String,
        val line: Int,
        val column: Int,
        val message: String,
        val ruleId: String,
    ) {
        fun textRange(file: PsiFile): com.intellij.openapi.util.TextRange? {
            val document = file.viewProvider.document ?: return null
            return try {
                val start = document.getLineStartOffset(line - 1)
                val end = document.getLineEndOffset(line - 1)
                com.intellij.openapi.util.TextRange(start, end)
            } catch (e: Exception) {
                null
            }
        }
    }
}
