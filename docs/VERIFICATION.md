# Initial implementation verification

Date: 2026-10-15. These are completed checks, not claims of game-runtime coverage.

- Java 21 / Minecraft 1.21.1 / Fabric combined build and local Maven publication pass.
- 31 Loggy tests pass: registration/immutable reports, provider target selection,
  severity and failure isolation, redaction, UTF-8 and line bounds, unavailable log,
  saved report retention, mock transport success/failure, overlap and deadlines.
- Actual published mclogs API 6.2.1 with Minecraft Gson 2.10.1: real filters/limits,
  report preparation and compressed upload succeeded with synthetic text only.
  Evidence: https://mclo.gs/fTPWJDm . No user logs or machine details were uploaded.
- Nested mclogs API JAR, remapped runtime/source artifacts, POM and MIT notices checked.
- Crests integration worker build passed with 636 tests and its configured coverage
  gate. Final consumer verification against the hosted Maven artifact is a release gate.
- Workflow run blocks passed shell syntax checks; publication uses a serialized
  writer to this repository's gh-pages branch. Maven releases remain available;
  any future Actions artifact upload must set retention-days: 1.

## Manual checks still needed

1. Integrated server: operator `/loggy list`, report commands and Crests aliases;
   verify evidence/link/local copy; non-operator denial.
2. Dedicated server: same commands from console/operator; no client classes required.
3. Two parent mods embedding the same and differing compatible Loggy versions:
   verify Fabric deduplication and provider registration; incompatible constraints
   must fail clearly. This has not been tested by merely inspecting nested JARs.
4. Observe shutdown while a report is running; verify no stale chat callback and
   bounded network activity.

Loggy intentionally pins internal mclogs FilterList/RequestBuilder APIs to combine
library filters/compression with configurable HTTP deadlines. Dependency upgrades
must rerun gateway/filter compatibility tests and a synthetic live smoke.

## 0.1.1 publication regression

In-game development reporting exposed a missing `gs.mclo.api.MclogsClient` class
with 0.1.0. Its manually constructed Maven publication included the nested JAR but
omitted the Java component's runtime dependency metadata. Version 0.1.1 publishes
from that component, preserving transitive runtime dependencies and the Gson
exclusion alongside Loom's remapped/nested artifacts.

`check` now publishes into a local Maven layout and runs a separate Gradle consumer
that instantiates MclogsClient and initializes Loggy's gateway without unpacking
nested libraries or making network requests. The consumer supplies Minecraft's
Gson 2.10.1; it must resolve mclogs-java transitively from Loggy's publication.

## 0.1.2 optional clipboard companion

Successful report completion sends a validated mclo.gs share link only to the
initiating player when their client advertises the receiver. The client copies
it on its own thread and confirms in chat; console and clients without Loggy
retain manual link/copy actions. Codec tests cover roundtrip and invalid URL
rejection. Client classes live in the client source set and client entrypoint;
common/server code never accesses the server host's clipboard.

Manual checks: auto-copy after `/loggy report ...` and Crests aliases in integrated
and multiplayer sessions; server-only Loggy with an unmodified client; clipboard
failure retains manual copy. These were not established by JVM tests.

## 0.2.0 namespaced provider IDs

Provider IDs may be `segment` or `segment:segment`. Registry tests cover accepting
`bca`, `bca:crests`, and `crests` together, duplicate rejection, and rejection of
empty segments, repeated or leading/trailing `:`, `/`, `|`, uppercase, and
digit-leading segments. `ReportTargetArgument` is parsed through a Brigadier
dispatcher, and a test shows the stock `word()` argument cannot parse `:`.

Manual check, not established by JVM tests: tab-completion and execution of
`/loggy report bca:crests` in a running server with a namespaced provider registered.
