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
