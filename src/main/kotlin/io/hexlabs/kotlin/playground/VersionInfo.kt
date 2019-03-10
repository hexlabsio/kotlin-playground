package io.hexlabs.kotlin.playground

data class VersionInfo(
    val version: String,
    val build: String,
    val stdlibVersion: String,
    val latestStable: Boolean = true,
    val obsolete: Boolean = false,
    val hasScriptJar: Boolean = false
)