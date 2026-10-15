# P3 source snapshot (`p3-short-dependency` worktree)

This directory is a byte-identical copy of the implementation that produced the four P3 live pilot runs published under `../../p3a/`, `../../p3b-fq/`, `../../p3b-short/` and `../../p3c/`, together with the preflight capability probes in `../../p3-preflight/` and `../../p3c-preflight/`.

- Branch: `p3-short-dependency` (isolated worktree of the private experiment repository).
- Commits: `6aed57575a56abcbd2dc111a535db1535a54a836` (P3 extension: `depth_end` / `indent` styles and `import` qualification), `d98fc14352ea11e25ed432b42f66649d7db61c1b` (preserved P3a/P3b live pilot results and preflight), `39d69491f2fc8cd5bcd5086327aeed82ace34506` (P3c combined `indent_named_end` / `indent_generic_end` styles) and `14a166a97a50779307c8969cfa0583eb33b3a824` (preserved P3c live pilot results and preflight). The snapshot is taken from the working tree at `14a166a97a50779307c8969cfa0583eb33b3a824`; it was clean at the time of copying (`git status --porcelain` empty, `git diff HEAD` empty for the copied paths).
- Contents: `bench/src` (main and test sources), `llm-core/src`, `build.sbt`, `project/build.properties`, `configs/p3a-pilot.json`, `configs/p3b-fq-pilot.json`, `configs/p3b-short-pilot.json`, `configs/p3c-pilot.json`, `configs/p3.md`, `docs/p3-short-dependency-design.md`. `configs/p3.md` and the design document are copied unchanged; their "unexecuted" wording for P3c predates the live P3c run recorded in `../../status.json`.
- Excluded: build caches, raw `trials/` request/response bodies, budget/usage ledgers, `.env`, credentials and private instruction files.

## Source hash

`Protocol.sourceHash` in `bench/src/main/scala/locality/bench/run/Protocol.scala` hashes, in sorted path order, every `.scala` file under `bench/src/main` and `llm-core/src/main` plus `build.sbt` and `project/build.properties`: for each file it joins `path + "\n" + sha256(fileText)` and takes the SHA-256 of these lines joined with `"\n"` (paths use forward slashes; file bytes are hashed as read by `Files.readString`, i.e. as checked out, line endings included).

Recomputing that algorithm over this snapshot (43 hashed files) gives

```
9d1d8aaf7ab703c39702f518386873b091a4d1f3f0b967ba13dc0b5f856b193c
```

which **matches** `config.sourceHash` in `p3c/protocol.json` (the P3c run; its separate protocol hash is `a266e4cb45980b516d6d55b5df4f91fdadcf710c97de5b940ecaa1973ea9f01c`). The P3a and P3b runs were produced by the earlier snapshot of the same worktree at `d98fc14352ea11e25ed432b42f66649d7db61c1b` (before the P3c styles were added), whose source hash `f57b680ff77f4a0b4190ede060a71f008bb1671a65d82f100fc9e6a5ffa4aff0` is recorded in `p3a/protocol.json`, `p3b-fq/protocol.json` and `p3b-short/protocol.json`; that earlier recomputation **matched** those three protocols. The two snapshots differ only in the P3c commit's changes (`Parser.scala`, `Renderer.scala`, `Syntax.scala`, `PromptBuilder.scala`, four test files, `configs/p3.md`, `configs/p3c-pilot.json` and the design document); the earlier snapshot is reachable in this repository's history at commit `0adc90b85d3095183ec99dc082d1ddffd0fc729c`, which published P3a/P3b. The match depends on the exact bytes: the public repository stores files with `* -text`, so line endings are preserved. A line-ending-normalised copy (CRLF to LF) of the current snapshot hashes to a different value (`ef384b19240d3b7446274efecf08c474c5e0f61ce0cd36f639e837e223b466f0`) and would not match the saved protocol.

This snapshot is not compiled by the public repository's root `sbt test`; the public `bench/` and `llm-core/` directories at the repository root remain the P2-era sources with hash `41ffc2647b8f3c3838a17c2425b0be97007fe611f5d0a68d3dd9d1c79e1b0b96` and do not contain the P3 code. To build this snapshot, copy this directory to an empty location and run `sbt test` there with JDK 21 and sbt 1.10.7.
