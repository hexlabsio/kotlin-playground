package io.play.kotlin

import com.intellij.openapi.vfs.CharsetToolkit
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.impl.PsiFileFactoryImpl
import com.intellij.testFramework.LightVirtualFile
import io.play.model.Project
import org.jetbrains.kotlin.codegen.KotlinCodegenFacade
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.load.kotlin.PackagePartClassUtils
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.resolve.BindingContext
object KotlinWrapper {

    data class CompilationResult(val files: Map<String, ByteArray>, val mainClass: String)

    fun compile(project: Project):  Pair<CompilationResult?, JavaExecuter.ExecutionResult> {
        val files = project.files.map { createFile(it.name, it.text) }
        val errors = ErrorHandler.analyze(files)
        if(isSafe(errors)) {
            val generationState = Resolver.generationState(files, EnvironmentManager.environment.project)
            val mainClass = findMainClass(generationState.bindingContext, files, "File")
            KotlinCodegenFacade.compileCorrectFiles(generationState, { error, file -> error.printStackTrace() })
            val factory = generationState.factory
            val result =  CompilationResult(factory.asList().map { it.relativePath to it.asByteArray() }.toMap(), mainClass)
            return result to errors
        }
        return null to errors
    }

    private fun isSafe(errors: JavaExecuter.ExecutionResult): Boolean{
        val actualErrors = errors.errors.flatMap { it.value }
        return actualErrors.isEmpty() || (actualErrors.find { it.severity == ErrorHandler.Severity.ERROR } == null)
    }

    fun createFile(name: String, text: String): KtFile {
        val fileName = if(name.endsWith(".kt")) name else "$name.kt"
        val virtualFile = LightVirtualFile(fileName, KotlinLanguage.INSTANCE, text).also {
            it.charset = CharsetToolkit.UTF8_CHARSET
        }
        return (PsiFileFactory.getInstance(EnvironmentManager.environment.project) as PsiFileFactoryImpl)
                .trySetupPsiForFile(virtualFile, KotlinLanguage.INSTANCE, true, false) as KtFile
    }

    private fun findMainClass(bindingContext: BindingContext, files: List<KtFile>, fileName: String): String{
        //val mainFunctionDetector = MainFunctionDetector(bindingContext, LanguageVersionSettingsImpl.DEFAULT)
        return files.first().let { PackagePartClassUtils.getPackagePartFqName(it.packageFqName, it.name).asString() }
    }
}