package io.hexlabs.kotlin.api

import io.hexlabs.kotlin.playground.Configuration
import org.http4k.core.HttpHandler
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status.Companion.OK


class Playground(configuration: Configuration): HttpHandler {
    override fun invoke(request: Request): Response {
        throw Exception("Hello")
        return Response(OK)
    }
}