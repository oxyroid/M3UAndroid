plugins {
    alias(libs.plugins.com.android.application)
    alias(libs.plugins.org.jetbrains.kotlin.android)
    alias(libs.plugins.com.google.dagger.hilt.android)
    alias(libs.plugins.com.google.devtools.ksp)
    alias(libs.plugins.androidx.baselineprofile)
}

android {
    namespace = "com.m3u.androidApp"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.m3u.androidApp"
        minSdk = 26
        targetSdk = 33
        versionCode = 67
        versionName = "1.12.1"

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
}

hilt {
    enableAggregatingTask = true
}

dependencies {
    implementation(project(":core"))
    implementation(project(":data"))
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
}