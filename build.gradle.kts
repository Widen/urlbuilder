plugins {
    `java-library`
    `maven-publish`
    signing
    alias(libs.plugins.nexus.publish)
}

group = "com.widen"
version = "3.0.0-SNAPSHOT"
description = "Utility class for constructing syntactically correct HTTP URLs using a fluent method-chaining API"

val repoUrl = "https://github.com/Widen/urlbuilder"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(8))
    }
    withSourcesJar()
    withJavadocJar()
}

dependencies {
    implementation(libs.bundles.bouncycastle)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)

    testImplementation(libs.aws.java.sdk.s3)
    testImplementation(libs.commons.io)
    testImplementation(libs.slf4j.simple)
}

// The Java 8 compiler and javadoc read sources in the machine's default encoding unless told otherwise.
tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

tasks.javadoc {
    (options as StandardJavadocDocletOptions).apply {
        encoding = "UTF-8"
        docEncoding = "UTF-8"
        charSet = "UTF-8"
    }
}

tasks.jar {
    manifest {
        attributes("Automatic-Module-Name" to "com.widen.urlbuilder")
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        showExceptions = true
        showCauses = true
        showStackTraces = true
    }
}

// Tests tagged "live" call S3 and run only from liveTest.
tasks.test {
    useJUnitPlatform { excludeTags("live") }
}

// The library targets Java 8, but most consumers run it on a newer JVM, where JDK classes such as
// java.util.regex.Pattern behave differently.
val testOnJava21 = tasks.register<Test>("testOnJava21") {
    description = "Runs the tests on Java 21."
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    testClassesDirs = files(sourceSets.test.map { it.output.classesDirs })
    classpath = files(sourceSets.test.map { it.runtimeClasspath })
    javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(21)) })
    useJUnitPlatform { excludeTags("live") }
}

tasks.register<Test>("liveTest") {
    description = "Runs the tests that call S3; needs AWS credentials in the environment."
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    testClassesDirs = files(sourceSets.test.map { it.output.classesDirs })
    classpath = files(sourceSets.test.map { it.runtimeClasspath })
    useJUnitPlatform { includeTags("live") }
    // The result depends on S3, which Gradle cannot see as an input.
    outputs.upToDateWhen { false }
}

tasks.check {
    dependsOn(testOnJava21)
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
