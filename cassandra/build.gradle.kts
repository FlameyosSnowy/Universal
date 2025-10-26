plugins {
    id("java")
    id("com.gradleup.shadow") version("9.0.0-beta12")
}

group = "io.github.flameyossnow.universal.cassandra"
version = "4.0.0"

repositories {
    mavenCentral()
    maven("https://jitpack.io")
}

dependencies {
    implementation("org.apache.cassandra:cassandra-all:5.0.5")
    compileOnly("org.jetbrains:annotations:24.0.1")

    compileOnly(project(":core"))

    testImplementation(project(":core"))
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}