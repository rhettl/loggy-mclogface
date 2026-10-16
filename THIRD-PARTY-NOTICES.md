# Third-party notices

## mclogs-java (`gs.mclo:api:6.2.1`)

The distributed Loggy JAR nests the published mclogs-java API library, which
provides access to the [mclo.gs](https://mclo.gs) upload service. The library is
maintained by Aternos. Loggy does not copy or modify its source files.

- Project: https://github.com/aternosorg/mclogs-java
- Artifact: https://central.sonatype.com/artifact/gs.mclo/api/6.2.1
- License: MIT; the complete license is included in the JAR at
  `META-INF/licenses/mclogs-java-MIT.txt`.
- Runtime dependency: the artifact declares Gson 2.12.1. Loggy excludes that
  dependency and uses Minecraft 1.21.1's Gson 2.10.1; Loggy's compatibility
  checks cover the Gson APIs used by the pinned library.

The separate [mclogs-integration](https://github.com/aternosorg/mclogs-integration)
and [mclogs](https://github.com/aternosorg/mclogs) projects are acknowledged as
references. No files from those repositories are copied into Loggy.

## Complete mclogs-java license

The same notice is present in the distributed JAR at
`META-INF/licenses/mclogs-java-MIT.txt`.

```
Copyright (c) 2021-2024 Aternos GmbH

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```
