package io.hexlabs.kotlin.api

import org.http4k.core.Filter
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.filter.ServerFilters

object Filters{
    val TRACING = ServerFilters.RequestTracing({ request, zipkin ->
        println("START [${zipkin.traceId.value} ${request.method.name}] - ${request.uri} ")
    }, { request, response, zipkin ->
        println("END [${zipkin.traceId.value} ${request.method.name}] - ${request.uri} RESPONSE - ${response.status}")
    })
    val CATCH_ALL = Filter { next -> { request ->
        try { next(request) } catch(e: Exception) {
            e.printStackTrace()
            Response(Status.INTERNAL_SERVER_ERROR)
        }
    }}
}