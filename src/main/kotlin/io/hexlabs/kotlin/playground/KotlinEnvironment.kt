package io.hexlabs.kotlin.playground

import com.intellij.openapi.Disposable
import com.intellij.psi.PsiElement
import com.intellij.psi.tree.TokenSet
import io.hexlabs.kotlin.playground.model.Analysis
import io.hexlabs.kotlin.playground.model.Completion
import org.jetbrains.kotlin.analyzer.AnalysisResult
import org.jetbrains.kotlin.cli.common.CLIConfigurationKeys
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.cli.jvm.compiler.CliBindingTrace
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.cli.jvm.compiler.TopDownAnalyzerFacadeForJVM
import org.jetbrains.kotlin.cli.jvm.config.addJvmClasspathRoots
import org.jetbrains.kotlin.config.CommonConfigurationKeys
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.JVMConfigurationKeys
import org.jetbrains.kotlin.container.getService
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.descriptors.PackageFragmentDescriptor
import org.jetbrains.kotlin.descriptors.PackageViewDescriptor
import org.jetbrains.kotlin.descriptors.PropertyDescriptor
import org.jetbrains.kotlin.descriptors.ValueParameterDescriptor
import org.jetbrains.kotlin.descriptors.VariableDescriptor
import org.jetbrains.kotlin.descriptors.impl.LocalVariableDescriptor
import org.jetbrains.kotlin.descriptors.impl.TypeParameterDescriptorImpl
import org.jetbrains.kotlin.idea.codeInsight.ReferenceVariantsHelper
import org.jetbrains.kotlin.idea.util.IdeDescriptorRenderers
import org.jetbrains.kotlin.lexer.KtKeywordToken
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtQualifiedExpression
import org.jetbrains.kotlin.psi.KtSimpleNameExpression
import org.jetbrains.kotlin.renderer.ClassifierNamePolicy
import org.jetbrains.kotlin.renderer.ParameterNameRenderingPolicy
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.DescriptorUtils
import org.jetbrains.kotlin.resolve.LazyTopDownAnalyzer
import org.jetbrains.kotlin.resolve.TopDownAnalysisMode
import org.jetbrains.kotlin.resolve.calls.smartcasts.DataFlowInfo
import org.jetbrains.kotlin.resolve.jvm.extensions.AnalysisHandlerExtension
import org.jetbrains.kotlin.resolve.lazy.declarations.FileBasedDeclarationProviderFactory
import org.jetbrains.kotlin.resolve.scopes.DescriptorKindFilter
import org.jetbrains.kotlin.resolve.scopes.MemberScope
import org.jetbrains.kotlin.types.asFlexibleType
import org.jetbrains.kotlin.types.isFlexible
import java.io.File
import java.util.UUID

data class KotlinEnvironment(val kotlinEnvironment: KotlinCoreEnvironment) {

    private data class DescriptorInfo(val isTipsManagerCompletion: Boolean, val descriptors: List<DeclarationDescriptor>)

    fun complete(file: KotlinFile, line: Int, character: Int) = with(file.insert("xxxxxxxxxxxxxxxxxxxxxxx ", line, character)) {
        elementAt(line, character)?.let { element ->
            val descriptorInfo = descriptorsFrom(this, element)
            val prefix = (if (descriptorInfo.isTipsManagerCompletion) element.text else element.parent.text)
                .substringBefore("xxxxxxxxxxxxxxxxxxxxxxx").let { if (it.endsWith(".")) "" else it }
            descriptorInfo.descriptors.toMutableList().apply {
                sortWith(Comparator { a, b ->
                    val (a1, a2) = a.presentableName()
                    val (b1, b2) = b.presentableName()
                    ("$a1$a2").compareTo("$b1$b2", true)
                }) }.mapNotNull { descriptor -> completionVariantFor(prefix, descriptor) } + keywordsCompletionVariants(KtTokens.KEYWORDS, prefix) + keywordsCompletionVariants(KtTokens.SOFT_KEYWORDS, prefix)
        } ?: emptyList()
    }

    private fun completionVariantFor(prefix: String, descriptor: DeclarationDescriptor): Completion? {
        val (name, tail) = descriptor.presentableName()
        val fullName: String = formatName(name, 40)
        var completionText = fullName
        var position = completionText.indexOf('(')
        if (position != -1) {
            if (completionText[position - 1] == ' ') position -= 2
            if (completionText[position + 1] == ')') position++
            completionText = completionText.substring(0, position + 1)
        }
        position = completionText.indexOf(":")
        if (position != -1) completionText = completionText.substring(0, position - 1)
        return if (prefix.isEmpty() || fullName.startsWith(prefix)) {
            Completion(completionText, fullName, tail, iconFrom(descriptor))
        } else null
    }

    private fun DeclarationDescriptor.presentableName() = when (this) {
        is FunctionDescriptor -> name.asString() + renderer.renderFunctionParameters(this) to when {
            returnType != null -> renderer.renderType(returnType!!)
            else -> (extensionReceiverParameter?.let { param ->
                " for ${renderer.renderType(param.type)} in ${DescriptorUtils.getFqName(containingDeclaration)}"
            } ?: "")
        }
        else -> name.asString() to when (this) {
            is VariableDescriptor -> renderer.renderType(type)
            is ClassDescriptor -> " (${DescriptorUtils.getFqName(containingDeclaration)})"
            else -> renderer.render(this)
        }
    }

    private val renderer = IdeDescriptorRenderers.SOURCE_CODE.withOptions {
        classifierNamePolicy = ClassifierNamePolicy.SHORT
        typeNormalizer = IdeDescriptorRenderers.APPROXIMATE_FLEXIBLE_TYPES
        parameterNameRenderingPolicy = ParameterNameRenderingPolicy.NONE
        typeNormalizer = {
            if (it.isFlexible()) it.asFlexibleType().upperBound
            else it
        }
    }

    private fun iconFrom(descriptor: DeclarationDescriptor) = when (descriptor) {
        is FunctionDescriptor -> "method"
        is PropertyDescriptor -> "property"
        is LocalVariableDescriptor -> "property"
        is ClassDescriptor -> "class"
        is PackageFragmentDescriptor -> "package"
        is PackageViewDescriptor -> "package"
        is ValueParameterDescriptor -> "genericValue"
        is TypeParameterDescriptorImpl -> "class"
        else -> ""
    }

    private fun formatName(builder: String, symbols: Int) = if (builder.length > symbols) builder.substring(0, symbols) + "..." else builder

    private fun keywordsCompletionVariants(keywords: TokenSet, prefix: String) = keywords.types.mapNotNull {
        if (it is KtKeywordToken && it.value.startsWith(prefix)) Completion(it.value, it.value, "", "") else null
    }
    private fun Analysis.referenceVariantsFrom(element: PsiElement) = when (element) {
        is KtSimpleNameExpression -> ReferenceVariantsHelper(
            analysisResult.bindingContext,
            resolutionFacade = KotlinResolutionFacade(kotlinEnvironment.project, componentProvider),
            moduleDescriptor = analysisResult.moduleDescriptor,
            visibilityFilter = { true }
        ).getReferenceVariants(element, DescriptorKindFilter.ALL, { true }, true, true, true, null).toList()
        else -> null
    }
    private fun descriptorsFrom(file: KotlinFile, element: PsiElement): DescriptorInfo =
        with(analysisOf(file)) {
            (referenceVariantsFrom(element) ?: referenceVariantsFrom(element.parent))?.let {
                    descriptors -> DescriptorInfo(true, descriptors)
            } ?: element.parent.let { parent -> DescriptorInfo(
                    isTipsManagerCompletion = false,
                    descriptors = when (parent) {
                        is KtQualifiedExpression -> {
                            analysisResult.bindingContext.get(BindingContext.EXPRESSION_TYPE_INFO, parent.receiverExpression)?.type?.let {
                                expressionType -> analysisResult.bindingContext.get(BindingContext.LEXICAL_SCOPE, parent.receiverExpression)?.let {
                                    expressionType.memberScope.getContributedDescriptors(DescriptorKindFilter.ALL, MemberScope.ALL_NAME_FILTER)
                                }
                            }?.toList() ?: emptyList()
                        }
                        else -> analysisResult.bindingContext.get(BindingContext.LEXICAL_SCOPE, element as KtExpression)
                            ?.getContributedDescriptors(DescriptorKindFilter.ALL, MemberScope.ALL_NAME_FILTER)
                            ?.toList() ?: emptyList()
                    }
                )
            }
        }

    private fun analysisOf(file: KotlinFile): Analysis = CliBindingTrace().let { trace ->
        val componentProvider = TopDownAnalyzerFacadeForJVM.createContainer(
            kotlinEnvironment.project,
            listOf(file.kotlinFile),
            trace,
            kotlinEnvironment.configuration,
            { globalSearchScope -> kotlinEnvironment.createPackagePartProvider(globalSearchScope) },
            { storageManager, ktFiles -> FileBasedDeclarationProviderFactory(storageManager, ktFiles) },
            TopDownAnalyzerFacadeForJVM.newModuleSearchScope(file.kotlinFile.project, listOf(file.kotlinFile))
        )
        componentProvider.getService(LazyTopDownAnalyzer::class.java)
            .analyzeDeclarations(TopDownAnalysisMode.TopLevelDeclarations, listOf(file.kotlinFile), DataFlowInfo.EMPTY)
        val moduleDescriptor = componentProvider.getService(ModuleDescriptor::class.java)
        AnalysisHandlerExtension.getInstances(file.kotlinFile.project)
            .find { it.analysisCompleted(file.kotlinFile.project, moduleDescriptor, trace, listOf(file.kotlinFile)) != null }
        Analysis(
            componentProvider,
            AnalysisResult.success(trace.bindingContext, moduleDescriptor)
        )
    }

    companion object {
        fun with(classpath: List<File>) = KotlinEnvironment(KotlinCoreEnvironment.createForTests(
            parentDisposable = Disposable {},
            extensionConfigs = EnvironmentConfigFiles.JVM_CONFIG_FILES,
            initialConfiguration = CompilerConfiguration().apply {
                addJvmClasspathRoots(classpath.filter { it.exists() && it.isFile && it.extension == "jar" })
                put(CLIConfigurationKeys.MESSAGE_COLLECTOR_KEY, MessageCollector.NONE)
                put(JVMConfigurationKeys.ADD_BUILT_INS_FROM_COMPILER_TO_DEPENDENCIES, true)
                put(CommonConfigurationKeys.MODULE_NAME, UUID.randomUUID().toString())
                with(K2JVMCompilerArguments()) {
                    put(JVMConfigurationKeys.DISABLE_PARAM_ASSERTIONS, noParamAssertions)
                    put(JVMConfigurationKeys.DISABLE_CALL_ASSERTIONS, noCallAssertions)
                }
            }
        ))
    }
}