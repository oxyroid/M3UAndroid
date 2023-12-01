package com.m3u.features.about.model

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember

@JvmInline
@Immutable
internal value class Dependency(val name: String)

@Immutable
internal data class DependencyHolder(
    val dependencies: List<Dependency>
)

@Composable
internal fun rememberDependencyHolder(dependencies: List<Dependency>): DependencyHolder {
    return remember(dependencies) {
        DependencyHolder(dependencies)
    }
}
