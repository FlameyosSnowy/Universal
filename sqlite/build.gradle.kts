plugins {
    id("java")
    id("maven-publish")
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "io.github.flameyossnowy.universal"
version = "1.0.0"

repositories {
    mavenCentral()
    maven("https://repo.foxikle.dev/flameyos")
}

dependencies {
    compileOnly("com.zaxxer:HikariCP:6.2.1")
    compileOnly("org.jetbrains:annotations:24.0.1")

    compileOnly(project(":core"))
    implementation("me.sunlan:fast-reflection:1.0.3")
}

/*val sourcesJar = tasks.register<Jar>("sourcesJar") {
    archiveClassifier.set("sources")
    from(sourceSets.main.get().allJava)
}

publishing {
    repositories {
        maven {
            name = "mainRepository"
            url = uri("https://repo.foxikle.dev/flameyos")
            credentials(PasswordCredentials::class)
            authentication {
                create<BasicAuthentication>("basic")
            }
        }
    }
    publications {
        create<MavenPublication>("maven") {
            groupId = project.group.toString()
            artifactId = project.name
            version = project.version.toString()
            artifact(tasks["shadowJar"])
            //artifact(javadocJar)
            artifact(sourcesJar)
        }
    }
}*/
