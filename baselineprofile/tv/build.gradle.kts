@file:Suppress("UnstableApiUsage")

import com.android.build.api.dsl.ManagedVirtualDevice

plugins {
    alias(libs.plugins.org.jetbrains.kotlin.android)
    alias(libs.plugins.androidx.baselineprofile)
    alias(libs.plugins.com.android.test)
}

android {
    namespace = "com.m3u.baselineprofile.tv"
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

    testOptions.managedDevices.devices {
        create<ManagedVirtualDevice>("tvApi34") {
            device = "Television (1080p)"
            apiLevel = 34
            systemImageSource = "aosp"
        }
    }
    // Note that your module name may have different name
    targetProjectPath = ":app:tv"
}

// This is the configuration block for the Baseline Profile plugin.
// You can specify to run the generators on a managed devices or connected devices.
baselineProfile {
    managedDevices += "tvApi34"
    useConnectedDevices = false
    enableEmulatorDisplay = true
}

androidComponents {
    onVariants { v ->
        v.instrumentationRunnerArguments.put(
            "targetAppId",
            "com.m3u.tv"
        )
    }
}

dependencies {
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.androidx.test.espresso.espresso.core)
    implementation(libs.androidx.test.uiautomator.uiautomator)
    implementation(libs.androidx.benchmark.benchmark.macro.junit4)
}