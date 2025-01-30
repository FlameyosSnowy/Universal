plugins {
    id("java")
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "me.flame.universal.sqlite"
version = "1.0.0"

repositories {
    mavenCentral()
    maven("https://repo.foxikle.dev/flameyos")
}

dependencies {
    compileOnly("com.zaxxer:HikariCP:6.2.1")
    compileOnly("org.jetbrains:annotations:24.0.1")

    implementation(project(":core"))
    implementation("me.sunlan:fast-reflection:1.0.3")
}