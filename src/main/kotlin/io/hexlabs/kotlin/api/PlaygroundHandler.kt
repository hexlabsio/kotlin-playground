package io.hexlabs.kotlin.api

import io.hexlabs.kotlin.playground.JavaExecutor
import io.hexlabs.kotlin.playground.KotlinCompiler
import io.hexlabs.kotlin.playground.KotlinEnvironment
import io.hexlabs.kotlin.playground.KotlinFile
import io.hexlabs.kotlin.playground.model.Configuration
import io.hexlabs.kotlin.playground.model.ErrorDescriptor
import io.hexlabs.kotlin.playground.model.JavaExecutionResult
import io.hexlabs.kotlin.playground.model.Project
import io.hexlabs.kotlin.playground.model.Severity
import io.hexlabs.kotlin.playground.model.TextInterval
import io.hexlabs.kotlin.playground.model.VersionInfo
import org.http4k.core.Body
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status.Companion.NOT_FOUND
import org.http4k.core.Status.Companion.OK
import org.http4k.core.with
import org.http4k.format.Jackson
import org.http4k.lens.FormField
import org.http4k.lens.Query
import org.http4k.lens.Validator
import org.http4k.lens.int
import org.http4k.lens.string
import org.http4k.lens.webForm
import org.http4k.routing.routes
import org.jetbrains.kotlin.resolve.calls.inference.model.ConstraintStorage.Empty.errors
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.UUID
import javax.xml.soap.Text

class PlaygroundHandler(private val environment: KotlinEnvironment, private val configuration: Configuration) {

    data class OutputDir(val path: Path, val subPaths: List<Path>)

    val typeFrom = Query.required("type")

    val playgroundRoutes = routes(
        Method.GET to ::getRoute,
        Method.POST to ::postRoute
    )

    private fun getRoute(request: Request): Response {
        return when (Operations.from(typeFrom(request), requiresBody = false)) {
            Operations.KOTLIN_VERSIONS -> Response(OK).with(bodyAs(listOf(
                VersionInfo(
                    version = configuration.kotlinVersion,
                    build = configuration.kotlinVersion,
                    stdlibVersion = configuration.kotlinVersion
                )
            )))
            else -> Response(NOT_FOUND).body(textForNotFoundOperations(post = false))
        }
    }

    private fun postRoute(request: Request): Response {
        val project = projectFrom(request)
        val files = project.files.map {
            KotlinFile.from(environment.kotlinEnvironment.project, it.name, it.text)
        }
        return when (Operations.from(typeFrom(request), requiresBody = true)) {
            Operations.COMPLETE -> Response(OK).with(bodyAs(environment.complete(files.first(), lineFrom(request), characterFrom(request))))
            Operations.HIGHLIGHT ->  Response(OK).with(bodyAs(environment.errorsFrom(files.map { it.kotlinFile })))
            Operations.RUN -> {
                val errors = environment.errorsFrom(files.map { it.kotlinFile })
                return Response(OK).with(
                    bodyAs( if(errors.any { it.value.any { error -> error.severity == Severity.ERROR} })
                        JavaExecutionResult("", errors = errors)
                    else {
                        val compilation = KotlinCompiler(environment).compile(files.map { it.kotlinFile })
                        if (compilation.files.isNotEmpty()) {
                            JavaExecutor.execute(argsFrom(compilation.mainClass!!, write(compilation)))
                        } else JavaExecutionResult("", JavaExecutor.ExceptionDescriptor("Something went wrong", "Exception"))
                    })
                )
            }
            else -> Response(NOT_FOUND).body(textForNotFoundOperations(post = true))
        }
    }

    private fun write(compiled: KotlinCompiler.Compiled): PlaygroundHandler.OutputDir {
        val sessionId = UUID.randomUUID().toString().replace("-", "")
        val outputDir = Paths.get(configuration.workingDirectory, "generated", sessionId)
        if(!configuration.disableSecurity) {
            val policy = File("executor.policy").readText()
                .replace("%%GENERATED%%", outputDir.toString())
                .replace("%%LIB_DIR%%", configuration.workingDirectory)
            outputDir.resolve("executor.policy").apply { parent.toFile().mkdirs() }.toFile().writeText(policy)
        }
        return PlaygroundHandler.OutputDir(outputDir, compiled.files.map { (name, bytes) ->
            outputDir.resolve(name).let { path ->
                path.parent.toFile().mkdirs()
                Files.write(path, bytes)
            }
        })
    }

    private fun argsFrom(mainClass: String, outputDirectory: PlaygroundHandler.OutputDir) = listOfNotNull(
        "java",
        if(!configuration.disableSecurity) "-Djava.security.manager" else null,
        if(!configuration.disableSecurity) "-Djava.security.policy=${outputDirectory.path.resolve("executor.policy").toAbsolutePath()}" else null,
        "-classpath"
    ) + (environment.classpath.map { it.absolutePath } + outputDirectory.path.toAbsolutePath().toString()).joinToString(":") +
            mainClass

    private fun lineFrom(request: Request): Int {
        val lineField = FormField.int().required("line")
        return Body.webForm(Validator.Feedback, lineField).toLens()(request).let {
            lineField.extract(it)
        }
    }
    private fun characterFrom(request: Request): Int {
        val characterField = FormField.int().required("ch")
        return Body.webForm(Validator.Feedback, characterField).toLens()(request).let {
            characterField.extract(it)
        }
    }
    private fun projectFrom(request: Request): Project {
        val projectField = FormField.string().required("project")
        return Body.webForm(Validator.Feedback, projectField).toLens()(request).let {
            Jackson.asA(projectField.extract(it), Project::class)
        }
    }

    private fun textForNotFoundOperations(post: Boolean) = "Expected the type query parameter to be ${Operations.values().filter { it.requiresBody == post }.joinToString(" or "){ it.endpoint }}"
}