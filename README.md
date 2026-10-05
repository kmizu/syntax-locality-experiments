# Syntax locality experiment: HTML report

This standalone publication project turns the saved, strictly graded Scala reports in `data/` into `site/index.html`. It does not send model requests, grade responses, resample families, or calculate confidence intervals. Percentages in the interactive table are exact ratios of persisted correct/evaluable counts; confidence intervals are copied from `paired-comparisons.csv` only.

The page separates the completed exploratory P1 pilot, the interrupted frozen main run, and the unexecuted P2 whole-program parsing/generation extension. Prefix scope lookup and active-stack observations are explicitly distinguished from whole-program parsing. The original experiment and frozen implementation are outside this project.

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

The optional environment variable `SYNTAX_P2_TEST_VERIFIED=true` is reserved for the publication workflow after its separate, current experiment-test job succeeds. It requires `SYNTAX_REPORT_WORKFLOW_URL` identifying the actual GitHub Actions run. Without that evidence the page retains the unverified implementation status. `site/build-provenance.json` records the supplied status and link; a passing build never changes P2's zero model measurements. The copied `data/status.json` remains the earlier data-snapshot state.

## Required data

Both `data/pilot/` and `data/main/` contain `summary.csv`, `paired-comparisons.csv`, `errors.csv`, `report.md`, and `protocol.json`. CSV parsing supports quoted commas, escaped quotes, CRLF/LF, and quoted multiline fields, and rejects malformed rows and duplicate headers. The generator validates aggregate counts against disjoint persisted leaf cells, preserves non-evaluable outcomes, and rejects inconsistent totals rather than publishing guessed results.

Supporting public documents and a bounded recovery witness live under `data/docs/` and `data/supporting/`. `data/status.json` and the data manifest, when present, remain downloadable. Consult those artifacts for the snapshot's exact status and source adaptation provenance. Report results must be updated by regenerating from saved Scala grading/report artifacts; never hand-edit HTML numbers.

## Publish

Deploy the contents of `site/` using GitHub Pages. Repository creation and Pages activation are separate external actions. The report generator does not create a repository, handle credentials, or publish files. Use the publication workflow provided by the surrounding task once the HTML has compiled and been inspected.

All P1 results are exploratory. An interrupted main snapshot has no inferential confidence interval. P2 has no measured success rate until actual responses have been saved and graded under its separate protocol.
