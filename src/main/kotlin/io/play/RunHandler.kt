package io.play

import io.play.kotlin.CompletionProvider
import io.play.kotlin.EnvironmentManager
import io.play.kotlin.EnvironmentManager.basePath
import io.play.kotlin.JavaExecuter
import io.play.kotlin.KotlinWrapper
import io.play.model.Project
import java.io.File
import java.lang.Math.abs
import java.nio.file.Files
import java.nio.file.Paths
import java.util.*


object RunHandler {
    fun run(project: Project): JavaExecuter.ExecutionResult{
        val compilationResult = KotlinWrapper.compile(project)
        return if(compilationResult.first != null) {
            (JavaExecuter.execute(argsFrom(compilationResult.first!!)) as JavaExecuter.JavaExecutionResult).copy(errors = compilationResult.second.errors)
        }
        else compilationResult.second
    }
    fun highlight(project: Project) = KotlinWrapper.compile(project).second.errors
    fun complete(project: Project, line: Int, character: Int): List<CompletionProvider.CompletionVariant>{
        return CompletionProvider(KotlinWrapper.createFile(project.files.first().name, project.files.first().text),line, character).getResult()
    }

    private fun argsFrom(compilationResult: KotlinWrapper.CompilationResult): Array<String>{
        val outputDir = Paths.get(basePath,"generated", abs(Random().nextInt()).toString())
        val policy = File("executor.policy").readText().replace("%%GENERATED%%", outputDir.toString()).replace("%%LIB_DIR%%",EnvironmentManager.basePath)
        val executorPolicy = outputDir.resolve("executor.policy").toString()
        compilationResult.files.forEach { name, bytes ->
            val path = outputDir.resolve(name)
            path.parent.toFile().mkdirs()
            Files.write(path, bytes)
        }
        File(executorPolicy).writeText(policy)
        return arrayOf(
                    "java",
                    "-Djava.security.manager",
                    "-Djava.security.policy=$executorPolicy",
                    "-classpath"
            ) + (EnvironmentManager.classPathUris + outputDir.toFile()).foldIndexed("") { index, acc, file -> acc + (if(index > 0) ":" else "") + file.absoluteFile} +
                    compilationResult.mainClass
    }
}



