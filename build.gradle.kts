plugins {
    `java-library`
    signing
    id("com.vanniktech.maven.publish") version "0.33.0"
    kotlin("jvm") version "1.9.23" apply false
}

group = "io.github.flameyossnowy"
version = "7.1.2"

allprojects {
    tasks.withType<JavaCompile> {
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

    tasks.withType<JavaCompile> {
        options.encoding = "UTF-8"
    }

    java {
        toolchain.languageVersion.set(JavaLanguageVersion.of(21))
        withSourcesJar()
        withJavadocJar()
    }

    tasks.withType<JavaExec>().configureEach {
        jvmArgs("--add-modules=jdk.incubator.vector")
    }

    apply(plugin = "com.vanniktech.maven.publish")

    configure<com.vanniktech.maven.publish.MavenPublishBaseExtension> {
        coordinates(group as String, "universal-${name}", version as String)

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

        configure<com.vanniktech.maven.publish.MavenPublishBaseExtension> {
            publishToMavenCentral()
            signAllPublications()
        }

        tasks.withType<JavaCompile> {
            options.encoding = "UTF-8"
            options.compilerArgs.add("-parameters")
        }
    }

    signing {
        useGpgCmd()
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
        jvmArgs("--add-modules", "jdk.incubator.vector")
    }

    afterEvaluate {
        tasks.named<com.vanniktech.maven.publish.tasks.JavadocJar>("plainJavadocJar") {
            dependsOn(tasks.named("javadoc"))
            archiveClassifier.set("javadoc")
            from(tasks.named<Javadoc>("javadoc"))
        }

        // Ensure metadata generation depends on Javadoc
        tasks.named("generateMetadataFileForMavenPublication") {
            dependsOn(tasks.named("plainJavadocJar"))
        }

        // Make publish depend on the Javadoc artifact
        tasks.named("publish") {
            dependsOn(tasks.named("plainJavadocJar"))
        }
    }
}