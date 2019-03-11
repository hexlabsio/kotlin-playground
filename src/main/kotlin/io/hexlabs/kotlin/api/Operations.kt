package io.hexlabs.kotlin.api

enum class Operations(val endpoint: String, val requiresBody: Boolean) {
    KOTLIN_VERSIONS("getKotlinVersions", false),
    COMPLETE("complete", true);
    companion object {
        fun from(endpoint: String, requiresBody: Boolean) = Operations.values().find { it.endpoint == endpoint && it.requiresBody == requiresBody }
    }
}