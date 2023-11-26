plugins {
    alias(libs.plugins.com.android.application)
    alias(libs.plugins.org.jetbrains.kotlin.android)
    alias(libs.plugins.com.google.dagger.hilt.android)
    alias(libs.plugins.com.google.devtools.ksp)
    alias(libs.plugins.androidx.baselineprofile)
    id("io.gitlab.arturbosch.detekt")
}

android {
    namespace = "com.m3u.androidApp"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.m3u.androidApp"
        minSdk = 26
        targetSdk = 33
        versionCode = 78
        versionName = "1.13.0-beta01"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        testInstrumentationRunnerArguments["androidx.benchmark.profiling.mode"] = "MethodTracing"
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        create("benchmark") {
            initWith(buildTypes.getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
            isDebuggable = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = libs.versions.compose.compiler.get()
    }

    packaging {
        resources.excludes += "META-INF/**"
    }
}

hilt {
    enableAggregatingTask = true
}

dependencies {
    implementation(project(":core"))
    implementation(project(":data"))
    implementation(project(":dlna"))
    implementation(project(":ui"))
    implementation(project(":features:main"))
    implementation(project(":features:favorite"))
    implementation(project(":features:setting"))
    implementation(project(":features:feed"))
    implementation(project(":features:live"))
    implementation(project(":features:console"))
    implementation(project(":features:crash"))
    implementation(project(":features:about"))

    implementation(libs.androidx.core.core.ktx)
    implementation(libs.androidx.appcompat.appcompat)
    implementation(libs.androidx.activity.activity.compose)

    implementation(libs.androidx.lifecycle.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.lifecycle.runtime.compose)

    implementation(libs.androidx.core.core.splashscreen)

    implementation(libs.com.google.dagger.hilt.android)
    ksp(libs.com.google.dagger.hilt.compiler)
    implementation(libs.androidx.hilt.hilt.navigation.compose)

    implementation(libs.androidx.work.work.runtime.ktx)
    ksp(libs.androidx.hilt.hilt.compiler)
    implementation(libs.androidx.hilt.hilt.work)

    debugImplementation(libs.com.squareup.leakcanary.leakcanary.android)

    androidTestImplementation(libs.androidx.compose.ui.ui.test.junit4)
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    val path = project.buildDir.absolutePath + "/compose_metrics"
    compilerOptions.freeCompilerArgs.addAll(
        "-P",
        "plugin:androidx.compose.compiler.plugins.kotlin:metricsDestination=$path",
    )
    compilerOptions.freeCompilerArgs.addAll(
        "-P",
        "plugin:androidx.compose.compiler.plugins.kotlin:reportsDestination=$path",
    )
}

detekt {
    config.setFrom("detekt.yml")
    allRules = true
}