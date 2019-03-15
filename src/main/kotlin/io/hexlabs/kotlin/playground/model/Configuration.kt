package io.hexlabs.kotlin.playground.model

import java.io.File

data class Configuration(
    val kotlinVersion: String,
    val libs: File? = null,
    val disableSecurity: Boolean = false,
    val workingDirectory: String = System.getProperty("user.dir")
)