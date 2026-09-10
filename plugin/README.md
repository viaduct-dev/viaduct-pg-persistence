# Gradle Plugin

The `dev.viaduct.pg-persistence` plugin generates a Hibernate and PostgreSQL persistence
model from an assembled Viaduct GraphQL schema.

The plugin is the build-time half of the persistence integration. Pair it with
`dev.viaduct.persistence:runtime` to execute generated db mappings at runtime.

```kotlin
plugins {
    kotlin("jvm")
    id("com.airbnb.viaduct.application-gradle-plugin") version "<viaduct-version>"
    id("dev.viaduct.pg-persistence") version "0.1.0-SNAPSHOT"
}

```

The plugin generates dynamic HBM metadata and PostgreSQL overlays, not entity source code.

Run:

```bash
./gradlew buildViaductEffectiveModel
./gradlew hibernateSchemaDiff
```

The generated PostgreSQL migration input and repeatable pg_graphql metadata are written to:

```text
build/generated/viaduct-effective-model/META-INF/postgresql-migration.sql
build/generated/viaduct-effective-model/META-INF/pg-graphql-metadata.sql
```

See the repository [README](../README.md) for repository configuration, GraphQL conventions,
custom naming strategies, generated artifacts, and migration workflow.
