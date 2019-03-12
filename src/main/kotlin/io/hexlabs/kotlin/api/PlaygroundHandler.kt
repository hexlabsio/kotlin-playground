package io.hexlabs.kotlin.api

import io.hexlabs.kotlin.playground.KotlinEnvironment
import io.hexlabs.kotlin.playground.KotlinFile
import io.hexlabs.kotlin.playground.model.Configuration
import io.hexlabs.kotlin.playground.model.Project
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
import java.util.UUID

class PlaygroundHandler(private val environment: KotlinEnvironment, private val configuration: Configuration) {

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
        val line = lineFrom(request)
        val character = characterFrom(request)
        val file = KotlinFile.from(environment.kotlinEnvironment.project, UUID.randomUUID().toString(), project.files.firstOrNull()?.text ?: "")
        return when (Operations.from(typeFrom(request), requiresBody = true)) {
            Operations.COMPLETE -> Response(OK).with(bodyAs(environment.complete(file, line, character)))
            else -> Response(NOT_FOUND).body(textForNotFoundOperations(post = true))
        }
    }

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