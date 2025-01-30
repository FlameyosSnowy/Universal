plugins {
    id("java")
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "me.flame.universal.api"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven("https://repo.kenzie.mx/releases")
    maven("https://repo.foxikle.dev/flameyos")
}

dependencies {
    compileOnly("org.jetbrains:annotations:24.0.1")

    compileOnly("me.sunlan:fast-reflection:1.0.3")
}