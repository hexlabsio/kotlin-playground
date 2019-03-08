package io.hexlabs.kotlin.api

import com.beust.jcommander.IStringConverter
import com.beust.jcommander.Parameter
import java.io.File

class Options(
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
    var disableSecurity: Boolean = false,
    @Parameter(
        names = ["-p", "-port"],
        description = "Port to bind to"
    )
    var port: Int = 80,
    @Parameter(
        names = ["-disable-health"],
        description = "Disables Health Endpoint"
    )
    var disableHealth: Boolean = false,
    @Parameter(
        names = ["-health-path"],
        description = "Sets Health Endpoint"
    )
    var healthPath: String = "/",
    @Parameter(
        names = ["-health-status"],
        description = "Sets Health Endpoint Status"
    )
    var healthStatus: Int = 200,
    @Parameter(
        names = ["-health-body"],
        description = "Sets Health Endpoint Response Body"
    )
    var healthBody: String = "Healthy"
)

class FileConverter : IStringConverter<File> {
    override fun convert(value: String): File {
        return File(value).also { if(!it.exists()) it.mkdirs() }
    }
}