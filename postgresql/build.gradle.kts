plugins {
    id("java")
    id("com.gradleup.shadow") version("9.0.0-beta12")
}

group = "io.github.flameyossnowy.universal"
version = "6.1.6"

repositories {
    mavenCentral()
    maven("https://jitpack.io")
}

dependencies {
    // hikaricp
    implementation("com.zaxxer:HikariCP:6.2.1")

    compileOnly(project(":core"))
    compileOnly(project(":sql-common"))

    compileOnly("org.jetbrains:annotations:24.0.1")

    // postgresql
    compileOnly("org.postgresql:postgresql:42.7.2")

    testImplementation("org.postgresql:postgresql:42.7.2")
    testImplementation(project(":core"))
    testImplementation(project(":sql-common"))

    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}

tasks.test {
    useJUnitPlatform()
}