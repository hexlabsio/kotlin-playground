package io.play.kotlin

import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.CharsetToolkit
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.impl.PsiFileFactoryImpl
import com.intellij.psi.tree.TokenSet
import com.intellij.testFramework.LightVirtualFile
import org.jetbrains.kotlin.analyzer.AnalysisResult
import org.jetbrains.kotlin.container.ComponentProvider
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.descriptors.impl.LocalVariableDescriptor
import org.jetbrains.kotlin.descriptors.impl.TypeParameterDescriptorImpl
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.idea.codeInsight.ReferenceVariantsHelper
import org.jetbrains.kotlin.idea.resolve.ResolutionFacade
import org.jetbrains.kotlin.idea.util.IdeDescriptorRenderers
import org.jetbrains.kotlin.lexer.KtKeywordToken
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.renderer.ClassifierNamePolicy
import org.jetbrains.kotlin.renderer.ParameterNameRenderingPolicy
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.DescriptorUtils
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.kotlin.resolve.scopes.DescriptorKindFilter
import org.jetbrains.kotlin.resolve.scopes.MemberScope
import org.jetbrains.kotlin.types.asFlexibleType
import org.jetbrains.kotlin.types.isFlexible

class CompletionProvider(var file: KtFile, val lineNumber: Int, val charNumber: Int, val project: Project = file.project, var document: Document = file.viewProvider.document!!) {
    private val NUMBER_OF_CHAR_IN_COMPLETION_NAME = 40
    private var caretPositionOffset = 0

    data class CompletionVariant(val text: String, val displayText: String, val tail: String, val icon: String)

    private val RENDERER = IdeDescriptorRenderers.SOURCE_CODE.withOptions{
        classifierNamePolicy = ClassifierNamePolicy.SHORT
           typeNormalizer = IdeDescriptorRenderers.APPROXIMATE_FLEXIBLE_TYPES
           parameterNameRenderingPolicy = ParameterNameRenderingPolicy.NONE
            typeNormalizer = {
                if (it.isFlexible()) it.asFlexibleType().upperBound
                else it
            }
        }
    private val VISIBILITY_FILTER = { _: DeclarationDescriptor ->  true }
    private val NAME_FILTER = { _: Name -> true }

    private fun getPresentableText(descriptor: DeclarationDescriptor): Pair<String, String> {
        var presentableText = descriptor.name.asString()
        var tailText  = ""
        var typeText = ""
        if (descriptor is FunctionDescriptor) {
            val returnType = descriptor.returnType
            typeText = returnType?.let { RENDERER.renderType(it) } ?: ""
            presentableText += RENDERER.renderFunctionParameters(descriptor)
            if ( descriptor.extensionReceiverParameter != null) {
                tailText += " for " + RENDERER.renderType(descriptor.extensionReceiverParameter!!.type)
                tailText += " in " + DescriptorUtils.getFqName(descriptor.containingDeclaration)
            }
        } else if (descriptor is VariableDescriptor) {
            typeText = RENDERER.renderType(descriptor.type)
        } else if (descriptor is ClassDescriptor) {
            val declaredIn = descriptor.containingDeclaration
            tailText = " (" + DescriptorUtils.getFqName(declaredIn) + ")"
        } else {
            typeText = RENDERER.render(descriptor)
        }

        return if (typeText.isEmpty()) {
            presentableText to tailText
        } else {
            presentableText to typeText
        }
    }

    private fun offsetFrom(line: Int, charNumber: Int) = document.getLineStartOffset(line) + charNumber
    private fun createFile(project: Project, name: String, text: String): KtFile {
        val fileName = if(name.endsWith(".kt")) name else "$name.kt"
        val virtualFile = LightVirtualFile(fileName, KotlinLanguage.INSTANCE, text)
        virtualFile.setCharset(CharsetToolkit.UTF8_CHARSET)
        return (PsiFileFactory.getInstance(project) as PsiFileFactoryImpl).trySetupPsiForFile(virtualFile, KotlinLanguage.INSTANCE, true, false) as KtFile
    }

    private fun expressionForScope(): PsiElement? {
        var element = file.findElementAt(caretPositionOffset)
        while (element !is KtExpression && element != null) {
            element = element.parent
        }
        return element
    }

    private fun addExpressionAtCaret() {
        caretPositionOffset = offsetFrom(lineNumber, charNumber)
        val text = file.text
        if (caretPositionOffset != 0) {
            val buffer = StringBuilder(text.substring(0, caretPositionOffset))
            buffer.append("IntellijIdeaRulezzz ")
            buffer.append(text.substring(caretPositionOffset))
            file = createFile(project, file.name, buffer.toString())
            document = file.viewProvider.document!!
        }
    }

    private fun descriptorsFrom(element: PsiElement, bindingContext: BindingContext, referenceVariantsHelper: ReferenceVariantsHelper): Pair<Boolean, Collection<DeclarationDescriptor>>{
        return when{
            element is KtSimpleNameExpression -> true to referenceVariantsHelper.getReferenceVariants(element, DescriptorKindFilter.ALL, NAME_FILTER, true, true, true, null)
            element.parent is KtSimpleNameExpression -> true to referenceVariantsHelper.getReferenceVariants(element.parent as KtSimpleNameExpression, DescriptorKindFilter.ALL, NAME_FILTER, true, true, true, null)
            else -> false to with(element.parent){
                if(this is KtQualifiedExpression){
                    val expressionType = bindingContext.get(BindingContext.EXPRESSION_TYPE_INFO, receiverExpression)?.type
                    val resolutionScope = bindingContext.get(BindingContext.LEXICAL_SCOPE, receiverExpression)
                    if(expressionType != null && resolutionScope != null){
                        expressionType.memberScope.getContributedDescriptors(DescriptorKindFilter.ALL, MemberScope.ALL_NAME_FILTER)
                    } else emptyList()
                }
                else {
                    bindingContext.get(BindingContext.LEXICAL_SCOPE, element as KtExpression)
                            ?.getContributedDescriptors(DescriptorKindFilter.ALL, MemberScope.ALL_NAME_FILTER) ?: emptyList()
                }
            }
        }
    }

    private fun completionVariantFor(prefix: String, descriptor: DeclarationDescriptor): CompletionVariant?{
        val (name, tail) = getPresentableText(descriptor)
        val fullName: String = formatName(name, NUMBER_OF_CHAR_IN_COMPLETION_NAME)
        var completionText = fullName
        var position = completionText.indexOf('(')
        if(position != -1){
            if(completionText[position-1] == ' ') position -= 2
            if(completionText[position+1] == ')') position++
            completionText = completionText.substring(0, position + 1)
        }
        position = completionText.indexOf(":")
        if(position != -1) completionText = completionText.substring(0, position - 1)
        return if(prefix.isEmpty() || fullName.startsWith(prefix)){
            CompletionVariant(completionText, fullName, tail, iconFrom(descriptor))
        } else null
    }

    fun getResult(): List<CompletionVariant>{
        addExpressionAtCaret()
        val (analysisResult, componentProvider) = Resolver.analyze(listOf(file), project)
        val bindingContext = analysisResult.bindingContext
        val element = expressionForScope()
        return if(element != null){
            val referenceVariantsHelper = ReferenceVariantsHelper(bindingContext, KotlinResolutionFacade(project, componentProvider) ,analysisResult.moduleDescriptor, VISIBILITY_FILTER)
            val (isTipsManagerCompletion, descriptors) = descriptorsFrom(element, bindingContext, referenceVariantsHelper)
            val prefix = (if(isTipsManagerCompletion) element.text else element.parent.text).substringBefore("IntellijIdeaRulezzz").let {
                if(it.endsWith(".")) "" else it
            }
            descriptors.toMutableList().let {
                it.sortWith(Comparator { a, b ->
                    val (a1, a2) = getPresentableText(a)
                    val (b1, b2) = getPresentableText(b)
                    ("$a1$a2").compareTo("$b1$b2", true)
                })
                it.mapNotNull { descriptor ->
                    completionVariantFor(prefix, descriptor)
                } + keywordsCompletionVariants(KtTokens.KEYWORDS, prefix) + keywordsCompletionVariants(KtTokens.SOFT_KEYWORDS, prefix)
            }
        } else emptyList()
    }

    private fun iconFrom(descriptor: DeclarationDescriptor) = when(descriptor) {
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

    private fun formatName(builder: String, symbols: Int) =  if (builder.length > symbols) builder.substring(0, symbols) + "..." else builder

    private fun keywordsCompletionVariants(keywords: TokenSet, prefix: String) = keywords.types.mapNotNull {
        if (it is KtKeywordToken && it.value.startsWith(prefix)) CompletionVariant(it.value, it.value, "", "") else null
    }

    class KotlinResolutionFacade(override val project: Project, private val componentProvider: ComponentProvider): ResolutionFacade{

        override fun <T : Any> getFrontendService(serviceClass: Class<T>): T {
            return componentProvider.resolve(serviceClass)?.getValue() as T
        }
        override val moduleDescriptor: ModuleDescriptor
            get() = TODO("not implemented")

        override fun analyze(elements: Collection<KtElement>, bodyResolveMode: BodyResolveMode): BindingContext = TODO("not implemented")
        override fun analyze(element: KtElement, bodyResolveMode: BodyResolveMode): BindingContext = TODO("not implemented")
        override fun analyzeWithAllCompilerChecks(elements: Collection<KtElement>): AnalysisResult = TODO("not implemented")
        override fun <T : Any> getFrontendService(element: PsiElement, serviceClass: Class<T>): T = TODO("not implemented")
        override fun <T : Any> getFrontendService(moduleDescriptor: ModuleDescriptor, serviceClass: Class<T>): T = TODO("not implemented")
        override fun <T : Any> getIdeService(serviceClass: Class<T>): T  = TODO("not implemented")
        override fun resolveToDescriptor(declaration: KtDeclaration, bodyResolveMode: BodyResolveMode): DeclarationDescriptor = TODO("not implemented")
        override fun <T : Any> tryGetFrontendService(element: PsiElement, serviceClass: Class<T>): T? = TODO("not implemented")
    }
}