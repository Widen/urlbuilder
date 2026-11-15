# AGENTS.md

## Java Commands

All `java`, `javac`, and related JVM commands must be prefixed with `mise exec --` to ensure the correct Java version (as specified by [mise](https://mise.jdx.dev/)) is used. Examples:

```bash
mise exec -- java -version
mise exec -- ./gradlew build
mise exec -- ./gradlew test
```

This applies to any direct `java` invocation and to any tool that shells out to the JVM.
