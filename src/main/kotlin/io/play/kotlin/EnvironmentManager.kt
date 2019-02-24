package io.play.kotlin

import com.intellij.openapi.Disposable
import org.jetbrains.kotlin.cli.common.CLIConfigurationKeys
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.cli.jvm.config.addJvmClasspathRoots
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.JVMConfigurationKeys
import java.io.File

object EnvironmentManager{
    val basePath = System.getProperty("user.dir") + "/lib"
    val classPathUris = File(basePath).listFiles { file: File -> file.name.endsWith(".jar")}.toList().let {
        if(it.isEmpty()) throw IllegalArgumentException("No Libraries found in $basePath")
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

        return KotlinCoreEnvironment.createForTests(Disposable {}, configurations,EnvironmentConfigFiles.JVM_CONFIG_FILES)
    }
}
