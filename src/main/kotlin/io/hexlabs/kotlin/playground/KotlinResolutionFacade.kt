package io.hexlabs.kotlin.playground

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analyzer.AnalysisResult
import org.jetbrains.kotlin.container.ComponentProvider
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.idea.resolve.ResolutionFacade
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode

class KotlinResolutionFacade(override val project: Project, private val componentProvider: ComponentProvider) : ResolutionFacade {
    override fun <T : Any> getFrontendService(serviceClass: Class<T>) = componentProvider.resolve(serviceClass)?.getValue() as T
    override val moduleDescriptor: ModuleDescriptor get() = TODO("not implemented")
    override fun analyze(elements: Collection<KtElement>, bodyResolveMode: BodyResolveMode): BindingContext = TODO("not implemented")
    override fun analyze(element: KtElement, bodyResolveMode: BodyResolveMode): BindingContext = TODO("not implemented")
    override fun analyzeWithAllCompilerChecks(elements: Collection<KtElement>): AnalysisResult = TODO("not implemented")
    override fun <T : Any> getFrontendService(element: PsiElement, serviceClass: Class<T>): T = TODO("not implemented")
    override fun <T : Any> getFrontendService(moduleDescriptor: ModuleDescriptor, serviceClass: Class<T>): T = TODO("not implemented")
    override fun <T : Any> getIdeService(serviceClass: Class<T>): T = TODO("not implemented")
    override fun resolveToDescriptor(declaration: KtDeclaration, bodyResolveMode: BodyResolveMode): DeclarationDescriptor = TODO("not implemented")
    override fun <T : Any> tryGetFrontendService(element: PsiElement, serviceClass: Class<T>): T? = TODO("not implemented")
}