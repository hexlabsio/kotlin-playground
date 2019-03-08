package io.hexlabs.kotlin.api

import io.hexlabs.kotlin.playground.Configuration
import org.http4k.core.HttpHandler
import org.http4k.core.Request
import org.http4k.core.Response


class Playground(configuration: Configuration): HttpHandler {
    override fun invoke(request: Request): Response {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
}