plugins {
    id("java")
    id("me.champeau.jmh") version "0.7.2"
    id("com.gradleup.shadow") version("9.3.1")
}

group = "io.github.flameyossnowy.universal"
version = "7.0.0"

repositories {
    mavenCentral()
    maven("https://jitpack.io")
}

dependencies {
    // hikaricp
    compileOnly("com.zaxxer:HikariCP:6.2.1")

    compileOnly(project(":core"))
    compileOnly(project(":sql-common"))

    compileOnly("org.jetbrains:annotations:24.0.1")

    // postgresql
    compileOnly("org.postgresql:postgresql:42.7.2")

    jmh("org.openjdk.jmh:jmh-core:1.37")
    jmhAnnotationProcessor("org.openjdk.jmh:jmh-generator-annprocess:1.35")

    testImplementation("org.postgresql:postgresql:42.7.2")
    testImplementation(project(":core"))
    testImplementation(project(":sql-common"))
    testAnnotationProcessor(project(":compile-time-checker"))

    testImplementation("org.mockito:mockito-core:5.11.0")
    testImplementation("org.mockito:mockito-junit-jupiter:5.11.0")

    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.junit.platform:junit-platform-launcher")
}

sourceSets {
    named("jmh") {
        java {
            setSrcDirs(listOf("src/jmh/java"))
        }
        compileClasspath += sourceSets.main.get().output
        runtimeClasspath += sourceSets.main.get().output
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}