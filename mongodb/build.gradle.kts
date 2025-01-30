plugins {
    id("java")
}

group = "me.flame.universal.mongodb"
version = "1.0.0"

repositories {
    mavenCentral()
}

dependencies {
    compileOnly("org.mongodb:mongodb-driver-sync:5.3.0")
    compileOnly("org.jetbrains:annotations:24.0.1")

    implementation(project(":core"))
    implementation("me.sunlan:fast-reflection:1.0.3")
}