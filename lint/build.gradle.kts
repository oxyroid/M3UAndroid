plugins {
    `java-library`
    id("kotlin")
    id("com.android.lint")
}
dependencies {
    compileOnly("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    compileOnly(libs.com.android.tools.lint.lint.api)
}