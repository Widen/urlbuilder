# Gradle 9 Modernization Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Upgrade urlbuilder from Gradle 6.8.3 to 9.4.0, convert Groovy build scripts to Kotlin DSL, and modernize to current best practices while maintaining Java 8 bytecode compatibility.

**Architecture:** Sequential layer-by-layer migration with validation after each phase. Gradle runs on Java 21 (Corretto via mise), compiles to Java 8 bytecode via toolchain. Build scripts converted to Kotlin DSL with modern lazy task registration and updated plugins.

**Tech Stack:** Gradle 9.4.0, Kotlin DSL, Java 21 (runtime), Java 8 (target), mise, nexus-publish-plugin 2.0.0

---

### Task 1: Upgrade Gradle Wrapper to 9.4.0

**Files:**
- Modify: `gradle/wrapper/gradle-wrapper.properties:3`

**Step 1: Update wrapper properties**

Edit `gradle/wrapper/gradle-wrapper.properties` line 3:

```properties
distributionUrl=https\://services.gradle.org/distributions/gradle-9.4.0-bin.zip
```

**Step 2: Regenerate wrapper files**

Run: `./gradlew wrapper --gradle-version 9.4.0`

Expected: Downloads Gradle 9.4.0, updates wrapper JAR and scripts

**Step 3: Verify Gradle version**

Run: `./gradlew --version`

Expected output includes:
```
Gradle 9.4.0
```

**Step 4: Commit**

```bash
git add gradle/ gradlew gradlew.bat
git commit -m "build: upgrade Gradle wrapper to 9.4.0"
```

---

### Task 2: Configure Java 21 via mise

**Files:**
- Create: `.mise.toml`

**Step 1: Create mise configuration**

Create `.mise.toml`:

```toml
[tools]
java = "corretto-21"
```

**Step 2: Install Java via mise**

Run: `mise install`

Expected: Downloads and installs Amazon Corretto 21

**Step 3: Verify Java version**

Run: `java -version`

Expected output includes:
```
openjdk version "21.x.x" 
...
Corretto-21
```

**Step 4: Verify Gradle uses correct Java**

Run: `./gradlew --version`

Expected: Shows JVM line with Java 21

**Step 5: Commit**

```bash
git add .mise.toml
git commit -m "build: configure Java 21 (Corretto) via mise"
```

---

### Task 3: Convert settings.gradle to Kotlin DSL

**Files:**
- Delete: `settings.gradle`
- Create: `settings.gradle.kts`

**Step 1: Create Kotlin settings file**

Create `settings.gradle.kts`:

```kotlin
rootProject.name = "urlbuilder"
```

**Step 2: Remove Groovy settings file**

Run: `rm settings.gradle`

**Step 3: Verify Gradle still works**

Run: `./gradlew help`

Expected: Completes successfully (build.gradle will have warnings but should still parse)

**Step 4: Commit**

```bash
git add settings.gradle.kts
git commit -m "build: convert settings.gradle to Kotlin DSL"
```

Note: `settings.gradle` deletion tracked automatically by git

---

### Task 4: Convert build.gradle to Kotlin DSL

**Files:**
- Delete: `build.gradle`
- Create: `build.gradle.kts`

**Step 1: Create Kotlin build file**

Create `build.gradle.kts`:

```kotlin
plugins {
    `java-library`
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
    useInMemoryPgpKeys(findProperty("signingKey") as String?, findProperty("signingPassword") as String?)
    sign(publishing.publications["maven"])
}
```

**Step 2: Remove Groovy build file**

Run: `rm build.gradle`

**Step 3: Verify build compiles**

Run: `./gradlew compileJava`

Expected: BUILD SUCCESSFUL

**Step 4: Run full build with tests**

Run: `./gradlew build`

Expected: BUILD SUCCESSFUL (tests pass)

**Step 5: Commit**

```bash
git add build.gradle.kts
git commit -m "build: convert build.gradle to Kotlin DSL with modernizations

- Convert to Kotlin DSL syntax
- Use java toolchain targeting Java 8
- Use lazy task registration (tasks.registering)
- Replace deprecated classifier with archiveClassifier
- Update nexus-publish-plugin to 2.0.0
- Remove com.widen.versioning plugin (manual version)
- Replace groovy plugin with java-library"
```

---

### Task 5: Verify Java 8 Bytecode Compatibility

**Files:**
- None (verification only)

**Step 1: Build the project**

Run: `./gradlew clean build`

Expected: BUILD SUCCESSFUL

**Step 2: Check bytecode version**

Run: `javap -verbose build/classes/java/main/com/widen/urlbuilder/UrlBuilder.class | grep "major version"`

Expected output:
```
  major version: 52
```

(52 = Java 8)

**Step 3: Verify no deprecation warnings**

Run: `./gradlew build --warning-mode all 2>&1 | grep -i deprecat || echo "No deprecation warnings found"`

Expected: "No deprecation warnings found"

---

### Task 6: Verify Publishing Configuration

**Files:**
- None (verification only)

**Step 1: Publish to local Maven repository**

Run: `./gradlew publishToMavenLocal`

Expected: BUILD SUCCESSFUL

**Step 2: Verify published artifacts**

Run: `ls ~/.m2/repository/com/widen/urlbuilder/1.0.0/`

Expected: Shows JAR, sources JAR, javadoc JAR, and POM files

**Step 3: Commit design and plan docs**

```bash
git add docs/
git commit -m "docs: add Gradle 9 modernization design and implementation plan"
```

---

### Task 7: Final Cleanup

**Files:**
- Possibly: `README.md` (update Gradle badge/instructions if needed)

**Step 1: Check if README needs updates**

Review `README.md` for any Gradle-specific instructions that may need updating.

**Step 2: Update README if necessary**

If the README references old Gradle syntax (e.g., `compile` instead of `implementation`), update it.

Current README shows:
```
compile 'com.widen:urlbuilder:{version}'
```

Update to:
```
implementation("com.widen:urlbuilder:{version}")
```

**Step 3: Final commit if changes made**

```bash
git add README.md
git commit -m "docs: update README with modern Gradle dependency syntax"
```

---

## Summary

After completing all tasks:

- Gradle: 6.8.3 → 9.4.0
- Java runtime: 21 (Corretto via mise)
- Java target: 8 (via toolchain)
- Build scripts: Groovy → Kotlin DSL
- Plugins: nexus-publish 2.0.0, removed versioning plugin
- All tests passing
- Zero deprecation warnings
- Publishing verified
