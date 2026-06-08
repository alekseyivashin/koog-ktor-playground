package com.aivashin.properties

import kotlinx.serialization.Serializable

@Serializable
data class Agents(val api: Api)

@Serializable
data class Api(val geminiApiKey: String)