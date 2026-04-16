plugins {
    `java-library`
    signing
    id("com.vanniktech.maven.publish") version "0.33.0"
    kotlin("jvm") version "2.3.20" apply false
}

group = "io.github.flameyossnowy"
version = "7.1.5"

allprojects {
    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
    }
}

subprojects {
    apply(plugin = "java-library")

    repositories {
        mavenCentral()
    }

    group = rootProject.group
    version = rootProject.version

    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
    }

    java {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(25))
        }

        tasks.withType<JavaCompile>().configureEach {
            options.release.set(25)
        }
        withSourcesJar()
    }

    tasks.withType<JavaExec>().configureEach {
        jvmArgs("--add-modules=jdk.incubator.vector")
    }

    apply(plugin = "com.vanniktech.maven.publish")

    configure<com.vanniktech.maven.publish.MavenPublishBaseExtension> {
        coordinates(group as String, "universal-${name}", version as String)

        publishToMavenCentral()
        signAllPublications()

        pom {
            name.set("Universal")
            description.set("Universal, Performant Distrubuted Object Runtime for databases such as MySQL, SQLite and MongoDB and microservices for Files and Networks.")
            inceptionYear.set("2025")
            url.set("https://github.com/FlameyosSnowy/Universal/")
            licenses {
                license {
                    name.set("MIT")
                    url.set("https://opensource.org/licenses/MIT")
                    distribution.set("https://mit-license.org/")
                }
            }
            developers {
                developer {
                    id.set("flameyosflow")
                    name.set("FlameyosFlow")
                    url.set("https://github.com/FlameyosSnowy/")
                }
            }
            scm {
                url.set("https://github.com/FlameyosSnowy/Universal/")
                connection.set("scm:git:git://github.com/FlameyosSnowy/Universal.git")
                developerConnection.set("scm:git:ssh://git@github.com/FlameyosSnowy/Universal.git")
            }
        }
    }

    signing {
        useGpgCmd()
    }

    afterEvaluate {
        tasks.matching { it.name.contains("generateMetadataFileFor") }.configureEach {
            dependsOn(tasks.matching { it.name == "plainJavadocJar" })
        }
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
        jvmArgs("--add-modules", "jdk.incubator.vector")
    }

    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.compilerArgs.add("-parameters")
    }
}