# Loggy McLogface

A server troubleshooting mod named in the spirit of Boaty McBoatface.
A future `/loggy report <target>` command will collect server evidence and registered
mod diagnostics, then return a shareable mclo.gs link.

## Current state: API baseline

Java 21 / Fabric / Minecraft 1.21.1. This baseline supplies the public contributor
API and registration lifecycle. **Commands, reports, uploads, and Crests integration
are not implemented yet.** See [the contract](docs/CONTRACT.md) for assignments and
acceptance gates.

Build/test: `./gradlew build`. Optional local publication: `./gradlew publishToMavenLocal`.
Coordinates: `io.github.rhettl:loggy-mclogface:0.1.0-SNAPSHOT`.
Release delivery to `rhettl/maven` is not configured yet.

## Register a provider

Call during your mod's common initialization:

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

Types are in `io.github.rhettl.loggy.api`; `List` is `java.util.List`.
Collection runs on the server thread: read bounded in-memory state only. Return
plain text with no secrets. Never block, perform I/O, mutate Minecraft state, or
retain the context. Registration closes when the first server starts. IDs are
lowercase command-safe names; minecraft/all/list/report are reserved.

The `internal` package and `Loggy.registry()` are implementation details, not a
supported extension API. Parents will embed the runtime mod as a nested dependency;
exact published consumption is a release/integration acceptance gate.

## Upstream acknowledgment

Loggy is designed around the [mclogs-java](https://github.com/aternosorg/mclogs-java)
API and [mclo.gs](https://mclo.gs) service. Foundational reference work comes from
[Aternos' mclogs-integration](https://github.com/aternosorg/mclogs-integration).
Loggy is independently maintained and is not affiliated with or endorsed by Aternos.
See [third-party notices](THIRD-PARTY-NOTICES.md).
