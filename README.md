# Syntax locality experiment: HTML report

[HTML report on GitHub Pages](https://kmizu.github.io/syntax-locality-experiments/) · [Publication verification workflow](https://github.com/kmizu/syntax-locality-experiments/actions/workflows/report-pages.yml)

This standalone publication project turns the saved, strictly graded Scala reports in `data/` into `site/index.html`. It does not send model requests, grade responses, resample families, or calculate confidence intervals. Percentages in the interactive table are exact ratios of persisted correct/evaluable counts; confidence intervals are copied from `paired-comparisons.csv` only.

The page separates the completed exploratory P1 pilot, the interrupted frozen main run, and the P2 whole-program parsing/generation extension. P2's standalone 960-trial synthetic mock verified the offline pipeline; its live model measurement remains unexecuted with zero generations and no measured success rate. Prefix scope lookup and active-stack observations are explicitly distinguished from whole-program parsing. The original experiment and frozen implementation are outside this project.

## Build

Use **Scala 3.3.8, JDK 21, sbt 1.10.7**. There are no application dependencies beyond the Scala standard library and JDK APIs.

From this directory on Windows:

```powershell
Expand-Archive -LiteralPath report-project.zip -DestinationPath . -Force
./scripts/sbt.ps1 'run'
```

With an existing sbt installation on Linux/macOS:

```sh
unzip report-project.zip
sbt 'run'
```

Optional positional input/output directories are accepted: `sbt 'run data site'`. Keep the publication directory as the working directory. A populated Maven/sbt cache or network access is needed for the build tools; the generator itself is offline.

The repository's byte-preserved `report-project.zip` contains the report generator, all public saved data, and the isolated experiment source/test snapshot under `experiment/`. It excludes credentials, private instruction files and build caches. The workflow expands it before compiling. To verify the experiment snapshot, use JDK 21 and run `./scripts/sbt.ps1 test` from the expanded `experiment/` directory. The original frozen reading main retains its separate historical implementation and protocol.

The default output is `site/index.html`. All public input artifacts are copied byte-for-byte to `site/data/`. The report includes their SHA-256 hashes and downloadable links. Given identical data, generator source, and build metadata, output is byte-identical: it has no clock time, absolute workspace path, external assets, analytics, or network fetches. If input artifacts are removed, use a fresh output directory before deployment so removed files are not retained.

The optional environment variable `SYNTAX_P2_TEST_VERIFIED=true` is reserved for the publication workflow after its prior required full experiment-test step succeeds. It requires `SYNTAX_REPORT_WORKFLOW_URL` identifying the actual GitHub Actions run. This flag records publication build verification only; it cannot establish standalone mock completion. `site/build-provenance.json` records build verification separately from the saved P2 mock witness and zero live generations.

Standalone mock completion requires `data/supporting/p2-mock-verification.json`, byte-identical to SHA-256 `224e9091619a7ce85cce16c663fff2238b3694161fc4f5540734bfd383b5c4dc`. The generator checks the fixed source hash `41ffc2647b8f3c3838a17c2425b0be97007fe611f5d0a68d3dd9d1c79e1b0b96`, workflow identity, synthetic label, counts, reproduction and resume assertions. Changed or malformed witness bytes are rejected; absent evidence retains the earlier unexecuted mock status. All five saved mock reports must exist separately under `data/mock-p2/`, and the Markdown report must retain `synthetic_mock=true`. They are downloadable artifacts and are excluded from the interactive P1/main tables and charts.

The saved witness comes from [workflow run 37371856651, attempt 3](https://github.com/kmizu/syntax-locality-experiments/actions/runs/37371856651/attempts/3), job `112109491633`. Its full suites passed (28 core, 19 bench, 12 preflight, 13 parsing), followed by 960 terminal strict-correct synthetic trials across 48 structural families. Regenerating the five reports from saved artifacts preserved their bytes; a completed-run resume added zero logical trials and zero simulated adapter attempts, with all immutable artifacts unchanged. The 1,920 fresh mock adapter attempts were synthetic count/generation calls. Zero external HTTP calls is evidence from the explicit `--mock` code path without `--execute`, not packet capture. Artifact `11390722284` has digest SHA-256 `a0058b2519122ca9bffe84e60fe1b0a1875b696416cbbbc69969e86b47392bc4`.

## Required data

Both `data/pilot/` and `data/main/` contain `summary.csv`, `paired-comparisons.csv`, `errors.csv`, `report.md`, and `protocol.json`. CSV parsing supports quoted commas, escaped quotes, CRLF/LF, and quoted multiline fields, and rejects malformed rows and duplicate headers. The generator validates aggregate counts against disjoint persisted leaf cells, preserves non-evaluable outcomes, and rejects inconsistent totals rather than publishing guessed results.

Supporting public documents and recovery/mock witnesses live under `data/docs/` and `data/supporting/`. The separate synthetic outputs in `data/mock-p2/` are `report.md`, `summary.csv`, `paired-comparisons.csv`, `errors.csv`, and `scores.jsonl`. `data/status.json` records the completed mock verification and unexecuted live P2 while preserving the original P1/main state. Consult those artifacts for exact status and source provenance. Report results must be updated by regenerating from saved Scala grading/report artifacts; never hand-edit HTML numbers.

## Publish

Deploy the contents of `site/` using GitHub Pages. Repository creation and Pages activation are separate external actions. The report generator does not create a repository, handle credentials, or publish files. Use the publication workflow provided by the surrounding task once the HTML has compiled and been inspected.

All P1 results are exploratory. An interrupted main snapshot has no inferential confidence interval. P2 has no measured success rate until actual responses have been saved and graded under its separate protocol.
