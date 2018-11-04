package io.play.kotlin

import com.intellij.codeInsight.ContainerProvider
import org.jetbrains.kotlin.analyzer.AnalysisResult
import org.jetbrains.kotlin.cli.jvm.compiler.CliBindingTrace
import org.jetbrains.kotlin.cli.jvm.compiler.TopDownAnalyzerFacadeForJVM
import org.jetbrains.kotlin.codegen.ClassBuilderFactories
import org.jetbrains.kotlin.codegen.state.GenerationState
import org.jetbrains.kotlin.config.JVMConfigurationKeys
import org.jetbrains.kotlin.container.ComponentProvider
import org.jetbrains.kotlin.container.getService
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.resolve.LazyTopDownAnalyzer
import org.jetbrains.kotlin.resolve.TopDownAnalysisMode
import org.jetbrains.kotlin.resolve.calls.smartcasts.DataFlowInfo
import org.jetbrains.kotlin.resolve.jvm.extensions.AnalysisHandlerExtension
import org.jetbrains.kotlin.resolve.lazy.declarations.FileBasedDeclarationProviderFactory

object Resolver{
    fun generationState(files: List<KtFile>, project: com.intellij.openapi.project.Project): GenerationState {
        val (analysis) = analyze(files, project)
        return GenerationState.Builder(
                project,
                ClassBuilderFactories.BINARIES,
                analysis.moduleDescriptor,
                analysis.bindingContext,
                files,
                EnvironmentManager.environment.configuration
        ).build()
    }

    fun analyze(files: List<KtFile>, project: com.intellij.openapi.project.Project): Pair<AnalysisResult, ComponentProvider> {
        val environment = EnvironmentManager.environment
        val trace = CliBindingTrace()
        environment.configuration.put(JVMConfigurationKeys.ADD_BUILT_INS_FROM_COMPILER_TO_DEPENDENCIES, true)
        val container = TopDownAnalyzerFacadeForJVM.createContainer(
                environment.project,
                files,
                trace,
                environment.configuration,
                { globalSearchScope -> environment.createPackagePartProvider(globalSearchScope) },
                { storageManager, ktFiles -> FileBasedDeclarationProviderFactory(storageManager, ktFiles) },
                TopDownAnalyzerFacadeForJVM.newModuleSearchScope(project, files)
        )
        container.getService(LazyTopDownAnalyzer::class.java)
                .analyzeDeclarations(TopDownAnalysisMode.TopLevelDeclarations, files, DataFlowInfo.EMPTY)
        val moduleDescriptor = container.getService(ModuleDescriptor::class.java)
        AnalysisHandlerExtension.getInstances(project).find { it.analysisCompleted(project, moduleDescriptor, trace, files) != null }
        return AnalysisResult.success(trace.bindingContext, moduleDescriptor) to container
    }
}