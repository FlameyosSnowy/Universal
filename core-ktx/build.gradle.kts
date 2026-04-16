plugins {
    id("java")
    id("java-library")
    id("org.jetbrains.kotlin.jvm") version "2.3.20"
}

kotlin {
    jvmToolchain(21)
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

dependencies {
    // JUnit
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    // Kotlin compileOnly
    compileOnly("org.jetbrains.kotlin:kotlin-stdlib:1.9.23")
    compileOnly(project(":core"))

    compileOnly("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
}