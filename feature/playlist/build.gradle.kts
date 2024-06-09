import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    alias(libs.plugins.com.android.library)
    alias(libs.plugins.org.jetbrains.kotlin.android)
    alias(libs.plugins.com.google.devtools.ksp)
    alias(libs.plugins.com.google.dagger.hilt.android)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "com.m3u.feature.playlist"
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
    packaging {
        resources.excludes += "META-INF/**"
    }
}

dependencies {
    implementation(project(":core"))
    implementation(project(":ui"))

    implementation(libs.androidx.core.ktx)

    // for m2 BackdropScaffold only
    implementation("androidx.compose.material:material")

    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)

    implementation(libs.google.dagger.hilt)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.hilt.work)

    ksp(libs.google.dagger.hilt.compiler)
    ksp(libs.androidx.hilt.compiler)

    implementation(libs.androidx.work.runtime.ktx)

    implementation(libs.androidx.tvprovider)
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions {
        freeCompilerArgs.addAll(
            "-opt-in=androidx.compose.material.ExperimentalMaterialApi"
        )
    }
}
