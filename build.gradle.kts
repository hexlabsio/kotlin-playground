import Build_gradle.Props.http4kVersion
import Build_gradle.Props.kotlinPluginArtifact
import Build_gradle.Props.kotlinPluginLocation
import Build_gradle.Props.kotlinVersion
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import com.jfrog.bintray.gradle.BintrayExtension
import groovy.util.Node
import groovy.util.NodeList
import groovy.xml.dom.DOMCategory.appendNode
import org.gradle.internal.impldep.com.amazonaws.util.XpathUtils.asNode
import org.jetbrains.kotlin.contracts.model.structure.UNKNOWN_COMPUTATION.type
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jlleitschuh.gradle.ktlint.KtlintExtension
import org.jlleitschuh.gradle.ktlint.reporter.ReporterType
import java.net.URL

fun version(): String {
    val buildNumber = System.getProperty("BUILD_NUM")
    val version = "0.1" + if (buildNumber.isNullOrEmpty()) "-SNAPSHOT" else ".$buildNumber"
    println("building version $version")
    return version
}

val projectVersion = version()

group = "io.hexlabs"
val artifactId = "kotlin-playground"
version = projectVersion


plugins {
    kotlin("jvm") version "1.3.21"
    id("org.jlleitschuh.gradle.ktlint") version "7.1.0"
    id("com.jfrog.bintray") version "1.8.4"
    id("com.github.johnrengelman.shadow") version "4.0.4"
    `maven-publish`
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
            build.version=$projectVersion
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
val shadowJar by tasks.getting(ShadowJar::class) {
    classifier = "uber"
    manifest {
        attributes(mapOf("Main-Class" to "io.hexlabs.kotlin.api.RootHandlerKt"))
    }
//    dependencies {
//        exclude(dependency("org.jetbrains.kotlin::$kotlinVersion"))
//    }
}

val jar by tasks.getting(Jar::class) {
    manifest {
        attributes["Main-Class"] = "io.hexlabs.kotlin.api.RootHandlerKt"
    }
}

artifacts {
    add("archives", shadowJar)
}

fun dependencyFrom(url: String, artifact: String, version: String) = File("$buildDir/download/$artifact-$version.jar").let { file ->
    //if(!file.exists()) {
        file.parentFile.mkdirs()
        file.writeBytes(URL(url).readBytes())
    //}
    files(file.absolutePath)
}

fun DependencyHandlerScope.compile(group: String, version: String, dependencies: List<String>) =
    dependencies.map { dependency -> compile("$group:$dependency:$version") }

object Props {
    const val kotlinVersion = "1.3.21"
    const val http4kVersion = "3.115.1"
    const val kotlinPluginArtifact = "kotlin-plugin"
    private const val kotlinRepository = "Kotlin_1320_CompilerAllPlugins"
    private const val kotlinId = "2034508"
    private const val kotlinPluginRelease = "release-IJ2018.3-2"
    const val kotlinPluginLocation = "$kotlinRepository/$kotlinId:id/$kotlinPluginArtifact-$kotlinVersion-$kotlinPluginRelease.zip!/Kotlin/lib/$kotlinPluginArtifact.jar"
    // https://teamcity.jetbrains.com/repository/download/Kotlin_1320_CompilerAllPlugins/2034508:id/kotlin-plugin-1.3.21-release-IJ2018.3-2.zip}
}

configure<KtlintExtension> {
    verbose.set(true)
    outputToConsole.set(true)
    coloredOutput.set(true)
    reporters.set(setOf(ReporterType.CHECKSTYLE, ReporterType.JSON))
}



bintray {
    user = "hexlabs-builder"
    key = System.getProperty("BINTRAY_KEY") ?: "UNKNOWN"
    setPublications("mavenJava")
    publish = true
    pkg(
        closureOf<BintrayExtension.PackageConfig> {
            repo = "hexlabs-maven"
            name = artifactId
            userOrg = "hexlabsio"
            setLicenses("Apache-2.0")
            vcsUrl = "https://github.com/hexlabsio/kotlin-playground.git"
            version(closureOf<BintrayExtension.VersionConfig> {
                name = projectVersion
                desc = projectVersion
            })
        })
}

publishing {
    publications {
        register("mavenJava", MavenPublication::class) {
            from(components["java"])
            artifactId = artifactId
            pom.withXml {
                val dependencies = (asNode()["dependencies"] as NodeList)
                configurations.compile.allDependencies.forEach {
                    dependencies.add(Node(null, "dependency").apply {
                        appendNode("groupId", it.group)
                        appendNode("artifactId", it.name)
                        appendNode("version", it.version)
                    })
                }
            }
        }
    }
}
