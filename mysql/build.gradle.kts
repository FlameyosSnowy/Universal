plugins {
    id("java")
}

group = "me.flame.universal"
version = "1.0.0"

repositories {
    mavenCentral()
}

dependencies {
    compileOnly("com.zaxxer:HikariCP:6.2.1")
    compileOnly("org.jetbrains:annotations:24.0.1")

    implementation(project(":core"))
    implementation("me.sunlan:fast-reflection:1.0.3")
}