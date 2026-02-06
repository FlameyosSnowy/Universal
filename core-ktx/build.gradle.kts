plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "1.9.10"
}

group = "me.flame.universal.api.kotlin"
version = "6.1.6"

repositories {
    mavenCentral()
}

dependencies {
    // JUnit
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    // Kotlin compileOnly
    compileOnly("org.jetbrains.kotlin:kotlin-stdlib:1.9.10")
    compileOnly(project(":core"))

    compileOnly("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
}

tasks.test {
    useJUnitPlatform()
}