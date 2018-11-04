package io.play.kotlin

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiErrorElement
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.diagnostics.Errors
import org.jetbrains.kotlin.diagnostics.rendering.DefaultErrorMessages
import org.jetbrains.kotlin.psi.KtFile

object ErrorHandler{
    data class ErrorDescriptor(val interval: TextInterval, val message: String, val severity: Severity, val className: String? = null)
    enum class Severity {
        INFO,
        ERROR,
        WARNING;
        companion object {
            fun from(severity: org.jetbrains.kotlin.diagnostics.Severity) : Severity {
                return when (severity) {
                    org.jetbrains.kotlin.diagnostics.Severity.ERROR -> Severity.ERROR
                    org.jetbrains.kotlin.diagnostics.Severity.INFO -> Severity.INFO
                    org.jetbrains.kotlin.diagnostics.Severity.WARNING -> Severity.WARNING
                    else -> Severity.WARNING
                }
            }
        }
    }
    fun analyze(files: List<KtFile>): JavaExecuter.ExecutionResult{
        val (analysis) =  Resolver.analyze(files, EnvironmentManager.environment.project)
        val diagnostics = analysis.bindingContext.diagnostics.all()
        return errorsFrom(diagnostics, errorsFrom(files))
    }

    private fun errorsFrom(files: List<KtFile>): Map<String, List<ErrorDescriptor>>{
        class Visitor: PsiElementVisitor(){
            val errors = mutableListOf<PsiErrorElement>()
            override fun visitElement(element: PsiElement) { element.acceptChildren(this) }
            override fun visitErrorElement(element: PsiErrorElement) { errors.add(element) }
        }
        return files.map { file ->
            file.name to Visitor().also { it.visitFile(file) }.errors.map {
                ErrorDescriptor(
                        TextInterval.from(it.textRange.startOffset, it.textRange.endOffset, file.viewProvider.document!!),
                        it.errorDescription,
                        Severity.ERROR,
                        "red_wavy_line"
                )
            }
        }.toMap()
    }


    private fun errorsFrom(diagnostics: Collection<Diagnostic>, errors: Map<String, List<ErrorDescriptor>>): JavaExecuter.ExecutionResult {
        val fileErrors = errors.map { it.key to it.value.toMutableList() }.toMap().toMutableMap()
        diagnostics.forEach { diagnostic ->
            diagnostic.psiFile.virtualFile?.let {
                val rendered = DefaultErrorMessages.render(diagnostic)
                if(diagnostic.severity != org.jetbrains.kotlin.diagnostics.Severity.INFO){
                    diagnostic.textRanges.firstOrNull()?.also { range ->
                        val className = if (diagnostic.severity == org.jetbrains.kotlin.diagnostics.Severity.ERROR && diagnostic.factory != Errors.UNRESOLVED_REFERENCE) {
                            "red_wavy_line"
                        } else diagnostic.severity.name
                        val interval = TextInterval.from(range.startOffset, range.endOffset, diagnostic.psiFile.viewProvider.document!!)
                        fileErrors[diagnostic.psiFile.name]?.add(
                                ErrorDescriptor(interval, rendered, Severity.from(diagnostic.severity), className)
                        )
                    }
                }
            }
        }
        return JavaExecuter.ExecutionResult(fileErrors)
    }
}


