package com.aivashin.configuration.telemetry

import ai.koog.agents.features.opentelemetry.feature.OpenTelemetryConfig
import com.aivashin.configuration.properties.ServiceProperties
import io.ktor.server.plugins.di.annotations.Property
import io.opentelemetry.exporter.logging.LoggingSpanExporter
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter
import kotlinx.serialization.Serializable


@Serializable
data class TelemetryProperties(
    val verbose: Boolean = false,
    val console: ConsoleTelemetry,
    val jaeger: JaegerTelemetry,
) {

    @Serializable
    data class ConsoleTelemetry(
        val enabled: Boolean = true,
    )

    @Serializable
    data class JaegerTelemetry(
        val enabled: Boolean = false,
        val endpoint: String = "",
    )
}

class TelemetryConfig(
    @Property("agents.telemetry") private val telemetryProperties: TelemetryProperties,
    private val serviceProperties: ServiceProperties
) {
    operator fun invoke(config: OpenTelemetryConfig) {
        config.setServiceInfo(serviceProperties.name, serviceProperties.version)
        config.setVerbose(telemetryProperties.verbose)

        if (telemetryProperties.console.enabled) {
            config.addSpanExporter(LoggingSpanExporter.create())
        }

        if (telemetryProperties.jaeger.enabled) {
            config.addSpanExporter(
                OtlpGrpcSpanExporter.builder()
                    .setEndpoint(telemetryProperties.jaeger.endpoint)
                    .build()
            )
        }
    }
}