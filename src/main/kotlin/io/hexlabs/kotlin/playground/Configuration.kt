package io.hexlabs.kotlin.playground

import java.io.File

data class Configuration(
    val kotlinVersion: String,
    val libs: File? = null,
    val disableSecurity: Boolean = false
)