@file:Suppress("UnstableApiUsage")

import com.android.build.api.dsl.ManagedVirtualDevice

plugins {
    alias(libs.plugins.org.jetbrains.kotlin.android)
    alias(libs.plugins.androidx.baselineprofile)
    alias(libs.plugins.com.android.test)
}

android {
    namespace = "com.m3u.baselineprofile.smartphone"
    compileSdk = 35

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    defaultConfig {
        minSdk = 28
        targetSdk = 34

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    flavorDimensions += listOf("channel", "codec")
    productFlavors {
        create("stableChannel") { dimension = "channel" }
        create("snapshotChannel") { dimension = "channel" }
        create("richCodec") { dimension = "codec" }
        create("liteCodec") { dimension = "codec" }
    }

    testOptions.managedDevices.devices {
        create<ManagedVirtualDevice>("Pixel5Api31") {
            device = "Pixel 5"
            apiLevel = 34
            systemImageSource = "aosp"
        }
    }
    // Note that your module name may have different name
    targetProjectPath = ":app:smartphone"
}

// This is the configuration block for the Baseline Profile plugin.
// You can specify to run the generators on a managed devices or connected devices.
baselineProfile {
    managedDevices += "Pixel5Api31"
    useConnectedDevices = false
    enableEmulatorDisplay = true
}

androidComponents {
    onVariants { v ->
        val isSnapshot = "snapshot" in v.name

        v.instrumentationRunnerArguments.put(
            "targetAppId",
            if (isSnapshot) "com.m3u.smartphone.snapshot"
            else "com.m3u.smartphone"
        )
    }
}

dependencies {
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.androidx.test.espresso.espresso.core)
    implementation(libs.androidx.test.uiautomator.uiautomator)
    implementation(libs.androidx.benchmark.benchmark.macro.junit4)
}