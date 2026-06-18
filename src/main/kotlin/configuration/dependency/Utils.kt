package com.aivashin.configuration.dependency

import io.ktor.server.plugins.di.DependencyKey
import io.ktor.server.plugins.di.DependencyResolver
import io.ktor.server.plugins.di.PropertyQualifier

suspend inline fun <reified T> DependencyResolver.resolveProperty(key: String): T {
    return get(DependencyKey<T>(key, PropertyQualifier))
}