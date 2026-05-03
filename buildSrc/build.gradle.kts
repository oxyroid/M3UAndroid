plugins {
    `kotlin-dsl`
}

dependencies {
    implementation("com.android.tools.build:gradle-api:${libs.versions.android.gradle.plugin.get()}")
    implementation(libs.snakeyaml)
}