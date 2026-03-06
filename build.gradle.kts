plugins {
    `java-library`
    groovy
    `maven-publish`
    signing
    id("io.github.gradle-nexus.publish-plugin") version "2.0.0"
}

group = "com.widen"
version = "1.0.0" // Set version manually (was using versioning plugin)
description = "Utility class for constructing syntactically correct HTTP URLs using a fluent method-chaining API"

val repoUrl = "https://github.com/Widen/${project.name}"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(8))
    }
}

repositories {
    mavenCentral()
}

dependencies {
    compileOnly("org.projectlombok:lombok:1.18.2")
    annotationProcessor("org.projectlombok:lombok:1.18.2")

    implementation("org.bouncycastle:bcprov-jdk15to18:1.80")
    implementation("org.bouncycastle:bcpkix-jdk15to18:1.80")

    testImplementation("cglib:cglib-nodep:3.2.5")
    testImplementation("commons-io:commons-io:2.4")
    testImplementation("junit:junit:4.8.2")
    testImplementation("org.codehaus.groovy:groovy-all:2.4.9")
    testImplementation("org.objenesis:objenesis:2.6")
    testImplementation("org.slf4j:slf4j-simple:1.7.25")
    testImplementation("org.spockframework:spock-core:1.1-groovy-2.4")
}

val sourcesJar by tasks.registering(Jar::class) {
    from(sourceSets.main.get().allSource)
    archiveClassifier.set("sources")
}

val javadocJar by tasks.registering(Jar::class) {
    from(tasks.javadoc)
    archiveClassifier.set("javadoc")
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
            artifact(sourcesJar)
            artifact(javadocJar)
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
