plugins {
    alias(libs.plugins.com.android.library)
    alias(libs.plugins.org.jetbrains.kotlin.android)
}

android {
    namespace = "com.m3u.dlna"
    compileSdk = 34

    defaultConfig {
        minSdk = 26

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    packaging {
        resources.excludes += setOf(
            "/META-INF/beans.xml",
            "/META-INF/{AL2.0,LGPL2.1}"
        )
    }
}

dependencies {
    // Servlet
    api(libs.servlet.api)
    // Jetty
    api(libs.jetty.server)
    api(libs.jetty.servlet)
    api(libs.jetty.client)
    // Nano http
    api(libs.nanohttpd)

    api(libs.jupnp)
    api(libs.jupnp.support)
}