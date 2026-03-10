plugins {
    `java-library`
    `maven-publish`
    signing
    id("io.github.gradle-nexus.publish-plugin") version "2.0.0"
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
    implementation("org.bouncycastle:bcprov-jdk15to18:1.80")
    implementation("org.bouncycastle:bcpkix-jdk15to18:1.80")

    // JUnit 5
    testImplementation(platform("org.junit:junit-bom:5.13.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    // Other test dependencies
    testImplementation("commons-io:commons-io:2.4")
    testImplementation("org.slf4j:slf4j-simple:1.7.25")
}

tasks.test {
    useJUnitPlatform()
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
    isRequired = signingKey != null && signingPassword != null
}
