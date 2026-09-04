# Viaduct PG Persistence

## Overview

Viaduct PG Persistence lets a Viaduct application's GraphQL schema define its data model.
Types, fields, and relationships are written once in GraphQL instead of being maintained
separately in GraphQL, Kotlin, and database mapping code.

The project has two parts:

- The **Gradle plugin** reads the assembled GraphQL schema and generates the database model,
  Kotlin persistence classes, and PostgreSQL integration files.
- The **runtime library** sends Viaduct's selected fields to `pg_graphql` and converts the response
  back into Viaduct result types.

The normal workflow is:

1. Describe the model in GraphQL.
2. Generate and review the proposed database changes.
3. Apply the approved changes through the application's migration system.
4. Use the runtime library to load persisted GraphQL types.

Schema-first does not mean that builds automatically modify a database. Generated SQL is review
input; the application remains responsible for committing and applying migrations.

## Getting Started

### 1. Add the repository

Make the plugin and libraries available to Gradle:

```kotlin
// settings.gradle.kts
pluginManagement {
    repositories {
        maven("https://viaduct-dev.github.io/viaduct-pg-persistence/")
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        maven("https://viaduct-dev.github.io/viaduct-pg-persistence/")
        mavenCentral()
    }
}
```

### 2. Apply the plugin and runtime

Add the persistence plugin to the Viaduct application and choose a package for generated code:

```kotlin
// build.gradle.kts
plugins {
    kotlin("jvm")
    id("com.airbnb.viaduct.application-gradle-plugin") version "<viaduct-version>"
    id("dev.viaduct.pg-persistence") version "0.1.0-SNAPSHOT"
}

dependencies {
    implementation("dev.viaduct.persistence:runtime:0.1.0-SNAPSHOT")
}

viaductPgPersistence {
    packageName.set("com.example.persistence.generated")
}
```

### 3. Define the data model

Write ordinary Viaduct GraphQL types. An object that implements the framework-provided `Node`
interface is persistent by default, and object references describe relationships:

```graphql
type Group implements Node {
  id: ID
  name: String
  members: [GroupMember]
}

type GroupMember implements Node {
  id: ID
  group: Group
  displayName: String
}
```

Put types backed by another service, or types that should not become database tables, in files
ending with `.notable.graphqls`. This is possible, but not recommended: standard practice is a
second, database-free Viaduct tenant module instead. See [Excluding Types](#excluding-types).

### 4. Generate the database model

Run:

```bash
./gradlew buildViaductEffectiveModel
```

The generated review files are written under:

```text
build/generated/viaduct-effective-model/META-INF/
```

Start with `postgresql-migration.sql` for relational changes and
`pg-graphql-metadata.sql` for the GraphQL-facing database metadata.

### 5. Review and apply a migration

Review the generated SQL, adapt it to the application's existing schema, and commit the approved
changes to the application's normal migration system. For an existing database,
`hibernateSchemaDiff` can compare the generated model with that database.

The plugin never applies these changes automatically.

### 6. Configure the runtime

Create a `DbClient` with the application's HTTP client, `pg_graphql` endpoint, and
request-specific authentication headers:

```kotlin
val dbClient = DbClient(
    httpClient = httpClient,
    endpoint = "$postgresGraphqlEndpoint/graphql",
    requestHeaders = DbRequestHeaders { context ->
        mapOf("Authorization" to "Bearer ${accessTokenFor(context)}")
    },
)
```

Inject this client into Viaduct resolvers that load persistent types. See
[Use pg_graphql as a Db Backend](#use-pg_graphql-as-a-db-backend) for resolver examples
and [Create or Update a Database](#create-or-update-a-database) for the complete migration
workflow.

## Requirements

- Java 21
- Kotlin/JVM
- A Viaduct application that provides `assembleViaductCentralSchema`
- PostgreSQL when applying the generated SQL
- `pgcrypto` and `pg_graphql` for the complete PostgreSQL/GraphQL overlay

The current artifact version is `0.1.0-SNAPSHOT`.

## Development Checks

Both published modules apply KtLint, Detekt, and SpotBugs through the standard Gradle `check`
lifecycle. Run the complete verification suite with:

```bash
./gradlew check
```

The checked-in baselines record existing findings so that new lint, best-practice, or SpotBugs
findings fail the build without requiring an unrelated cleanup. Regenerate a module's baseline
only when deliberately accepting its current findings.

## Published Artifacts

The two public libraries use the `dev.viaduct.persistence` Maven group:

| Coordinate | Purpose |
| --- | --- |
| `dev.viaduct.persistence:runtime` | Viaduct db runtime, pg_graphql translation, and client |
| `dev.viaduct.persistence:plugin` | Gradle plugin, persistence model generation, overlays, and Liquibase integration |

The Gradle plugin ID is `dev.viaduct.pg-persistence`.

## GraphQL Conventions

By default, every object type in an ordinary `*.graphqls` file that implements the
framework-provided `Node` interface is persistent.

```graphql
type Group implements Node {
  id: ID
  name: String
  members: [GroupMember]
}

type GroupMember implements Node {
  id: ID
  group: Group
  person: Person
}

type Person implements Node {
  id: ID
  displayName: String
}
```

This model produces three entity tables. `GroupMember.group` and `GroupMember.person` become
foreign keys. `Group.members` uses the foreign key implied by `GroupMember.group`.

Relationships are usually declared as object references. A scalar field such as `groupId` must
not shadow a `group: Group` relationship declared on the same type.

A scalar `ID` field carrying `@idOf(type: "Group")` is itself a foreign key to `Group`, without an
accompanying object reference:

```graphql
type Person implements Node {
  id: ID
  groupId: ID @idOf(type: "Group")
}
```

`Person.groupId` directs the same foreign key that an object-typed `group: Group` field would,
and can serve as the target of `Group`'s own to-many collection field. It stays a plain scalar
column and is not renamed in pg_graphql's generated schema, so it never collides with a
synthesized relationship accessor.

Relay-style connections use the same relationship rules as lists. A connection wrapper is not a
persistent entity; the persistence model follows `edges.node` to identify the target collection:

```graphql
type Group implements Node {
  id: ID
  members: PersonConnection
}

type Person implements Node {
  id: ID
}

type PersonConnection @connection {
  edges: [PersonEdge]
  pageInfo: PageInfo
}

type PersonEdge @edge {
  node: Person
}
```

`Group.members` is therefore modeled as a to-many relationship to `Person`; the connection and
edge types do not produce separate entity tables. `pageInfo`, cursors, and connection arguments
are API fields and do not change the persistence mapping. Scalar and object fields on an edge are
persisted on the association row when the relationship uses a join table.

For every join-table-backed connection, including an edge containing only `node` and `cursor`, the
pg_graphql adapter reads the real `membersAssociations` connection, applies pagination to those
association rows, selects the row's `node` relationship, and unwraps each row into the authored
Viaduct edge. A single unidirectional connection uses the target table directly and is passed
through without this association-row translation. No view or SQL function is generated.

Relationships do not need to be bidirectional. The generated mappings preserve the authored
relationship names used by Viaduct:

- An object reference becomes a foreign key.
- A list or connection with a matching back-reference on the target uses the target foreign key.
- A single unidirectional list or connection to a target uses the target foreign key; no join table
  is created.
- A mutual list relationship uses one deterministic join table.
- Multiple unidirectional lists or connections to the same target use separate join tables.
- Association-backed relationships are exposed through pg_graphql's ordinary foreign-key
  relationship from the owner to the real association table. The generated relationship name is
  `<fieldName>Associations` (for example, `membersAssociations`).

Join tables are created in the `viaduct_internal` schema by default. Because pg_graphql must read
association rows directly, that schema must be included in the provider's exposed schemas and its
tables must have suitable `SELECT` and RLS policies. The persistence plugin does not silently hide
the schema or manufacture a view/function to bypass that requirement. Self-referential
relationships use distinct owner and target columns. Override the schema when needed:

```kotlin
viaductPgPersistence {
    associationSchemaName.set("application_internal")
}
```

The raw pg_graphql schema is an internal transport surface, not the authored public API. PostgreSQL
columns required for filtering and pg_graphql's automatic inverse foreign-key relationships may
also be present there. Viaduct remains the public schema and controls which fields clients can use.

Supported scalar mappings are:

| GraphQL | Kotlin/Hibernate |
| --- | --- |
| `ID` | `UUID` |
| `String` | `String` |
| `Date` | `LocalDate` |
| `DateTime` | `OffsetDateTime` |
| `Time` | `LocalTime` |
| `Boolean` | `Boolean` |
| `Byte`, `Short`, `Int`, `Long` | Matching integer type |
| `Float` | `Double` |
| `BigDecimal` | `java.math.BigDecimal` |
| `BigInteger` | `java.math.BigInteger` |
| `JSON` | `String`, stored as `jsonb` |
| GraphQL enum | Generated Kotlin enum |
| One-dimensional scalar list | PostgreSQL array |

Resolver-backed fields that do not form relationships between included persistent types are not
persisted. A type reachable from a `@db` root cannot contain a transitively reachable
`@resolver` field because pg_graphql must resolve the complete db.

### Excluding Types

Put externally backed or resolver-only types in a file ending in `.notable.graphqls`:

```text
src/main/viaduct/schema/GitHub.notable.graphqls
```

Definitions in notable files are excluded from persistence discovery. A notable file cannot
contain GraphQL extensions, and an ordinary file cannot redefine or extend a type defined in a
notable file. These cases fail generation instead of producing a partial database model.

`.notable.graphqls` works, but is not the recommended way to keep externally backed types out of
the database. Standard practice is a second Viaduct tenant module that never applies this plugin
and has no `@db` schema of its own, alongside the tenant that owns persistence. This keeps
externally backed types on equal footing with database-backed ones — same module boundary,
Kotlin dependency rules, and resolver ownership — rather than relying on a filename convention to
exclude them from a database they were never going to belong to.

### Persistence Policy YAML

Persistence policy lives next to the schema in the optional
`src/main/viaduct/persistence.yaml` file. With no file, every eligible `Node` object discovered in
an ordinary schema file is persisted. Policy values do not belong in the Gradle build script.

The complete file shape is:

```yaml
denyList:
  types:
    - ExternalProfile

semanticNotNull:
  types:
    - Group
  fields:
    - Person.displayName
    - GroupMember.person

relationships:
  unidirectionalTargetForeignKeyFields:
    - Group.members
  inverseFieldOverrides:
    ExternalGroup.discordServerRoles: server
```

The YAML is strictly validated. Unknown keys, values of the wrong YAML type, duplicate list entries,
unknown coordinates, and coordinates that do not apply to a persistent object fail generation.

#### Denylisting Types

`denyList.types` is subtracted from the automatically discovered persistent `Node` objects. This is
a denylist, not an allowlist: a newly added eligible type is persisted unless it is explicitly
denied. A denylist entry must name a discovered persistent object. Unknown types and types already
excluded by another mechanism are rejected as stale or ineffective configuration.

If a retained persistent field has a stored relationship to a denied type, generation fails and
names both coordinates. The plugin does not silently remove the relationship or reinterpret it as
a scalar. Resolver-only fields may still refer to non-persistent types under the existing resolver
rules.

#### Semantic Non-null

`semanticNotNull` makes the persistence representation stricter than the authored GraphQL schema.
For example:

```graphql
type Person implements Node {
  id: ID
  displayName: String
}
```

```yaml
semanticNotNull:
  fields:
    - Person.displayName
```

`Person.displayName` remains nullable in the public GraphQL schema, but the generated Kotlin
property, Hibernate mapping, and PostgreSQL column are non-null. The effective rule is:

```text
non-null in persistence = GraphQL SDL non-null
                       OR containing type is in semanticNotNull.types
                       OR field is in semanticNotNull.fields
```

A coordinate in `semanticNotNull.types` applies to the stored singular fields declared on that
object, including owning to-one relationship foreign keys. It does not cascade into related types.
A coordinate in `semanticNotNull.fields` applies only to that stored field. Basic fields, scalar
arrays, and stored to-one relationships are supported. For an array, the setting controls whether
the array column itself can be null, not whether individual elements can be null.

Resolver-only fields, computed fields, and to-many relationships cannot be field-level semantic
non-null coordinates because they do not map to a nullable owning column. Primary and generated
internal IDs remain mandatory independently of this policy. It is valid, though redundant, to list
a field that is already non-null in GraphQL.

Semantic non-null does not rewrite GraphQL SDL. Generation also packages the expanded field
coordinates as runtime metadata. The result-returning `DbClient` operations use that metadata to
check partial responses: a null semantic-non-null field is accepted when an upstream GraphQL error
explains its response path, while an unexplained null adds a `SEMANTIC_NON_NULL_VIOLATION` error.
Existing rows must still satisfy the database constraint before its reviewed schema-diff migration
is applied.

#### Relationship Overrides

The `relationships` section replaces the former relationship-only YAML file. Use
`unidirectionalTargetForeignKeyFields` to select target-side foreign-key storage and
`inverseFieldOverrides` to disambiguate a reverse relationship. All persistence policy now has one
source of truth.

Override only the YAML location from Gradle when needed:

```kotlin
viaductPgPersistence {
    persistenceConfigFile.set(layout.projectDirectory.file("config/persistence.yaml"))
}
```

## Tasks

| Task | Purpose |
| --- | --- |
| `validateViaductPgPersistenceSchema` | Validate db and persistence constraints |
| `generateViaductPgPersistenceModel` | Generate plain entities and `orm.xml` from the assembled schema |
| `buildViaductEffectiveModel` | Compile the model through Hibernate and generate database overlays |
| `hibernateSchemaSnapshot` | Write a reviewable Liquibase JSON snapshot |
| `hibernateSchemaDiff` | Compare the generated model with a PostgreSQL database |

Generate the effective model:

```bash
./gradlew buildViaductEffectiveModel
```

The main generated outputs are:

```text
build/generated/viaduct-persistence/
  kotlin/...
  resources/META-INF/orm.xml
  resources/META-INF/persistence.xml

build/generated/viaduct-effective-model/META-INF/
  pg-graphql.sql
  pg-graphql-metadata.sql
  pg-graphql-overlay.sql
  postgresql-migration.sql
  postgresql-prerequisites.sql
  postgresql-repeatable.sql
```

The effective SQL directory is packaged into the application JAR. The effective model and
Liquibase reference metadata are rebuilt from the assembled schema, generated mapping, classpath,
and naming configuration; no metadata descriptor is packaged or passed between tasks.

## Use pg_graphql as a Db Backend

`buildViaductEffectiveModel` produces repeatable SQL that maps the generated PostgreSQL schema to
the authored GraphQL names. Apply this file after the relational tables and constraints exist:

```text
build/generated/viaduct-effective-model/META-INF/pg-graphql-metadata.sql
```

`pg-graphql.sql` remains a complete convenience bundle for creating a fresh schema. Do not use the
complete bundle as a repeatable production migration; review `postgresql-migration.sql` as
migration input and use `pg-graphql-metadata.sql` for repeatable metadata.

Send GraphQL requests to whatever endpoint fronts `pg_graphql` on the target Postgres instance —
for Supabase, that's:

```text
https://<project>.supabase.co/graphql/v1
```

Required headers depend on that fronting layer, not on `pg_graphql` itself. Supabase's PostgREST
gateway expects both the caller's JWT and its own API key:

```http
Authorization: Bearer <access-token>
apikey: <publishable-or-anon-key>
Content-Type: application/json
```

A plain Postgres instance with `pg_graphql` behind a different gateway may need only an
`Authorization` header, or a different scheme entirely.

### Semantic Non-null and pg_graphql

Semantic non-null reaches `pg_graphql` through the PostgreSQL schema rather than through custom
GraphQL metadata:

```text
persistence.yaml
  -> effective persistence model
  -> generated Kotlin and Hibernate nullable="false" mapping
  -> reviewed hibernateSchemaDiff migration
  -> PostgreSQL NOT NULL constraint
  -> pg_graphql schema introspection
```

The `pg-graphql-metadata.sql` overlay continues to provide authored type and relationship names; it
does not carry semantic-nullability declarations. After the `NOT NULL` migration is applied,
`pg_graphql` observes the database constraint when it generates its internal transport schema and
PostgreSQL rejects null writes. The authored Viaduct field remains nullable, so public API clients
do not see a schema-breaking nullability change.

This distinction matters operationally: changing `persistence.yaml` and rebuilding artifacts is not
enough to enforce semantic non-null in an existing database. Review and apply the output of
`hibernateSchemaDiff` first, including any data backfill needed for existing null values. Until the
database constraint exists, `pg_graphql` has no semantic non-null policy to discover.

For partial GraphQL responses, use `fetchResult` or `fetchJsonResult`. Their `DbResult` preserves
partial data and each upstream error's message, path, locations, and extensions. The runtime
restores both data and error paths through the same response-shape transformation, correlates
semantic nulls with those authored paths, and adds a
violation only for an unexplained null. The original `fetch` and `fetchJson` convenience methods
remain strict for compatibility and throw `UpstreamGraphqlException` when `pg_graphql` returns
errors. `DbResult` does not itself install errors into Viaduct's field-error channel; resolvers that
retain partial data must do that at their execution boundary.

The overlay enables row-level security but does not invent authorization policies. Define and
migrate the PostgreSQL RLS policies required by the application.

Viaduct db selections and pg_graphql use different collection shapes:

```graphql
# Viaduct
fragment Main on GroupCollection {
  nodes { id name }
}

# pg_graphql equivalent
fragment Main on GroupConnection {
  edges { node { id name } }
}
```

Add the runtime library:

```kotlin
dependencies {
    implementation("dev.viaduct.persistence:runtime:0.1.0-SNAPSHOT")
}
```

Configure the endpoint and provider-specific headers. Header resolution runs for every request,
so credentials come from the current execution context rather than being fixed at client
construction:

```kotlin
import dev.viaduct.persistence.runtime.db.DbClient
import dev.viaduct.persistence.runtime.db.DbRequestHeaders

val dbClient = DbClient(
    httpClient = httpClient,
    endpoint = "$postgresGraphqlEndpoint/graphql",
    requestHeaders = DbRequestHeaders { context ->
        mapOf("Authorization" to "Bearer ${accessTokenFor(context)}")
    },
)
```

Node resolvers can then hydrate their owned selections from a filtered pg_graphql collection:

```kotlin
return dbClient.fetchByInternalId(
    ctx = ctx,
    collectionField = "groupCollection",
    id = ctx.id.internalID,
    ownedSelections = ctx.ownedSelections(),
    requestedSelections = ctx.selections(),
)
```

The runtime also exposes `fetch` for an explicit `DbRead`, `fetchResult` for partial typed data and
structured upstream errors, and `fetchJsonResult` for the equivalent raw JSON result. Use
`fetchNode` when requested node
references must be attached, and `fetchUuidIds` for collection resolvers that return node
references. For connection-backed collection resolvers, `fetchUuidConnection` accepts Viaduct's
standard `first`/`after` and `last`/`before` arguments and returns UUID references together with
the provider's cursors and `pageInfo`; pass the returned cursor to the next call so pagination
remains caller-managed and database-backed.

The runtime does not require a translation metadata resource. It derives the type and field map
from generated Viaduct reflection and recognizes a collection structurally: a generated
`ConnectionBuilder` with a compatibility `nodes` field, or a connection whose `edges` object has a
`node` field. A Viaduct collection or connection may expose `nodes`; pg_graphql exposes the same
records through `edges { node }`. Translation rewrites recognized `nodes` selections to that shape
and marks the generated edge selections with an internal alias. The response restorer changes the
alias back to `nodes` recursively, including for nested collections, while ordinary domain fields
named `nodes` or `edges` pass through unchanged. When reflection finds custom fields on a
connection edge, translation uses the real `<fieldName>Associations` relationship, moves edge
fields into the association row, and restores the row to the authored edge shape. This convention
also applies recursively to nested connections; no generated descriptor, view, or SQL function is
needed.

Translation is included in the runtime library. The runtime owns GraphQL request construction,
upstream error handling, response-shape restoration, Viaduct GRT mapping, and node-reference
hydration. The application still owns the `HttpClient` lifecycle, endpoint, and authentication
policy.

Every `@db` type is validated during generation. A transitively reachable `@resolver` field
is rejected because pg_graphql cannot resolve that field from the database. Types or fields
backed by external services should remain outside that db — standard practice is a second,
database-free tenant module, with `.notable.graphqls` as the fallback (see
[Excluding Types](#excluding-types)).

## Create or Update a Database

The plugin does not apply migrations. A typical workflow is:

1. Change the GraphQL schema.
2. Run `buildViaductEffectiveModel`.
3. Run `hibernateSchemaDiff` against the current database.
4. Review and edit the generated SQL.
5. Commit the approved migration to the application's migration system.
6. Review `META-INF/postgresql-migration.sql` and include the required changes in the migration.
7. Apply `META-INF/pg-graphql-metadata.sql` after the relational schema exists.

Configure the comparison database without committing credentials:

```kotlin
viaductPgPersistence {
    schemaDiffUrl.set(
        providers.environmentVariable("SCHEMA_DIFF_DATABASE_URL")
    )
    schemaDiffUser.set(
        providers.environmentVariable("SCHEMA_DIFF_DATABASE_USER")
    )
    schemaDiffPassword.set(
        providers.environmentVariable("SCHEMA_DIFF_DATABASE_PASSWORD")
    )
}
```

Then run:

```bash
./gradlew hibernateSchemaDiff
```

The review SQL is written to:

```text
build/schema-diff/hibernate-review.postgresql.sql
```

Potentially destructive changes are separated into:

```text
build/schema-diff/hibernate-destructive-review.postgresql.sql
```

The normal review file excludes drops, empty-comment changes, and duplicate changes. Liquibase
cannot reliably distinguish a rename from a drop plus add, so removals, renames, data backfills,
and database-owned constraints remain explicit manual migration work.

The combined `pg-graphql.sql` overlay:

- Creates self-contained generated global ID columns for supported Viaduct `Node` db types.
- Enables row-level security on generated entity tables.
- Preserves authored GraphQL type and relationship names with pg_graphql comments.
- Enforces non-null scalar-array elements.
- Names the ordinary foreign-key relationships used to read real association tables.

## Customize Hibernate

The defaults use:

- `ViaductImplicitNamingStrategy`
- `ViaductPhysicalNamingStrategy`
- No metadata customizers

The physical strategy pluralizes table names, converts columns to snake case, and maps the
generated internal ID to `_uuid_id`.

Supply compiled application classes by name:

```kotlin
viaductPgPersistence {
    implicitNamingStrategyClassName.set(
        "com.example.persistence.CustomImplicitNamingStrategy"
    )
    physicalNamingStrategyClassName.set(
        "com.example.persistence.CustomPhysicalNamingStrategy"
    )
    metadataCustomizerClassNames.add(
        "com.example.persistence.CustomMetadataCustomizer"
    )
}
```

A metadata customizer implements:

```kotlin
import dev.viaduct.persistence.hibernate.HibernateMetadataCustomizer
import org.hibernate.boot.MetadataBuilder

class CustomMetadataCustomizer : HibernateMetadataCustomizer {
    override fun customize(metadataBuilder: MetadataBuilder) {
        // Apply additional Hibernate metadata configuration.
    }
}
```

For complete control, replace the generated mapping:

```kotlin
viaductPgPersistence {
    replacementOrmXml.set(layout.projectDirectory.file("config/orm.xml"))
}
```

`replacementOrmXml` is a complete replacement, not a merge. The effective-model task validates
that it still represents the GraphQL fields, relationship targets, and nullability. Complete
replacement is supported but is not the recommended default.

## Liquibase Database Driver

The plugin library resolves a Liquibase reference database from a YAML descriptor file:

```text
hibernate:viaduct:<path-to-descriptor.yaml>
```

The path points to a descriptor written by `ViaductHibernateDatabase.reference(configuration)`,
holding the full `HibernateMetadataConfiguration` — including the semantic persistence model used
for pg_graphql-aware diffing, when one is available. The driver reads the descriptor, creates an
isolated classloader from it, applies the configured Hibernate naming strategies and metadata
customizers, and returns the same metadata used by `buildViaductEffectiveModel`. The Gradle tasks
write and delete the descriptor automatically.

Add the driver when using it outside the plugin tasks:

```kotlin
dependencies {
    implementation(
        "dev.viaduct.persistence:plugin:0.1.0-SNAPSHOT"
    )
    implementation("org.liquibase:liquibase-core:5.0.3")
    implementation("org.liquibase.ext:liquibase-hibernate7:5.0.3")
}
```

Programmatic usage:

```kotlin
import liquibase.database.DatabaseFactory
import liquibase.resource.ClassLoaderResourceAccessor
import dev.viaduct.persistence.hibernate.HibernateMetadataConfiguration
import dev.viaduct.persistence.liquibase.ViaductHibernateDatabase

val configuration = HibernateMetadataConfiguration.default()
val accessor = ClassLoaderResourceAccessor(
    ViaductHibernateDatabase::class.java.classLoader
)
ViaductHibernateDatabase.reference(configuration).use { reference ->
    val database = DatabaseFactory.getInstance().openDatabase(
        reference.url,
        null,
        null,
        null,
        accessor,
    )
    try {
        // Use as a Liquibase snapshot or diff reference database.
    } finally {
        database.close()
    }
}
accessor.close()
```

`HibernateMetadataConfiguration.default()` works as-is once the plugin has generated a mapping
file: it uses the plugin's own generated location
(`build/generated/viaduct-persistence/resources/META-INF/orm.xml`), the current JVM's classpath,
and the entity classes declared in that mapping file
(`HibernateMetadataConfiguration.managedClassNamesIn`).

`mappingFile`, `classpath`, and `managedClassNames` have no default on the primary constructor
itself, on purpose: a process building configurations for more than one mapping file — a test
generating several scenario-specific models is the common case — must not have a missing override
silently fall back to an unrelated mapping file that happens to exist at the conventional location.
Use the primary constructor with an explicit `mappingFile` whenever more than one is in play, or
the entity classes aren't already on the running JVM's classpath:

```kotlin
val mappingFile = File("some/other/orm.xml")
val configuration = HibernateMetadataConfiguration(
    mappingFile = mappingFile,
    classpath = listOf(File("build/classes/kotlin/main")),
    managedClassNames = HibernateMetadataConfiguration.managedClassNamesIn(mappingFile),
)
```

Naming strategies, dialect, metadata customizers, and Hibernate settings are policy knobs that
default to the same configuration described in [Customize Hibernate](#customize-hibernate) on both
paths; pass only the ones you want to change:

```kotlin
val configuration = HibernateMetadataConfiguration.default(
    physicalNamingStrategyClassName = "com.example.persistence.CustomPhysicalNamingStrategy",
    metadataCustomizerClassNames = listOf("com.example.persistence.CustomMetadataCustomizer"),
)
```

Because the descriptor is a real file, a standalone Liquibase CLI process can use this URL too —
write the descriptor once with `HibernateMetadataConfigurationDescriptor.write(configuration, file)`
and pass `hibernate:viaduct:<absolute path to file>` as the `--url`, with the plugin library and
its Liquibase dependencies on the CLI's classpath.

This driver is a Liquibase reference database, not a JDBC driver and not an application runtime
ORM. Applications using pg_graphql do not need Hibernate in their production runtime. Tests or
tools that explicitly boot the generated persistence unit should add the plugin library and a
PostgreSQL JDBC driver to their own test/tool configuration.

```kotlin
dependencies {
    testImplementation(
        "dev.viaduct.persistence:plugin:0.1.0-SNAPSHOT"
    )
    testImplementation("org.postgresql:postgresql:42.7.5")
}
```

## Local Development

Build and test both published projects:

```bash
./gradlew build
```

Publish both libraries to an isolated local Maven repository:

```bash
./gradlew publish \
  -PisolatedRepository=/tmp/viaduct-persistence-repository
```

Use that path in a consumer's `pluginManagement` and `dependencyResolutionManagement`
repositories.

### Release Signing

Release publications are signed with an in-memory PGP key. Supply the credentials as Gradle
properties:

- `signingKeyId`: the signing key ID
- `signingKey`: the ASCII-armored private key
- `signingPassword`: the private key passphrase

In GitHub Actions, expose them as `ORG_GRADLE_PROJECT_signingKeyId`,
`ORG_GRADLE_PROJECT_signingKey`, and `ORG_GRADLE_PROJECT_signingPassword`. Snapshot publications
do not require signing. Publishing a non-snapshot version to a Maven repository fails when signing
credentials are missing.

Pushing a `vX.Y.Z` tag, or manually running the **Build signed artifacts** workflow with a version,
builds and tests every public module, verifies every Maven artifact has a detached signature, and
uploads the signed Maven repository as a GitHub Actions artifact. Manual snapshot builds are
supported for validation. A non-snapshot build fails if any generated POM references a snapshot
dependency.

### Maven Central Snapshots

The **Publish Maven Central snapshot** workflow publishes versions ending in `-SNAPSHOT` to:

```text
https://central.sonatype.com/repository/maven-snapshots/
```

Configure these GitHub repository secrets with a Central Portal user token:

- `MAVEN_CENTRAL_USERNAME`
- `MAVEN_CENTRAL_PASSWORD`

The token credentials are separate from the account's interactive username and password. Snapshot
artifacts are not release artifacts and do not require PGP signing.

## Modules

| Module | Responsibility |
| --- | --- |
| `runtime` | Db transport, Viaduct GRT mapping, and node-reference hydration |
| `plugin` | Persistence model generation, overlays, Liquibase integration, and Gradle orchestration |

Application schemas remain external compatibility consumers and are not copied into this
repository.

## License

Viaduct PG Persistence is licensed under the
[Apache License, Version 2.0](LICENSE), matching Viaduct.
