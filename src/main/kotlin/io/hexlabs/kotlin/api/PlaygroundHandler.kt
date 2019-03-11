package io.hexlabs.kotlin.api

import io.hexlabs.kotlin.playground.model.Configuration
import io.hexlabs.kotlin.playground.model.VersionInfo
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status.Companion.NOT_FOUND
import org.http4k.core.Status.Companion.OK
import org.http4k.core.with
import org.http4k.lens.Query
import org.http4k.routing.routes

class PlaygroundHandler(private val configuration: Configuration) {
    val playgroundRoutes = routes(
        Method.GET to ::getRoute,
        Method.POST to ::postRoute
    )

    private fun getRoute(request: Request): Response {
        return when (Operations.from(Query.required("type")(request), requiresBody = false)) {
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
        return when (Operations.from(Query.required("type")(request), requiresBody = true)) {
            Operations.COMPLETE -> Response(OK).with(bodyAs(listOf("Hello")))
            else -> Response(NOT_FOUND).body(textForNotFoundOperations(post = true))
        }
    }

    private fun textForNotFoundOperations(post: Boolean) = "Expected the type query parameter to be ${Operations.values().filter { it.requiresBody == post }.joinToString(" or "){ it.endpoint }}"
}