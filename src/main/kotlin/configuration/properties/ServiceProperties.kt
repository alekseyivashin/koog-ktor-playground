package com.aivashin.configuration.properties

import kotlinx.serialization.Serializable

@Serializable
data class ServiceProperties(
    val name: String,
    val version: String,
)