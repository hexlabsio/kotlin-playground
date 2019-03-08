package io.hexlabs.kotlin.api

import com.beust.jcommander.DynamicParameter
import com.beust.jcommander.JCommander
import com.beust.jcommander.Parameter
import org.http4k.core.Method
import org.http4k.routing.bind
import org.http4k.routing.routes
import com.beust.jcommander.IStringConverter
import java.io.File


val RootHandler = routes(
    "/" bind Method.GET to HealthEndpoint,
    "/kotlinServer" bind routes(
        Method.GET to KotlinEndpoint,
        Method.POST to KotlinEndpoint
    )
)

fun main(args: Array<String>){
    val kotlinArgs = Args().apply { JCommander.newBuilder().addObject(this).build().parse(*args) }

}

class Args(
    @Parameter(
        names = ["-libs", "-libraries"],
        description = "Extra Library Directory",
        converter = FileConverter::class
    )
    var libs: File? = null,
    @Parameter(
        names = ["-disable-security"],
        description = "Disables Security Policy"
    )
    var disableSecurity: Boolean = false
)

class FileConverter : IStringConverter<File> {
    override fun convert(value: String): File {
        return File(value).also { if(!it.exists()) it.mkdirs() }
    }
}
