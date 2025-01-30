plugins {
    id("java")
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "me.flame.universal"
version = "1.0.0"

repositories {
    mavenCentral()
}

dependencies {
    // hikaricp
    implementation("com.zaxxer:HikariCP:6.2.1")

    implementation(project(":core"))

    compileOnly("org.jetbrains:annotations:24.0.1")
}