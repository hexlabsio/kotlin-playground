package io.hexlabs.kotlin.api

import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import kotlin.test.expect

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RootHandlerTest {

    @Nested
    inner class CORSHeaders {
        @Test
        fun `CORS is disabled by default`() {
            val response = RootHandler(Options())(Request(Method.GET, "/"))
            expect(true, "No CORS headers should be present") {
                response.headers.find { CORS.values().map { cors -> cors.header.toLowerCase() }.contains(it.first.toLowerCase()) } == null
            }
        }
        @Test
        fun `should have correct CORS headers if cors enabled`() {
            val response = RootHandler(Options(cors = true))(Request(Method.GET, "/"))
            expect("content-type") { response.header(CORS.HEADERS.header)?.toLowerCase() }
            expect("GET, POST, PUT, DELETE, OPTIONS, TRACE, PATCH, PURGE, HEAD") { response.header(CORS.METHODS.header) }
            expect("*") { response.header(CORS.ORIGINS.header) }
        }
        @Test
        fun `should have correct CORS headers if option set`() {
            val response = RootHandler(Options(
                cors = true,
                corsAllowedHeaders = listOf("a", "b", "c"),
                corsAllowedMethods = listOf("GET", "POST")
            ))(Request(Method.GET, "/"))
            expect("a, b, c") { response.header(CORS.HEADERS.header)?.toLowerCase() }
            expect("GET, POST") { response.header(CORS.METHODS.header) }
            expect("*") { response.header(CORS.ORIGINS.header) }
        }
        @Test
        fun `should have correct CORS origin if option set and origin matches`() {
            val response = RootHandler(Options(
                cors = true,
                corsAllowedOrigins = listOf("kloudformation.hexlabs.io", "hexlabs.io")
                ))(Request(Method.GET, "/").header("Origin", "hexlabs.io"))
            expect("hexlabs.io") { response.header(CORS.ORIGINS.header) }
        }
    }
    @Nested
    inner class HealthEndpoint {
        @Test
        fun `health endpoint is up by default on slash`() {
            val response = RootHandler(Options())(Request(Method.GET, "/"))
            expect(Status.OK) { response.status }
            expect("Healthy") { response.bodyString() }
        }
        @Test
        fun `should move health endpoint to health when option set`() {
            val handler = RootHandler(Options(healthPath = "/health"))
            val slashResponse = handler(Request(Method.GET, "/"))
            val response = handler(Request(Method.GET, "/health"))
            expect(Status.NOT_FOUND) { slashResponse.status }
            expect(Status.OK) { response.status }
            expect("Healthy") { response.bodyString() }
        }
        @Test
        fun `should set health body when option set`() {
            val response = RootHandler(Options(healthBody = "Hello"))(Request(Method.GET, "/"))
            expect("Hello") { response.bodyString() }
        }
        @Test
        fun `should set status code when option set`() {
            val response = RootHandler(Options(healthStatus = 204))(Request(Method.GET, "/"))
            expect(204) { response.status.code }
            expect("Healthy") { response.status.description }
        }
    }
}

private enum class CORS(val header: String) {
    HEADERS("Access-control-allow-headers"),
    METHODS("Access-control-allow-methods"),
    ORIGINS("Access-control-allow-origin")
}