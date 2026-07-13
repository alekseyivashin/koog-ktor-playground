package com.aivashin.model.graph

import kotlinx.serialization.Serializable

@Serializable
data class OptimizerState(
    val sessionId: String,
    val userQuery: String,
    val tableNamesInQuery: List<String> = emptyList(),
    val isWebSearchNeeded: Boolean = false,
    val extractedSchema: String = "",
    val webResearchData: String = "",
    val generatedRawSql: String = "",
    val explanation: String = "",
    val isSafe: Boolean = true,
    val validationErrors: List<String> = emptyList(),
    val systemErrors: List<String> = emptyList(),
    val iterationCount: Int = 0
)