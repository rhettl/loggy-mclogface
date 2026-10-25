# Loggy McLogface

A Fabric server troubleshooting mod named in the spirit of Boaty McBoatface.
The public provider API is established; the command and reporting runtime is
being integrated against the [implementation contract](docs/CONTRACT.md).
Administrators use `/loggy list` to see registered targets and
`/loggy report <target>` to prepare and upload a report; `minecraft`, each
registered provider ID, and `all` are valid targets.

Loggy targets Java 21, Fabric, and Minecraft 1.21.1. The source repository is
[`rhettl/loggy-mclogface`](https://github.com/rhettl/loggy-mclogface). Its public
Maven coordinates are `io.github.rhettl:loggy-mclogface:<version>`; the initial
release is not published yet while the reporting runtime is integrated.

## Commands

All commands require permission level 2 and operate on the current server, including
single-player's integrated server:

```text
/loggy list
/loggy report minecraft
/loggy report crests
/loggy report all
```

`list` describes the registered targets without collecting or uploading anything.
`report` collects built-in environment evidence, the requested providers, and the
current instance's `latest.log`, then saves prepared text in `logs/loggy/` and
uploads it to mclo.gs. When Loggy is installed on the requesting player's client, the report link is
automatically copied to that player's clipboard, with a chat confirmation. The
response keeps local-copy information on its first line, followed by the clickable
report URL and `[copy]` at the end of its second line. These manual actions remain
available to other clients and console use. Only
validated mclo.gs share links are accepted by the optional client receiver. Single-player's
process log may contain both client and server messages. The ten newest saved
reports are retained. Log/report truncation is identified in the report.

Uploads require successful retrieval of the service's filters and limits; if these
cannot be verified, Loggy saves a locally redacted fallback and skips upload. No
report is collected or uploaded without a command. Review what your providers
include: redaction cannot guarantee removal of arbitrary secrets.

Version 0.2.0 adds namespaced provider IDs with a single `:` separator.
Version 0.1.1 fixes development-classpath dependency metadata missing in 0.1.0.
The build checks a separate Maven consumer in addition to the unit suite, so a
correct nested JAR alone cannot satisfy the publication check.

## Build and local Maven publication

```sh
./gradlew build
./gradlew publishToMavenLocal -Pmod_version=0.1.0-SNAPSHOT
```

For a plain Maven directory that can be inspected or used by another local
checkout, run:

```sh
./gradlew publishMavenJavaPublicationToLocalMavenRepository -Pmod_version=0.1.0-SNAPSHOT
```

The local repository is written to `build/maven-repository/`. It contains the
remapped runtime JAR, remapped sources JAR, and Maven POM. The check workflow
builds and tests pull requests and release tags; a separate workflow handles
publication.

## Use Loggy from another Fabric mod

Add the Maven repository that hosts Loggy, then use Loom's mod dependency and
`include` configurations to put Loggy inside the parent mod:

```kotlin
repositories {
    maven("https://rhettl.github.io/loggy-mclogface/")
}

dependencies {
    val loggy = modImplementation("io.github.rhettl:loggy-mclogface:0.1.0")
    include(loggy)
}
```

Use the exact version you intend to ship. For local testing, replace the Maven
URL with `mavenLocal()` and set `-Pmod_version=0.1.0-SNAPSHOT` when publishing.
Nested Loggy includes its pinned mclo.gs API library. The runtime artifact
excludes the library's Gson dependency and uses Minecraft 1.21.1's Gson 2.10.1.

## Register a diagnostic provider

Register during common initialization:

```java
Loggy.register(new DiagnosticProvider() {
    public String id() { return "example"; }
    public String displayName() { return "Example Mod"; }
    public String description() { return "Example configuration diagnostics"; }
    public DiagnosticReport collect(DiagnosticContext context) {
        return new DiagnosticReport(
            List.of(new DiagnosticSection("Configuration", List.of("Enabled: true"))),
            List.of());
    }
});
```

Types are in `io.github.rhettl.loggy.api`; `List` is `java.util.List`. Collection
runs on the server thread, so providers must read bounded in-memory state only.
Return plain text without secrets. Do not block, perform I/O, mutate Minecraft
state, or retain the context. Registration closes when the first server starts.
IDs are lowercase command-safe names; `minecraft`, `all`, `list`, and `report`
are reserved. Since 0.2.0 an ID may carry one namespace, written `namespace:name`
(for example `bca` and `bca:crests` are distinct providers). Each segment matches
`[a-z][a-z0-9_-]*`; empty segments and more than one `:` are rejected. Targets are
selected by exact ID, as in `/loggy report bca:crests`; wildcard selectors such as
`bca:*` are not supported.

The `internal` package and `Loggy.registry()` are implementation details, not a
supported extension API. See [the implementation contract](docs/CONTRACT.md)
for the current behavior and acceptance requirements.

## Maven releases

The Maven repository is served from this project's `gh-pages` branch at
`https://rhettl.github.io/loggy-mclogface/`. The first release has not been
published yet. The workflow publishes `<base>-SNAPSHOT` from `main`, and a
`vX.Y.Z` tag publishes that exact version. It also supports a manual run from
`main` or a release tag. Existing release versions cause publication to fail;
snapshots may be refreshed. The workflow preserves the branch's existing
artifacts and metadata and serializes publication jobs so concurrent runs do
not race. GitHub Pages must serve the `gh-pages` branch from its root.

No cross-repository secret is needed. The workflow uses the repository's
`GITHUB_TOKEN` with `contents:write` permission to update its own `gh-pages`
branch. Pull request checks only build and test; they never publish. CI does not
upload temporary build artifacts; published Maven files remain in the Pages
branch as repository contents and do not expire after a day.

## Upstream acknowledgment

Loggy uses the [mclogs-java API](https://github.com/aternosorg/mclogs-java) and
uploads reports to the [mclo.gs service](https://mclo.gs). The
[mclogs-integration project](https://github.com/aternosorg/mclogs-integration)
is also acknowledged as a reference. Loggy is independently maintained and is
not affiliated with or endorsed by Aternos. See
[third-party notices](THIRD-PARTY-NOTICES.md) for the distributed library's
license and provenance.
