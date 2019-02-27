package io.play

import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.s3.model.GetObjectRequest
import io.play.kotlin.CompletionProvider
import io.play.kotlin.EnvironmentManager
import io.play.kotlin.ErrorHandler
import io.play.kotlin.JavaExecuter
import io.play.model.Project
import org.http4k.core.*
import org.http4k.core.Status.Companion.OK
import org.http4k.filter.CorsPolicy
import org.http4k.filter.ServerFilters
import org.http4k.format.Jackson
import org.http4k.server.SunHttp
import org.http4k.format.Jackson.auto
import org.http4k.lens.*
import org.http4k.routing.bind
import org.http4k.routing.routes
import org.http4k.server.asServer
import org.http4k.serverless.AppLoader
import java.io.File

fun main(args: Array<String>){
    root.asServer(SunHttp(80)).start()
    println("Server Started on Port 80")
}
val tracing = ServerFilters.RequestTracing({ request, zipkin ->
    println("START [${zipkin.traceId.value} ${request.method.name}] - ${request.uri} ")
}, { request, response, zipkin ->
    println("END [${zipkin.traceId.value} ${request.method.name}] - ${request.uri} RESPONSE - ${response.status}")
})
val root = Filter{ next -> { it ->
    try{
        next(it.removeHeader("Accept"))
    } catch (e: Exception){
        e.printStackTrace()
        Response(Status.INTERNAL_SERVER_ERROR)
    }
}
}.then(ServerFilters.Cors(CorsPolicy.UnsafeGlobalPermissive)).then(tracing).then(routes(
        "/kotlinServer" bind Method.GET to RootHandler,
        "/kotlinServer" bind Method.POST to RootHandler,
        "/" bind Method.GET to { Response(Status.OK).body("Healthy") }
))

object LambdaHandler: AppLoader{
    override fun invoke(p1: Map<String, String>) = root
}

object RootHandler: HttpHandler{
    private val result = Body.auto<JavaExecuter.ExecutionResult>().toLens()
    private val highlightResult = Body.auto<Map<String, List<ErrorHandler.ErrorDescriptor>>>().toLens()
    private val completionResult = Body.auto<List<CompletionProvider.CompletionVariant>>().toLens()
    override fun invoke(request: Request): Response {
        val type = request.query("type")
        return when(type){
            "getKotlinVersions" -> {
                Response(OK).body("[{\"version\":\"1.3.0\",\"build\":\"1.3.0\",\"obsolete\":false,\"latestStable\":true,\"hasScriptJar\":false,\"stdlibVersion\":\"1.3.0\"}]")
            }
            "highlight" -> {
                setupEnvironment()
                Response(OK).with(highlightResult of RunHandler.highlight(projectFrom(request)))
            }
            "complete" -> {
                setupEnvironment()
                Response(OK).with(completionResult of RunHandler.complete(projectFrom(request), lineFrom(request), characterFrom(request))
                )
            }
            "run" -> {
                setupEnvironment()
                Response(OK).with(result of RunHandler.run(projectFrom(request)))
            }
            else -> Response(Status.OK)
        }
    }
    private fun setupEnvironment(){
        EnvironmentManager.createEnvironment()
    }
    private fun lineFrom(request: Request): Int{
        val lineField = FormField.int().required("line")
        return Body.webForm(Validator.Feedback, lineField).toLens()(request).let {
            lineField.extract(it)
        }
    }
    private fun characterFrom(request: Request): Int{
        val characterField = FormField.int().required("ch")
        return Body.webForm(Validator.Feedback, characterField).toLens()(request).let {
            characterField.extract(it)
        }
    }
    private fun projectFrom(request: Request): Project{
        val projectField = FormField.string().required("project")
        return Body.webForm(Validator.Feedback, projectField).toLens()(request).let {
            //val fileName = fileNameField.extract(it)
            Jackson.asA(projectField.extract(it), Project::class)
        }
    }
}
