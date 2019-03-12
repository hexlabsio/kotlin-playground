import Build_gradle.Props.http4kVersion
import Build_gradle.Props.kotlinPluginArtifact
import Build_gradle.Props.kotlinPluginLocation
import Build_gradle.Props.kotlinVersion
import org.jetbrains.kotlin.contracts.model.structure.UNKNOWN_COMPUTATION.type
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jlleitschuh.gradle.ktlint.KtlintExtension
import org.jlleitschuh.gradle.ktlint.reporter.ReporterType
import java.net.URL

group = "io.hexlabs"
version = "0.1-SNAPSHOT"

plugins {
    kotlin("jvm") version "1.3.21"
    id("org.jlleitschuh.gradle.ktlint") version "7.1.0"
}

repositories {
    mavenCentral()
    jcenter()
}

val kotlinDependency by configurations.creating

val copyDependencies by tasks.creating(Copy::class) {
    from(kotlinDependency)
    into("lib")
}

dependencies {
    File("src/main/resources/configuration.properties").apply{
        parentFile.mkdirs()
        writeText("""
            build.version=$version
            kotlin.version=$kotlinVersion
        """.trimIndent())
    }
    kotlinDependency(kotlin("stdlib-jdk8"))
    kotlinDependency(kotlin("reflect"))
    compile(
        group = "org.jetbrains.kotlin",
        version = kotlinVersion,
        dependencies = listOf("kotlin-stdlib-jdk8", "kotlin-compiler")
    )
    compile(
        group = "org.http4k",
        version = http4kVersion,
        dependencies = listOf("http4k-core", "http4k-format-jackson", "http4k-multipart")
    )
    compile("com.beust:jcommander:1.72")
    compile(dependencyFrom("https://teamcity.jetbrains.com/guestAuth/repository/download/$kotlinPluginLocation",
        artifact = kotlinPluginArtifact,
        version = kotlinVersion)
    )
    testImplementation(group = "org.jetbrains.kotlin", name = "kotlin-test-junit5", version = kotlinVersion)
    testImplementation(group = "org.junit.jupiter", name = "junit-jupiter-api", version = "5.4.0")
    testRuntime(group = "org.junit.jupiter", name = "junit-jupiter-engine", version = "5.4.0")
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
    finalizedBy(copyDependencies)
}
tasks.withType<Test> {
    useJUnitPlatform()
}

fun dependencyFrom(url: String, artifact: String, version: String) = File("$buildDir/download/$artifact-$version.jar").let { file ->
    file.parentFile.mkdirs()
    file.writeBytes(URL(url).readBytes())
    files(file.absolutePath)
}

fun DependencyHandlerScope.compile(group: String, version: String, dependencies: List<String>) =
    dependencies.map { dependency -> compile("$group:$dependency:$version") }

object Props {
    const val kotlinVersion = "1.3.21"
    const val http4kVersion = "3.115.1"
    const val kotlinPluginArtifact = "kotlin-plugin"
    private const val kotlinRepository = "Kotlin_1320_CompilerAllPlugins"
    private const val kotlinId = "1937367"
    private const val kotlinPluginRelease = "release-IJ2018.3-1"
    const val kotlinPluginLocation = "$kotlinRepository/$kotlinId:id/$kotlinPluginArtifact-$kotlinVersion-$kotlinPluginRelease.zip!/Kotlin/lib/$kotlinPluginArtifact.jar"
}

configure<KtlintExtension> {
    verbose.set(true)
    outputToConsole.set(true)
    coloredOutput.set(true)
    reporters.set(setOf(ReporterType.CHECKSTYLE, ReporterType.JSON))
}
