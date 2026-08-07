plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(ktorLibs.plugins.ktor)
}

group = "com.aivashin"
version = "1.0.0-SNAPSHOT"

application {
    mainClass = "io.ktor.server.netty.EngineMain"
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(platform(libs.kotlinx.coroutines.bom))
    implementation(libs.kotlinx.coroutines.reactor)

    implementation(ktorLibs.server.config.yaml)
    implementation(ktorLibs.server.core)
    implementation(ktorLibs.server.netty)
    implementation(ktorLibs.server.contentNegotiation)
    implementation(ktorLibs.server.di)
    implementation(ktorLibs.serialization.kotlinx.json)
    implementation(libs.logback.classic)
    implementation(libs.kotlin.logging)
    implementation(libs.koog.agents)
    implementation(libs.koog.agents.additions)
    implementation(libs.koog.agents.features.sql)
    implementation(libs.koog.agents.features.chat.history.jdbc)
    implementation(libs.koog.agents.features.chat.memory.jdbc)
    implementation(libs.postgresql)
    implementation(libs.hikari)
    implementation(libs.postgresql.r2dbc)
    implementation(libs.r2dbc.pool)
    implementation(libs.opentelemetry.exporter.logging)
    implementation(libs.opentelemetry.exporter.otlp)

    testImplementation(kotlin("test"))
    testImplementation(ktorLibs.server.testHost)
    testImplementation(libs.mockk)
    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.testcontainers.junit.jupiter)
    testImplementation(libs.koog.agents.test)
    testImplementation(libs.h2)
    testImplementation(libs.r2dbc.h2)
}

tasks.test {
    useJUnitPlatform()
}
