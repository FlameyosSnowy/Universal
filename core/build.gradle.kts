plugins {
    id("java")
    id("maven-publish")
    id("signing")
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "io.github.flameyossnowy.universal"
version = "1.0.0"

repositories {
    mavenCentral()
    maven("https://repo.kenzie.mx/releases")
    maven("https://repo.foxikle.dev/flameyos")
}

dependencies {
    compileOnly("org.jetbrains:annotations:24.0.1")

    compileOnly("me.sunlan:fast-reflection:1.0.3")
}

/*
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
