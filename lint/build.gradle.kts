plugins {
    `java-library`
    id("kotlin")
    id("com.android.lint")
}
dependencies {
    compileOnly("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    compileOnly("com.android.tools.lint:lint:31.1.2")
}