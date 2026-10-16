# Loggy McLogface — implementation contract

Status: agreed scope translated into an implementation contract, 2026-10-15.
This document is authoritative for the initial extraction. Implementation has not started.
It supersedes the original Crests `dev-docs/bca-status-command.md` for the shared reporting project.

## Purpose and success

A developer asks an administrator to run one command. The administrator returns
one mclo.gs link containing current server evidence, the current log, and the
requested mod's diagnostics. No locating files or choosing client versus server logs.
Chat output is short; the uploaded evidence is detailed.

- Repository: `rhettl/loggy-mclogface`.
- Display name: Loggy McLogface (Boaty McBoatface reference).
- Mod ID and command root: `loggy`.
- Initial target: Fabric, Minecraft 1.21.1, Java 21.
- Maven coordinates: `io.github.rhettl:loggy-mclogface:<version>`.
- Distribution: standalone mod and nested dependency embedded by parent mods.
- Upload implementation: published `gs.mclo:api:6.2.1`, behind Loggy's wrapper.
- First consumer: Cobble Crests. BCA-CE integration follows as a separate task.

Success requires the published artifact to work in Crests, not just independent
worker builds. No Cobblemon, owo, or Crests dependency belongs in Loggy.

## Commands and authority

All commands require vanilla permission level 2. Register server commands for both
integrated and dedicated servers; console use is supported.

```
/loggy list
/loggy report minecraft
/loggy report crests
/loggy report all
```

`list` lists registered targets with descriptions; it neither collects nor uploads.
Command completion suggests registered IDs and `all`. Bare `/loggy` prints usage.
`report <target>` collects, saves a prepared local report, uploads it, and returns
a clickable/copyable link. Unknown targets fail before collecting or uploading.

Every report includes Minecraft/environment evidence and the current log.
Selecting `minecraft` collects only built-in diagnostics; another target adds that
provider; `all` adds every provider. Collect each provider once, ordered by ID.
Do not introduce summary/full/upload modes in v1.

Existing `/crests status` and `/crests status full` become documented aliases for
`/loggy report crests`. Both upload; remove the old formatter and direct HTTP code.
Keep `/crests gen-datapack` independently registered and operational.

## Public API

Package: `io.github.rhettl.loggy.api`. One published mod artifact exposes this API.
The signatures below are the v1 contract; worker agents may not independently
change them. The coordinator resolves necessary changes before dependents continue.

```java
public interface DiagnosticProvider {
    String id();
    String displayName();
    String description();
    DiagnosticReport collect(DiagnosticContext context);
}

public record DiagnosticContext(
    MinecraftServer server,
    Instant collectedAt
) {}

public record DiagnosticReport(
    List<DiagnosticSection> sections,
    List<DiagnosticFinding> findings
) {}

public record DiagnosticSection(String title, List<String> lines) {}

public record DiagnosticFinding(
    Severity severity,
    String message
) {}

public enum Severity { INFO, WARN, ERROR }

public final class Loggy {
    public static void register(DiagnosticProvider provider);
}
```

Use the normal Java imports for MinecraftServer, Instant, and List. Record
constructors validate nulls and defensively copy lists. Plain text only: no chat
components, formatting codes, paths-to-upload, mutable Minecraft objects, arbitrary
serialized objects, or provider-controlled network requests in returned reports.

Provider identity is a lowercase command-safe ID matching `[a-z][a-z0-9_-]*`.
IDs `minecraft`, `all`, `list`, and `report` are reserved for Loggy. Invalid or
duplicate registration throws a descriptive IllegalArgumentException; retain the
first registration. Provider metadata is validated and captured at registration.

Parents call `Loggy.register(new TheirDiagnostics())` during common initialization.
Registration closes when the first server starts; late calls throw
IllegalStateException. Registry metadata is process-scoped; diagnostic results
are not cached across requests or worlds. Initialization must happen once when
multiple parents embed Loggy. The built-in provider is registered internally.

`collect` runs synchronously on the server thread. Providers must perform bounded,
read-only in-memory inspection: no disk/network I/O, waiting on futures, world
scanning, loading chunks, transactions, or mutation. Do not retain context/server
references after collection. There is no separate `status()` call or check pass.

Status is derived from findings: ERROR > WARN > OK; INFO does not degrade status.
An empty finding list means the provider reported no issues, not that all possible
checks were performed. Providers should identify missing/unperformed relevant
checks. One provider exception produces an ERROR section and remaining providers
continue. Null/invalid results are provider failures. Never expose a raw stack
trace or exception message in generated diagnostic sections; log failures locally
with provider ID and include a safe failure description in the report.

## Evidence and collection flow

1. Validate permissions/target and acquire a single report-in-progress slot per
   server (across all callers). Reject overlap with a clear message.
2. On the server thread, capture immutable provider results, collection timestamp,
   dedicated/integrated identity, and selected target. Include built-in information:
   Minecraft, Fabric Loader, Loggy, Java version/vendor, OS name/architecture,
   available processors, JVM used/committed/max memory, all loaded mod IDs/names/
   versions, and enabled datapack IDs/titles. Include mod-provided and vanilla packs,
   identified where possible; do not publish their absolute filesystem paths.
3. Off-thread, read a bounded snapshot of the current instance's `logs/latest.log`
   using its server root. In single-player this is the shared process log and can
   contain both client and server messages. Identify that accurately; do not attempt
   thread-name-based filtering. Missing/unreadable log becomes an explicit finding,
   not a reason to discard provider evidence.
4. Assemble a plain-text document: report identity, collection status, environment,
   provider sections/findings, log inclusion/truncation notice, then the log.
5. Prepare the bounded/redacted upload content, save it under
   `<server-root>/logs/loggy/`, upload, and return success/failure to the initiating
   command source on the server thread. Do not broadcast links to other players.
6. Release the in-progress slot on every terminal path. If the server stops, suppress
   stale chat callbacks and clean up owned tasks/resources.

The server-thread collector cannot be safely interrupted by a timeout. Slow
providers are a contract violation; record elapsed time and warn locally. A stuck
provider cannot be solved by moving unsafe Minecraft access to a worker thread.

## Upload, privacy, and failure policy

Wrap mclogs-java; do not expose its types in the provider API. Use one reusable
client, default mclo.gs endpoint, and an internal transport seam for tests. No
custom-endpoint configuration is required for v1.

- Apply a 30-second upload-operation deadline, including filter retrieval. Avoid
  endless overlapping underlying requests; verify cancellation/cleanup behavior.
- Use the published library's filtering and compression. Its filter-fetch fallback
  has no local redaction: require verified local filtering or independently apply
  equivalent baseline redaction before upload. Do not silently treat failed filter
  retrieval as successful local redaction. Do not depend on internal API types
  without documenting and testing the pinned-version dependency.
- Generated sections must deliberately exclude secrets, credentials, full configs,
  environment-variable dumps, JVM arguments, hostnames, player rosters, UUID lists,
  and absolute paths. Do not invent a broad configuration scraper.
- Metadata/source fields bypass content filters: use only fixed application identity
  and known-safe version/side/target fields. Do not insert arbitrary provider text.
- Generated reports start with a non-whitespace heading. Verify the pinned library's
  leading-whitespace content-loss behavior when reading arbitrary log files.
- Bound content before library trimming. Reserve space for generated diagnostics;
  preserve the recent log tail within the remaining budget at line boundaries.
  Respect the service's advertised byte/line limits and count UTF-8 bytes. Explicitly
  report omissions/truncation. If diagnostics alone exceed the budget, truncate
  sections with an explicit notice. Automatic library trimming is a final safeguard.
- Persist the prepared report before upload. Use unique timestamps/request IDs,
  retain at most the newest 10 reports, and never modify `latest.log`.
- On upload failure, return the relative saved-report location and a safe reason.
  If local saving also fails, say so; still attempt upload of prepared content.
- Deletion tokens must never appear in chat, report content, logs, or Git. V1 need
  not implement a delete command or retain tokens.
- Upload only after an explicit report command; no automatic background telemetry.

mclo.gs redaction is additional protection, not a guarantee for arbitrary provider
fields. No new per-report confirmation screen is required.

## Crests integration

Implement `CrestsDiagnostics` inside Crests, depending on Loggy's public API.
Report mod/schema version, resolved bank defaults (safe selected settings), shop
and group counts/listings, last reload outcome/time, and actionable load findings.
Record parsing/validation/condition errors in the loader's diagnostic collection,
not only LOGGER calls. Preserve resource/pack identity where available. Expected
conditional exclusions are informational, distinct from invalid definitions.
Count rejected definitions separately from distinct affected packs; never infer
“All datapacks loaded successfully” solely from an empty schema-error list.
Diagnostics describe the last load/current state; invoking a report must not reload
resources or reroll merchant wares. Preserve the existing assignment/trading system.

## Distribution, attribution, and delivery

Publish source and remapped runtime artifacts with correct POM metadata; include
Loggy's runtime library dependencies for standalone/nested use. Avoid bundling a
second Gson blindly: verify against Minecraft 1.21.1's runtime Gson. Pin dependency
versions and verify licenses/notices in the distributed JAR.

Credit Aternos/mclo.gs in README, link upstream projects/site, and retain full MIT
notices for dependencies and copied/adapted code. Record copied-file provenance
(repository, commit, modifications) in THIRD-PARTY-NOTICES.md. Do not imply endorsement.

Destination is the user's `rhettl/maven` repository. Inspect its actual layout and
publication mechanism before implementing delivery. The local sibling
`../owo-dark-ui` demonstrates Java 21, nested consumption, and directory-based Maven
publication, but its workflow publishes to its own gh-pages branch: do not copy
that destination or assume GITHUB_TOKEN can write another repository.

PR checks run build/tests without publishing. Release tags `vX.Y.Z` publish that
exact immutable version; main may publish `<base>-SNAPSHOT` using established Maven
repository conventions. One serialized publisher writes the shared destination;
preserve all existing artifacts and metadata. Document required cross-repository
credentials by name, never their values. Never overwrite an existing release.

## Acceptance gates

- Pure tests: registration validation/reserved/duplicate IDs, defensive copies,
  target selection/deduplication, severity, provider failure isolation, report
  formatting, truncation/UTF-8 limits, and redaction/fallback behavior.
- Mock transport tests: success, failed/malformed response, filter failure, deadline,
  repeated invocation rejection, local fallback, and cleanup. Never upload real logs
  in automated tests. Real service smoke test uses synthetic content only.
- Runtime checks: standalone integrated and dedicated server; admin/console allowed,
  non-admin denied; current instance evidence; failure of one provider; Crests aliases.
- Packaging: Crests alone embeds Loggy; two parents embedding the same version load
  one Loggy instance; differing compatible embedded versions resolve predictably;
  an incompatible combination fails clearly. No client-required classes on server.
- Publication: clean consumer resolves the exact released version from rhettl/maven;
  no mavenLocal, sibling checkout, unpublished artifact, or developer cache required.
- Handoff: source repo, issue/PR links, commits/tags, public API guide, coordinates,
  Maven URL, workflow setup, test evidence, remaining limitations, and Crests
  integration commit linked back from this project.

## Fleet assignments and ownership

Do not launch workers until the coordinator has established a baseline API/scaffold
commit in the new repository. Each implementation worker gets a branch/worktree,
explicit owned files, and the same contract revision. GitHub issues are the durable
task records. No worker silently changes the shared API or another worker's files.

1. **Coordinator/bootstrap:** inspect publication conventions; establish repository,
   API records/interface/registry skeleton and test fixtures; create linked issues;
   own API decisions, integration and final acceptance. Do not implement all workers'
   tasks during bootstrap. No need for a custom agent-orchestration service.
2. **Loggy implementation:** own Loggy runtime, built-in provider, commands, wrapper,
   local persistence, privacy/limits and associated tests. Deliver a working local
   Maven artifact plus integration instructions.
3. **Crests implementation:** own loader diagnostic completeness, provider, aliases,
   Crests dependency wiring and tests. Use the baseline API/local artifact until a
   published release exists. Do not duplicate upload/formatting infrastructure.
4. **Release/documentation:** own build/publication workflows, attribution, README,
   consumer example and Maven delivery; run after coordinates/scaffold are fixed.
   May be assigned to a worker finishing task 2 or 3.
5. **Independent review:** assess actual diff and acceptance evidence, including
   integrated-server behavior, privacy failure paths and nested dependencies.
   Coordinator resolves findings before release and final consumer verification.

Every handoff specifies branch/commit, changes, tests actually run, unresolved
findings and next dependencies. BCA-CE implementation, client-only reporting,
startup-crash handling, historical log browsing, deletion commands, dashboards,
and multi-loader support are outside this initial assignment.

## References checked

- `.refs/mclogs` — service checkout `f02d7dd`.
- `.refs/mclogs-integration` — platform integration checkout `e08ec2f`.
- `.refs/mclogs-java` — library reference; actual Maven Central 6.2.1 artifact checked.
- https://api.mclo.gs/ — API/filter/storage-limit documentation.
- https://github.com/aternosorg/mclogs-java
- https://github.com/aternosorg/mclogs-integration

Reference checkouts are local and ignored; workers must use upstream links and
pinned releases/commits rather than assume these directories exist on their machines.
