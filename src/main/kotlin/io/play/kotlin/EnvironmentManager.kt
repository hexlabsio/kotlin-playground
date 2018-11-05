package io.play.kotlin

import com.intellij.codeInsight.NullabilityAnnotationInfo
import com.intellij.codeInsight.NullableNotNullManager
import com.intellij.mock.MockProject
import com.intellij.openapi.Disposable
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Getter
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiAnnotation
import org.jetbrains.kotlin.cli.common.CLIConfigurationKeys
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.cli.jvm.config.addJvmClasspathRoot
import org.jetbrains.kotlin.cli.jvm.config.addJvmClasspathRoots
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.JVMConfigurationKeys
import org.jetbrains.kotlin.js.config.JSConfigurationKeys
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths

object EnvironmentManager{
    val basePath = if(System.getenv()["Environment"] != "local") "/tmp/lib" else (System.getProperty("user.dir") + "/lib")
    val classPathUris = File(basePath).listFiles { file: File -> file.name.endsWith(".jar")}.map {
        it
    }

    val environment: KotlinCoreEnvironment = createEnvironment()

    fun createEnvironment(): KotlinCoreEnvironment{
        val arguments = K2JVMCompilerArguments()
        val configurations = CompilerConfiguration()
        configurations.addJvmClasspathRoots(classPathUris)
        configurations.put(CLIConfigurationKeys.MESSAGE_COLLECTOR_KEY, MessageCollector.NONE)
        configurations.put(JVMConfigurationKeys.DISABLE_PARAM_ASSERTIONS, arguments.noParamAssertions)
        configurations.put(JVMConfigurationKeys.DISABLE_CALL_ASSERTIONS, arguments.noCallAssertions)

        val environment = KotlinCoreEnvironment.createForTests(Disposable {}, configurations,EnvironmentConfigFiles.JVM_CONFIG_FILES)
        //(environment.project as MockProject).also{ it.registerService(NullableNotNullManager::class.java,NotNullManager(it)) }
        return environment
    }
}
