package io.hexlabs.kotlin.playground

import java.io.File

data class Configuration(
    val libs: File? = null,
    val disableSecurity: Boolean = false
)