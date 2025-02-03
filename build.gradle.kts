plugins {
    id("java")
    id("maven-publish")
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "me.flame.universal"
version = "1.0.0"

repositories {
    mavenCentral()
}

dependencies {
    // HikariCP
    implementation("com.zaxxer:HikariCP:6.2.1")

    implementation(project(":core"))

    compileOnly("org.jetbrains:annotations:24.0.1")
}

subprojects {
    apply(plugin = "java")
    apply(plugin = "maven-publish")
    apply(plugin = "signing")
    apply(plugin = "com.github.johnrengelman.shadow")

    val sourcesJar = tasks.register<Jar>("sourcesJar") {
        archiveClassifier.set("sources")
        from(sourceSets.main.get().allJava)
    }

    publishing {
        publications {
            create<MavenPublication>("maven") {
                groupId = project.group.toString()
                artifactId = project.name
                version = project.version.toString()
                artifact(tasks["shadowJar"])
                artifact(sourcesJar)

                pom {
                    name.set("Universal")
                    description.set("Universal, Very lightweight and fast ORM/ODM for databases such as MySQL, SQLite, and MongoDB.")
                    url.set("https://github.com/FlameyosSnowy/Universal")
                    licenses {
                        license {
                            name.set("The MIT License")
                            url.set("https://mit-license.org/")
                        }
                    }
                    developers {
                        developer {
                            id.set("flameyosflow")
                            name.set("FlameyosFlow")
                            email.set("flamesfflowsnow@gmail.com")
                        }
                    }
                    scm {
                        connection.set("scm:git:https://github.com/FlameyosSnowy/Universal.git")
                        developerConnection.set("scm:git:https://github.com/FlameyosSnowy/Universal.git")
                        url.set("https://github.com/FlameyosSnowy/Universal")
                    }
                }
            }
        }
    }
}