import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

buildscript {
    dependencies {
        classpath(libs.org.jetbrains.kotlin.kotlin.serialization)
    }
}

plugins {
    alias(libs.plugins.com.android.application) apply false
    alias(libs.plugins.com.android.library) apply false
    alias(libs.plugins.org.jetbrains.kotlin.android) apply false
    alias(libs.plugins.com.google.dagger.hilt.android) apply false
    alias(libs.plugins.com.google.devtools.ksp) apply false
    alias(libs.plugins.com.android.test) apply false
    id("io.gitlab.arturbosch.detekt").version("1.23.1")
}
subprojects {
    apply(plugin = "io.gitlab.arturbosch.detekt")

    detekt {
        source.setFrom(
            "src/main/java"
        )
        config.setFrom("$rootDir/config/detekt.yml")
        buildUponDefaultConfig = true
    }

    dependencies {
        detektPlugins("com.twitter.compose.rules:detekt:0.0.26")
    }
    tasks.withType<KotlinCompile>().configureEach {
        kotlinOptions {
            freeCompilerArgs += "-Xcontext-receivers"
        }
    }
}
