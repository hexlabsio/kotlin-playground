package io.hexlabs.kotlin.playground

enum class Operations(val endpoint: String, val requiresBody: Boolean) {
    KOTLIN_VERSIONS("getKotlinVersions", false);
    companion object {
        fun from(endpoint: String, requiresBody: Boolean) = Operations.values().find { it.endpoint == endpoint && it.requiresBody == requiresBody }
    }
}