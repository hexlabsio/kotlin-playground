package io.hexlabs.kotlin.api

import io.hexlabs.kotlin.playground.KotlinEnvironment
import io.hexlabs.kotlin.playground.KotlinFile
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.with
import org.http4k.format.Jackson
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.io.File
import kotlin.test.expect

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PlaygroundTest {

//    @Test
//    fun go(){
//        val environment = KotlinEnvironment.with(File("lib").listFiles().toList())
//        val file = KotlinFile.from(environment.kotlinEnvironment.project, "fred", "fun main(){ pr0intln(\"hello\") }")
//        val a = environment.errorsFrom(file)
//        println(a)
//    }

    @Nested
    inner class KotlinVersions {
        @Test
        fun `should return version info matching input kotlin version`() {
            val response = RootHandler(Options(kotlinVersion = "1.5.6"))(Request(Method.GET, "/kotlinServer?type=getKotlinVersions"))
            val bodyNode = Jackson.body().toLens()(response)
            expect(true, "Body should be an array") { bodyNode.isArray }
            with(bodyNode.elements().asSequence().toList()) {
                expect(1, "Body should be an array with one item") { size }
                with(first()) {
                    expect(true, "First item should be an object") { isObject }
                    with(fields().asSequence().map { it.key to it.value }.toMap()) {
                        expect("1.5.6") { this["version"]?.asText() }
                        expect("1.5.6") { this["build"]?.asText() }
                        expect("1.5.6") { this["stdlibVersion"]?.asText() }
                        expect(true) { this["latestStable"]?.asBoolean() }
                        expect(false) { this["obsolete"]?.asBoolean() }
                        expect(false) { this["hasScriptJar"]?.asBoolean() }
                    }
                }
            }
        }
    }
}