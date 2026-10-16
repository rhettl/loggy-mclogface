# Loggy worker instructions

Read docs/CONTRACT.md before editing. It is the v1 behavior and API contract.
Do not change public API signatures without coordinator agreement.
Own your assigned files; use a separate branch/worktree and preserve other work.
Use Java 21 and Minecraft 1.21.1. Keep Minecraft access on the server thread;
providers do not perform I/O. Do not add Crests, Cobblemon, or UI dependencies.
Run ./gradlew build. Network tests use synthetic data and mock transport only.
Do not publish real logs or credentials. Do not add release publication until its
cross-repository destination and authentication are established.
Report branch/commit, tests actually run, limitations, and downstream dependencies.
