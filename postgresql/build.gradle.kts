plugins {
    id("java")
    id("java-library")
    id("me.champeau.jmh") version "0.7.2"
    id("com.gradleup.shadow") version("9.3.1")
}

dependencies {
    compileOnly("com.zaxxer:HikariCP:6.2.1")

    compileOnly(project(":core"))
    compileOnly(project(":sql-common"))

    compileOnly("org.jetbrains:annotations:24.0.1")

    compileOnly("org.postgresql:postgresql:42.7.2")
    compileOnly("io.github.flameyossnowy:uniform-json:1.5.9")
    testCompileOnly("io.github.flameyossnowy:uniform-json:1.5.9")

    testImplementation("org.postgresql:postgresql:42.7.2")
    testImplementation(project(":core"))
    testImplementation(project(":sql-common"))
    testAnnotationProcessor(project(":compile-time-checker"))

    testImplementation("org.mockito:mockito-core:5.11.0")
    testImplementation("org.mockito:mockito-junit-jupiter:5.11.0")

    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.junit.platform:junit-platform-launcher")

    jmh(project(":core"))
    jmh(project(":sql-common"))
    jmhAnnotationProcessor(project(":compile-time-checker"))
    jmh("com.zaxxer:HikariCP:6.2.1")
    jmh("org.postgresql:postgresql:42.7.2")
    jmh("org.jetbrains:annotations:24.0.1")

    jmh("org.hibernate.orm:hibernate-core:6.6.3.Final")
    jmh("jakarta.persistence:jakarta.persistence-api:3.2.0")

    jmh("org.jooq:jooq:3.19.9")

    jmh("org.openjdk.jmh:jmh-core:1.37")
    jmhAnnotationProcessor("org.openjdk.jmh:jmh-generator-annprocess:1.37")

    // Silence Hibernate's logging during benchmarks
    jmh("org.slf4j:slf4j-nop:2.0.12")
}

sourceSets {
    named("jmh") {
        java { setSrcDirs(listOf("src/jmh/java")) }
        compileClasspath += sourceSets.main.get().output
        runtimeClasspath += sourceSets.main.get().output
    }
}

jmh {
    warmupIterations  = 3
    iterations        = 5
    fork              = 1
    timeUnit          = "ms"
    benchmarkMode     = listOf("thrpt", "avgt")
    resultsFile       = project.file("${project.buildDir}/reports/jmh/results.json")
    resultFormat      = "JSON"
    jmhVersion        = "1.37"
    jvmArgsAppend     = listOf(
        "-Dbenchmark.host=${project.findProperty("benchmark.host") ?: "localhost"}",
        "-Dbenchmark.port=${project.findProperty("benchmark.port") ?: "5432"}",
        "-Dbenchmark.db=${project.findProperty("benchmark.db")     ?: "test"}",
        "-Dbenchmark.user=${project.findProperty("benchmark.user") ?: "postgres"}",
        "-Dbenchmark.password=${project.findProperty("benchmark.password") ?: "secret"}"
    )
}

// Find where jmhAnnotationProcessor puts its generated files and add them to runtime
val jmhApGenDir = layout.buildDirectory.dir("generated/sources/annotationProcessor/java/jmh")

sourceSets {
    named("jmh") {
        java { setSrcDirs(listOf("src/jmh/java")) }
        compileClasspath += sourceSets.main.get().output
        runtimeClasspath += sourceSets.main.get().output
    }
}

tasks.named("processJmhResources") {
    dependsOn("compileJmhJava")
}

tasks.shadowJar {
    mergeServiceFiles()
}

tasks.withType<ProcessResources>().configureEach {
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
}