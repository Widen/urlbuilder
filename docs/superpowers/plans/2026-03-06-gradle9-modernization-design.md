# Gradle 9 Modernization Design

**Date:** 2026-03-06  
**Project:** urlbuilder  
**Current State:** Gradle 6.8.3, Groovy DSL, Java 8 target

## Overview

Upgrade urlbuilder to Gradle 9.4.0, convert build scripts from Groovy to Kotlin DSL, and modernize to current best practices while maintaining Java 8 bytecode compatibility for consumers.

## Migration Approach

**Sequential Layer-by-Layer**: Each phase is validated independently before proceeding to the next. This isolates issues and allows rollback at any phase.

## Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Gradle version | 9.4.0 | Latest stable |
| Gradle runtime Java | Corretto 21 via mise | Amazon distribution, LTS |
| Target bytecode | Java 8 | Maintain backward compatibility for consumers |
| Build script format | Kotlin DSL | Type-safe, better IDE support |
| Versioning plugin | Remove (manual version) | `com.widen.versioning` last updated 2019, incompatible |
| Nexus plugin | 2.0.0 | Latest stable, Gradle 9 compatible |
| Dependency updates | Conservative | Only update if required for Gradle 9 |
| Testing strategy | Incremental | Validate after each phase |

## Migration Phases

### Phase 1: Gradle Wrapper Upgrade

Upgrade from 6.8.3 to 9.4.0.

**Files:**
- `gradle/wrapper/gradle-wrapper.properties`

**Validation:** `./gradlew --version` shows 9.4.0

### Phase 2: Java Environment via mise

Configure Corretto 21 for running Gradle.

**Files:**
- `.mise.toml` (new)

**Content:**
```toml
[tools]
java = "corretto-21"
```

**Validation:** `mise install && java -version` shows Corretto 21

### Phase 3: Convert settings.gradle to Kotlin DSL

**Files:**
- `settings.gradle` → `settings.gradle.kts`

**Validation:** `./gradlew help` succeeds

### Phase 4: Convert build.gradle to Kotlin DSL

Full conversion with modernizations:
- Kotlin syntax (double quotes, `.set()` methods)
- Java toolchain targeting Java 8
- Lazy task registration (`tasks.register<T>`)
- Modern artifact configuration (`archiveClassifier`)
- Kotlin-style publishing DSL

**Files:**
- `build.gradle` → `build.gradle.kts`

**Validation:** `./gradlew build` succeeds

### Phase 5: Plugin Updates

- Update `io.github.gradle-nexus.publish-plugin` 1.1.0 → 2.0.0
- Remove `com.widen.versioning`, set version manually

**Files:**
- `build.gradle.kts`

**Validation:** `./gradlew build` succeeds

### Phase 6: Final Validation

**Checks:**
1. `./gradlew build --warning-mode all` - zero deprecation warnings
2. `./gradlew publishToMavenLocal` - publishing works
3. Verify bytecode: `javap -verbose build/classes/java/main/com/widen/urlbuilder/UrlBuilder.class | grep "major version"` shows 52 (Java 8)

## Files Changed

| File | Action |
|------|--------|
| `gradle/wrapper/gradle-wrapper.properties` | Modify |
| `.mise.toml` | Create |
| `settings.gradle` | Delete |
| `settings.gradle.kts` | Create |
| `build.gradle` | Delete |
| `build.gradle.kts` | Create |

## Risk Mitigation

- **Incremental validation** catches issues early
- **Git commits per phase** allow easy rollback
- **Conservative dependency updates** minimize scope
- **Java 8 toolchain** ensures bytecode compatibility verified at build time
