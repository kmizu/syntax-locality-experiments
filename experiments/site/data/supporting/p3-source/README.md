# P3 source snapshot (`p3-short-dependency` worktree)

This directory is a byte-identical copy of the implementation that produced the three P3 live pilot runs published under `../../p3a/`, `../../p3b-fq/` and `../../p3b-short/`, together with the preflight capability probe in `../../p3-preflight/`.

- Branch: `p3-short-dependency` (isolated worktree of the private experiment repository).
- Commits: `6aed57575a56abcbd2dc111a535db1535a54a836` (P3 extension: `depth_end` / `indent` styles and `import` qualification) and `d98fc14352ea11e25ed432b42f66649d7db61c1b` (preserved P3 live pilot results and preflight). The working tree was clean at the time of copying (`git status --porcelain` empty, `git diff HEAD` empty for the copied paths).
- Contents: `bench/src` (main and test sources), `llm-core/src`, `build.sbt`, `project/build.properties`, `configs/p3a-pilot.json`, `configs/p3b-fq-pilot.json`, `configs/p3b-short-pilot.json`, `configs/p3.md`, `docs/p3-short-dependency-design.md`. `configs/p3.md` and the design document are copied unchanged; their "unexecuted" / "in progress" wording predates the live runs recorded in `../../status.json`.
- Excluded: build caches, raw `trials/` request/response bodies, budget/usage ledgers, `.env`, credentials and private instruction files.

## Source hash

`Protocol.sourceHash` in `bench/src/main/scala/locality/bench/run/Protocol.scala` hashes, in sorted path order, every `.scala` file under `bench/src/main` and `llm-core/src/main` plus `build.sbt` and `project/build.properties`: for each file it joins `path + "\n" + sha256(fileText)` and takes the SHA-256 of these lines joined with `"\n"` (paths use forward slashes; file bytes are hashed as read by `Files.readString`, i.e. as checked out, line endings included).

Recomputing that algorithm over this snapshot (43 hashed files) gives

```
f57b680ff77f4a0b4190ede060a71f008bb1671a65d82f100fc9e6a5ffa4aff0
```

which **matches** `config.sourceHash` in `p3a/protocol.json`, `p3b-fq/protocol.json` and `p3b-short/protocol.json`. The match depends on the exact bytes: the public repository stores files with `* -text`, so line endings are preserved. A line-ending-normalised copy (CRLF to LF) hashes to a different value (`2ab52681aa41de5cc5a7c54921e05c0209e0fc1ac5be120f980cb36a72fb7663`) and would not match the saved protocols.

This snapshot is not compiled by the public repository's root `sbt test`; the public `bench/` and `llm-core/` directories at the repository root remain the P2-era sources with hash `41ffc2647b8f3c3838a17c2425b0be97007fe611f5d0a68d3dd9d1c79e1b0b96` and do not contain the P3 code. To build this snapshot, copy this directory to an empty location and run `sbt test` there with JDK 21 and sbt 1.10.7.
