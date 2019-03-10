package io.hexlabs.kotlin.api

import io.hexlabs.kotlin.playground.Configuration
import io.hexlabs.kotlin.playground.VersionInfo
import org.http4k.core.Method
import org.http4k.core.Response
import org.http4k.core.Status.Companion.OK
import org.http4k.core.with
import org.http4k.routing.bind
import org.http4k.routing.routes

class PlaygroundHandler(val configuration: Configuration){
    val playgroundRoutes = routes(
        "/" bind Method.GET to { Response(OK) },
        "/getKotlinVersions" bind Method.GET to { Response(OK).with(bodyAs(listOf(VersionInfo(
            version = configuration.kotlinVersion,
            build = configuration.kotlinVersion,
            stdlibVersion = configuration.kotlinVersion
        )))) }
    )
}