package io.hexlabs.kotlin.api

import com.beust.jcommander.JCommander
import io.hexlabs.kotlin.playground.Configuration
import org.http4k.core.Method
import org.http4k.routing.bind
import org.http4k.routing.routes
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.server.SunHttp
import org.http4k.server.asServer

val RootHandler = { options: Options ->
    val playground = Playground(Configuration(libs = options.libs, disableSecurity = options.disableSecurity))
    routes(
        *listOfNotNull(
            if(!options.disableHealth)
                (options.healthPath bind Method.GET to { Response(Status(options.healthStatus, "HealthStatus")).body(options.healthBody) })
                    .also { println("Health endpoint initialised. Will respond with status code ${options.healthStatus} on path ${options.healthPath}")  }
            else null,
            "/kotlinServer" bind routes(
                Method.GET to playground,
                Method.POST to playground
            )
        ).toTypedArray()
    )
}

fun main(args: Array<String>){
    Options().apply { JCommander.newBuilder().addObject(this).build().parse(*args) }.apply {
        RootHandler(this).asServer(SunHttp(port)).start()
        println("Server started on port $port")
    }
}
