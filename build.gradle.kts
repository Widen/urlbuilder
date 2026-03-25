plugins {
    `java-library`
    `maven-publish`
    signing
    alias(libs.plugins.nexus.publish)
}

group = "com.widen"
version = "3.0.0-SNAPSHOT"
description = "Utility class for constructing syntactically correct HTTP URLs using a fluent method-chaining API"

val repoUrl = "https://github.com/Widen/${project.name}"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(8))
    }
    withSourcesJar()
    withJavadocJar()
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.bouncycastle.bcprov)
    implementation(libs.bouncycastle.bcpkix)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)

    testImplementation(libs.commons.io)
    testImplementation(libs.slf4j.simple)
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("started", "passed", "skipped", "failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        showExceptions = true
        showCauses = true
        showStackTraces = true
    }
}

nexusPublishing {
    repositories {
        sonatype {
            nexusUrl.set(uri("https://ossrh-staging-api.central.sonatype.com/service/local/"))
            snapshotRepositoryUrl.set(uri("https://central.sonatype.com/repository/maven-snapshots/"))
        }
    }
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            pom {
                name.set(project.name)
                description.set(project.description)
                url.set(repoUrl)
                licenses {
                    license {
                        name.set("The Apache Software License, Version 2.0")
                        url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                        distribution.set("repo")
                    }
                }
                organization {
                    name.set("Widen Enterprises, Inc")
                    url.set("https://www.widen.com")
                }
                developers {
                    developer {
                        id.set("uriahcarpenter")
                        name.set("Uriah Carpenter")
                        email.set("uriah@widen.com")
                    }
                    developer {
                        id.set("scoakley")
                        name.set("Stephen Coakley")
                        email.set("scoakley@widen.com")
                    }
                }
                scm {
                    url.set("$repoUrl.git")
                    connection.set(repoUrl.replace("https://github.com/", "scm:git@github.com:") + ".git")
                    developerConnection.set(repoUrl.replace("https://github.com/", "scm:git@github.com:") + ".git")
                }
            }
        }
    }
}

signing {
    val signingKey = findProperty("signingKey") as String?
    val signingPassword = findProperty("signingPassword") as String?
    useInMemoryPgpKeys(signingKey, signingPassword)
    sign(publishing.publications["maven"])
    isRequired = signingKey != null
}
