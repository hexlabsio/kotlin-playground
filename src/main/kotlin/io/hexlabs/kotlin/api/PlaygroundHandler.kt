package io.hexlabs.kotlin.api

import io.hexlabs.kotlin.playground.Configuration
import io.hexlabs.kotlin.playground.Operations
import io.hexlabs.kotlin.playground.VersionInfo
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status.Companion.NOT_FOUND
import org.http4k.core.Status.Companion.OK
import org.http4k.core.with
import org.http4k.lens.Query
import org.http4k.routing.bind
import org.http4k.routing.routes

class PlaygroundHandler(private val configuration: Configuration){
    val playgroundRoutes = routes(
        "/" bind Method.GET to ::getRoute
    )

    private fun getRoute(request: Request): Response {
        return when(Operations.from(Query.required("type")(request), requiresBody = false)){
            Operations.KOTLIN_VERSIONS -> Response(OK).with(bodyAs(listOf(VersionInfo(
                version = configuration.kotlinVersion,
                build = configuration.kotlinVersion,
                stdlibVersion = configuration.kotlinVersion
            ))))
            null -> Response(NOT_FOUND).body("Expected the type query parameter to be ${Operations.values().filter { !it.requiresBody }.joinToString(" or "){ it.endpoint }}")
        }
    }
}