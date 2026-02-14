plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "1.9.23"
}

group = "me.flame.universal.api.kotlin"
version = "7.0.0"

repositories {
    mavenCentral()
    maven {
        name = "jitpack"
        url = uri("https://jitpack.io")
    }
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

kotlin {
    jvmToolchain(21)
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

tasks.test {
    useJUnitPlatform()
}